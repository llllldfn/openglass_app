package com.example.myapp.input.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.ContentValues
import android.os.ParcelUuid
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.util.UUID
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BleConfig {
	// OpenGlass 服务 UUID
	val SERVICE_UUID = "19B10000-E8F2-537E-4F6C-D104768A1214"
	val PHOTO_CHAR_UUID = "19B10005-E8F2-537E-4F6C-D104768A1214"
	val PHOTO_CONTROL_CHAR_UUID = "19B10006-E8F2-537E-4F6C-D104768A1214"
	val AUDIO_CHAR_UUID = "19B10001-E8F2-537E-4F6C-D104768A1214"  // 修正为OpenGlass固件中的UUID
	// 音频控制指令（与设备端约定，若不同请告知）
	const val CMD_AUDIO_START: Byte = 0x61  // 'a' -> start
	const val CMD_AUDIO_STOP: Byte = 0x60   // '`' -> stop
}

data class BleConnectionState(
	val isConnected: Boolean = false,
	val deviceName: String? = null,
	val deviceAddress: String? = null,
	val isScanning: Boolean = false,
	val error: String? = null
)

class BleInput(private val context: Context) {
	private val _connectionState = MutableStateFlow(BleConnectionState())
	val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()
	
	// 图片数据流
	private val _photoData = MutableSharedFlow<ByteArray>()
	val photoData: SharedFlow<ByteArray> = _photoData.asSharedFlow()
	
	private var gatt: BluetoothGatt? = null
	private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
	private var scanCallback: ScanCallback? = null

	// 音频临时缓冲（设备录音）
	private var isCapturingAudio: Boolean = false
	private var audioBuffer: java.io.ByteArrayOutputStream? = null
	
	@SuppressLint("MissingPermission")
	fun scanAndConnect(deviceName: String = "OpenGlass"): Flow<ByteArray> = callbackFlow {
		val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
		val adapter: BluetoothAdapter = btManager.adapter ?: run {
			close(IllegalStateException("Bluetooth not available"))
			return@callbackFlow
		}
		
		scanner = adapter.bluetoothLeScanner
		_connectionState.value = BleConnectionState(isScanning = true)
		
		// 图片数据缓冲区
		var photoBuffer: ByteArray = ByteArray(0)
		var previousChunkId: Int = -1
		val packetCache = mutableMapOf<Int, ByteArray>()
		var expectedPacketId: Int = -1
		val maxMissingPackets = 20
		val missingPackets = mutableSetOf<Int>()
		val maxCacheSize = 40
		var waitingTimeoutJob: Job? = null
		val maxWaitTimeMs = 500L
		var retransmitRequested = false
		var retransmitJob: Job? = null
		
		val callback = object : BluetoothGattCallback() {
			override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
				when (newState) {
					BluetoothProfile.STATE_CONNECTED -> {
						Log.d("BleInput", "Connected to device: ${g.device.name}")
						_connectionState.value = BleConnectionState(
							isConnected = true,
							deviceName = g.device.name,
							deviceAddress = g.device.address
						)
						// 请求更大的MTU以匹配Web端200字节有效载荷（总包~202字节）
						val requested = g.requestMtu(247)
						Log.d("[PHOTO_PREVIEW] BleInput", "🔧 请求MTU=247, 发起结果: $requested")
					}
					BluetoothProfile.STATE_DISCONNECTED -> {
						Log.d("BleInput", "Disconnected from device")
						_connectionState.value = BleConnectionState()
						close()
					}
				}
			}

			override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
				Log.i("[PHOTO_PREVIEW] BleInput", "📶 MTU已变更: $mtu, status=$status")
				if (status == BluetoothGatt.GATT_SUCCESS) {
					gatt.discoverServices()
				} else {
					Log.w("[PHOTO_PREVIEW] BleInput", "⚠️ MTU修改失败，继续发现服务")
					gatt.discoverServices()
				}
			}
			
			override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
				if (status == BluetoothGatt.GATT_SUCCESS) {
					Log.d("BleInput", "Services discovered")
					setupNotifications(g)
				} else {
					Log.e("BleInput", "Service discovery failed: $status")
					_connectionState.value = _connectionState.value.copy(error = "Service discovery failed")
				}
			}
			
			override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
				val data = characteristic.value ?: return
				Log.i("[PHOTO_PREVIEW] BleInput", "📡 BLE_DATA: 收到特征数据 ${data.size} bytes, UUID: ${characteristic.uuid}")
				
				// 打印前几个字节用于调试
				if (data.size > 0) {
					val hexString = data.take(8).joinToString("") { "%02X".format(it) }
					Log.d("[PHOTO_PREVIEW] BleInput", "🔍 BLE_DATA: 数据前8字节: $hexString")
				}
				
				// 处理图片数据 - 严格按照OpenGlass逻辑
				if (characteristic.uuid.toString().uppercase() == BleConfig.PHOTO_CHAR_UUID.uppercase()) {
					Log.i("[PHOTO_PREVIEW] BleInput", "📷 BLE_PHOTO: 收到图片数据包 ${data.size} bytes")
					
					// 检查是否是结束标记 (0xFF, 0xFF)
					if (data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFF.toByte()) {
						Log.i("[PHOTO_PREVIEW] BleInput", "🏁 BLE_PHOTO: 收到图片结束标记")
						// 处理缓存中的剩余包
						while (packetCache.containsKey(expectedPacketId)) {
							val d = packetCache.remove(expectedPacketId)!!
							photoBuffer += d
							expectedPacketId++
						}
						// finalize as before
						if (photoBuffer.isNotEmpty()) {
							Log.i("[PHOTO_PREVIEW] BleInput", "✅ BLE_PHOTO: 图片接收完成，总大小: ${photoBuffer.size} bytes")
							
							// 打印最后几个字节用于调试
							if (photoBuffer.size >= 4) {
								val lastBytes = photoBuffer.takeLast(4).joinToString("") { "%02X".format(it) }
								Log.i("[PHOTO_PREVIEW] BleInput", "🔍 BLE_PHOTO: 图片最后4字节: $lastBytes")
							}
							
							// 先保存图片数据，再清空缓冲区
							var completeImage = photoBuffer.copyOf()
							Log.i("[PHOTO_PREVIEW] BleInput", "💾 BLE_PHOTO: 保存图片数据: ${completeImage.size} bytes")
							
							// 验证完整图片数据
							if (completeImage.size >= 4) {
								val header = completeImage.take(2).joinToString("") { "%02X".format(it) }
								val footer = completeImage.takeLast(2).joinToString("") { "%02X".format(it) }
								Log.i("[PHOTO_PREVIEW] BleInput", "🔍 BLE_PHOTO: 完整图片验证 - 头: $header, 尾: $footer")
								
								// 检查是否是有效的JPEG
								if (header == "FFD8" && footer == "FFD9") {
									Log.i("[PHOTO_PREVIEW] BleInput", "✅ BLE_PHOTO: 图片数据完整，JPEG格式正确")
								} else if (header == "FFD8") {
									// 如果有JPEG头但没有正确的尾，尝试修复
									Log.w("[PHOTO_PREVIEW] BleInput", "🔧 BLE_PHOTO: 尝试修复JPEG结束标记")
									
									// 检查是否已经有FFD9结束标记（可能不在最后2字节）
									var hasValidEnd = false
									
									// 从后往前搜索FFD9
									for (i in completeImage.size - 10 downTo 1) {
										if (i < completeImage.size - 1 && 
											completeImage[i] == 0xFF.toByte() && 
											completeImage[i + 1] == 0xD9.toByte()) {
											hasValidEnd = true
											Log.i("[PHOTO_PREVIEW] BleInput", "✅ BLE_PHOTO: 找到JPEG结束标记在位置: $i")
											break
										}
									}
									
									// 如果没有找到结束标记，手动添加
									if (!hasValidEnd) {
										completeImage = completeImage + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
										Log.i("[PHOTO_PREVIEW] BleInput", "🔧 BLE_PHOTO: 已添加JPEG结束标记，新大小: ${completeImage.size} bytes")
									}
								} else {
									Log.w("[PHOTO_PREVIEW] BleInput", "⚠️ BLE_PHOTO: 图片数据可能不完整 - 头: $header, 尾: $footer")
								}
							}
							
							// 最小有效尺寸校验（小于8KB多数为截断图，直接丢弃并等待下一张）
							if (completeImage.size < 8_000) {
								Log.w("[PHOTO_PREVIEW] BleInput", "❌ BLE_PHOTO: 图片过小(${completeImage.size} bytes)，判定为截断，丢弃不展示")
								// 清空缓冲区
								photoBuffer = ByteArray(0)
								previousChunkId = -1
								expectedPacketId = -1
								packetCache.clear()
								missingPackets.clear()
								waitingTimeoutJob?.cancel()
								return
							}

							// 发送完整图片到photoData流
							CoroutineScope(Dispatchers.IO).launch {
								Log.i("[PHOTO_PREVIEW] BleInput", "📤 BLE_PHOTO: 开始发送图片到流: ${completeImage.size} bytes")
								
								// 打印图片数据的前几个字节用于调试
								if (completeImage.size > 0) {
									val hexString = completeImage.take(16).joinToString("") { "%02X".format(it) }
									Log.i("[PHOTO_PREVIEW] BleInput", "🔍 BLE_PHOTO: 发送图片前16字节: $hexString")
								}
								
								_photoData.emit(completeImage)
								Log.i("[PHOTO_PREVIEW] BleInput", "📤 BLE_PHOTO: 图片发送完成")
							}
							
							// 清空缓冲区
							photoBuffer = ByteArray(0)
							previousChunkId = -1
							expectedPacketId = -1
							packetCache.clear()
							missingPackets.clear()
							waitingTimeoutJob?.cancel()
							Log.i("[PHOTO_PREVIEW] BleInput", "🧹 BLE_PHOTO: 缓冲区已清空")
						} else {
							Log.w("[PHOTO_PREVIEW] BleInput", "⚠️ BLE_PHOTO: 收到结束标记但缓冲区为空")
						}
						return
					}
					
					// 处理数据包 - 按照OpenGlass格式：前2字节是16位包ID，后面是图片数据
					if (data.size >= 3) {
					val packetId = (data[0].toInt() and 0xFF) + ((data[1].toInt() and 0xFF) shl 8)
					val packetData = data.sliceArray(2 until data.size)
						Log.d("[PHOTO_PREVIEW] BleInput", "📦 BLE_PHOTO: 数据包 $packetId, 数据大小: ${packetData.size} bytes")
						if (expectedPacketId == -1) {
							// 新图片开始
							photoBuffer = ByteArray(0)
							expectedPacketId = packetId
							previousChunkId = packetId - 1
							packetCache.clear()
							missingPackets.clear()
							Log.i("[PHOTO_PREVIEW] BleInput", "🚀 BLE_PHOTO: 开始接收照片，起始包ID: $packetId")
						}
						if (packetId < expectedPacketId) {
							Log.w("[PHOTO_PREVIEW] BleInput", "🔄 BLE_PHOTO: 收到重复或过期的数据包: $packetId < $expectedPacketId，忽略")
							return
						}
						if (packetId == expectedPacketId) {
							photoBuffer += packetData
							previousChunkId = packetId
							expectedPacketId++
							// 处理缓存中的后续包
							while (packetCache.containsKey(expectedPacketId)) {
								val d = packetCache.remove(expectedPacketId)!!
								photoBuffer += d
								previousChunkId = expectedPacketId
								expectedPacketId++
							}
							Log.d("[PHOTO_PREVIEW] BleInput", "✅ BLE_PHOTO: 接收数据包 $packetId, 缓冲区大小: ${photoBuffer.size}")
						} else {
							Log.w("[PHOTO_PREVIEW] BleInput", "⚠️ BLE_PHOTO: 数据包乱序: 期望 $expectedPacketId，收到 $packetId，缓存等待")
							for (i in expectedPacketId until packetId) {
								missingPackets.add(i)
							}
							packetCache[packetId] = packetData
							// 当缺包过多时，安排一次重传请求（避免频繁发送）
							if (missingPackets.size > maxMissingPackets && !retransmitRequested) {
								Log.w("[PHOTO_PREVIEW] BleInput", "🚨 丢失包过多 (${missingPackets.size})，准备发送重传请求")
								retransmitRequested = true
								retransmitJob?.cancel()
								retransmitJob = CoroutineScope(Dispatchers.IO).launch {
									delay(2000)
									requestRetransmissionSafely()
									retransmitRequested = false
								}
							}
							// 启动等待超时机制，超时后跳过丢失的包，从最小可用ID继续
							if (waitingTimeoutJob == null) {
								waitingTimeoutJob = CoroutineScope(Dispatchers.Default).launch {
									delay(maxWaitTimeMs)
									// 找最小可用的包ID
									val minId = packetCache.keys.minOrNull()
									if (minId != null) {
										val lost = minId - expectedPacketId
										Log.w("[PHOTO_PREVIEW] BleInput", "⏰ 等待超时，跳过 $lost 个丢失包，从 $minId 继续")
										expectedPacketId = minId
										// 把缓存里连续的包依次加入
										while (packetCache.containsKey(expectedPacketId)) {
											val d = packetCache.remove(expectedPacketId)!!
											photoBuffer += d
											expectedPacketId++
										}
									}
									waitingTimeoutJob = null
								}
							}
							if (missingPackets.size > 30 || packetCache.size > 20) {
								// 立即触发一次跳过尝试
								waitingTimeoutJob?.cancel()
								waitingTimeoutJob = CoroutineScope(Dispatchers.Default).launch {
									delay(10)
									val minId = packetCache.keys.minOrNull()
									if (minId != null) {
										val lost = minId - expectedPacketId
										Log.w("[PHOTO_PREVIEW] BleInput", "🚀 立即跳过 $lost 个丢失包，从 $minId 继续")
										expectedPacketId = minId
										while (packetCache.containsKey(expectedPacketId)) {
											val d = packetCache.remove(expectedPacketId)!!
											photoBuffer += d
											expectedPacketId++
										}
									}
									waitingTimeoutJob = null
								}
							}
							if (packetCache.size > maxCacheSize) {
								Log.w("[PHOTO_PREVIEW] BleInput", "🧹 BLE_PHOTO: 缓存过大 (${packetCache.size})，清理旧包")
								val cutoff = expectedPacketId - 10
								val toRemove = packetCache.keys.filter { it < cutoff }
								toRemove.forEach { packetCache.remove(it) }
							}
						}
						if (photoBuffer.size >= 4) {
							val header = photoBuffer.take(2).joinToString("") { "%02X".format(it) }
							val footer = photoBuffer.takeLast(2).joinToString("") { "%02X".format(it) }
							Log.d("[PHOTO_PREVIEW] BleInput", "🔍 BLE_PHOTO: 当前缓冲区 - 头: $header, 尾: $footer")
						}
						return
					}
				}
				// 音频数据（通知频发），每帧前3字节为 header: [frame_low, frame_high, flags]
				else if (characteristic.uuid.toString().uppercase() == BleConfig.AUDIO_CHAR_UUID.uppercase()) {
					if (isCapturingAudio) {
						val payloadOffset = if (data.size > 3) 3 else 0
						val payloadLen = data.size - payloadOffset
						if (payloadLen > 0) {
							// 记录帧头信息用于调试
							if (data.size >= 3) {
								val frameId = (data[1].toInt() and 0xFF shl 8) or (data[0].toInt() and 0xFF)
								val flags = data[2].toInt() and 0xFF
								Log.v("BleInput", "[AUDIO_REC_BLE] 📡 音频帧: ID=$frameId, flags=0x${flags.toString(16)}, payload=${payloadLen} bytes")
							}
							try {
								audioBuffer?.write(data, payloadOffset, payloadLen)
							} catch (e: Exception) {
								Log.e("BleInput", "写入音频缓冲失败", e)
							}
							if ((payloadLen % 2) != 0) {
								Log.w("BleInput", "[AUDIO_REC_BLE] 音频负载长度不是偶数: $payloadLen")
							}
						}
					}
				}
			}
		}
		
		scanCallback = object : ScanCallback() {
			override fun onScanResult(callbackType: Int, result: ScanResult) {
				val name = result.device.name ?: result.scanRecord?.deviceName
				Log.d("BleInput", "Found device: $name")
				
				if (name == deviceName) {
					Log.d("BleInput", "Found target device: $deviceName")
					scanner?.stopScan(this)
					_connectionState.value = _connectionState.value.copy(isScanning = false)
					
					gatt = result.device.connectGatt(
						context, 
						false, 
						callback, 
						BluetoothDevice.TRANSPORT_LE
					)
				}
			}
			
			override fun onScanFailed(errorCode: Int) {
				Log.e("BleInput", "Scan failed: $errorCode")
				_connectionState.value = BleConnectionState(error = "Scan failed: $errorCode")
				close()
			}
		}
		
		// 开始扫描
		val filters = listOf(
			ScanFilter.Builder()
				.setServiceUuid(ParcelUuid(UUID.fromString(BleConfig.SERVICE_UUID)))
				.build()
		)
		val settings = ScanSettings.Builder()
			.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
			.build()
		
		scanner?.startScan(filters, settings, scanCallback)
		
		awaitClose {
			Log.d("BleInput", "Closing BLE connection")
			scanner?.stopScan(scanCallback)
			gatt?.disconnect()
			gatt?.close()
			_connectionState.value = BleConnectionState()
		}
	}
	
	@SuppressLint("MissingPermission")
	private fun setupNotifications(gatt: BluetoothGatt) {
		try {
			val service = gatt.getService(UUID.fromString(BleConfig.SERVICE_UUID))
			if (service == null) {
				Log.e("BleInput", "Service not found")
				return
			}
			
			// 设置图片特征通知
			val photoChar = service.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CHAR_UUID))
			if (photoChar != null) {
				gatt.setCharacteristicNotification(photoChar, true)
				val descriptor = photoChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
				descriptor?.let {
					it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
					gatt.writeDescriptor(it)
				}
				Log.d("BleInput", "Photo notifications enabled")
			}
			
			// 音频特征通知已禁用，只专注图片功能
			// 可在需要设备录音时按需开启
			val audioChar = service.getCharacteristic(UUID.fromString(BleConfig.AUDIO_CHAR_UUID))
			if (audioChar != null) {
				gatt.setCharacteristicNotification(audioChar, true)
				val descriptor = audioChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
				descriptor?.let {
					it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
					gatt.writeDescriptor(it)
				}
				Log.d("BleInput", "Audio notifications enabled")
			}
			
			// 注意：不再在连接后自动触发拍照或自动拍照，改为由上层显式调用
			// 这避免“设备录音”时误触发拍照与上传
			
		} catch (e: Exception) {
			Log.e("BleInput", "Error setting up notifications", e)
		}
	}

	// 安全地发送重传请求（向控制特征写入 0xFE）
	@SuppressLint("MissingPermission")
	private fun requestRetransmissionSafely() {
		try {
			val service = gatt?.getService(UUID.fromString(BleConfig.SERVICE_UUID)) ?: return
			val control = service.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CONTROL_CHAR_UUID)) ?: return
			control.value = byteArrayOf(0xFE.toByte())
			val ok = gatt?.writeCharacteristic(control) ?: false
			Log.i("[PHOTO_PREVIEW] BleInput", "🔄 发送重传请求 0xFE，写入结果: $ok")
		} catch (e: Exception) {
			Log.e("[PHOTO_PREVIEW] BleInput", "❌ 重传请求失败", e)
		}
	}
	
	@SuppressLint("MissingPermission")
	fun disconnect() {
		gatt?.disconnect()
		gatt?.close()
		scanner?.stopScan(scanCallback)
		_connectionState.value = BleConnectionState()
	}
	
	@SuppressLint("MissingPermission")
	fun takePhoto() {
		val service = gatt?.getService(UUID.fromString(BleConfig.SERVICE_UUID))
		val controlChar = service?.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CONTROL_CHAR_UUID))
		if (controlChar != null) {
			controlChar.value = byteArrayOf(-1) // 拍一张照片
			val success = gatt?.writeCharacteristic(controlChar) ?: false
			Log.i("BleInput", "📸 BLE_PHOTO: 手动触发拍照，写入结果: $success")
			
			if (success) {
				Log.i("BleInput", "📸 BLE_PHOTO: 拍照命令发送成功，等待设备响应...")
			} else {
				Log.e("BleInput", "❌ BLE_PHOTO: 拍照命令发送失败")
			}
		} else {
			Log.e("BleInput", "🔍 BLE_PHOTO: 找不到拍照控制特征")
		}
	}

	// ===== 设备录音 capture APIs =====
	fun startAudioCapture() {
		isCapturingAudio = true
		audioBuffer = java.io.ByteArrayOutputStream()
		// 不再向拍照控制特征写入任何值，避免误触发拍照/连拍
		Log.i("BleIn", "[AUDIO_REC_BLE] 🎙️ 设备录音开始（缓存收集）")
	}

	fun stopAudioCapture(): ByteArray {
		isCapturingAudio = false
		// 不再向拍照控制特征写入任何值，避免误触发拍照/连拍
		val bytes = audioBuffer?.toByteArray() ?: ByteArray(0)
		audioBuffer = null
		Log.i("BleIn", "[AUDIO_REC_BLE] 🛑 设备录音结束，大小: ${bytes.size} bytes")
		return bytes
	}

	// 真实的蓝牙拍照功能 - 等待设备响应
	
	@SuppressLint("MissingPermission")
	fun startAutoPhoto(intervalSeconds: Int = 5) {
		val service = gatt?.getService(UUID.fromString(BleConfig.SERVICE_UUID))
		val controlChar = service?.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CONTROL_CHAR_UUID))
		if (controlChar != null) {
			controlChar.value = byteArrayOf(intervalSeconds.toByte()) // 设置间隔
			gatt?.writeCharacteristic(controlChar)
			Log.d("BleInput", "Auto photo started with ${intervalSeconds}s interval")
		}
	}
	
	@SuppressLint("MissingPermission")
	fun stopAutoPhoto() {
		val service = gatt?.getService(UUID.fromString(BleConfig.SERVICE_UUID))
		val controlChar = service?.getCharacteristic(UUID.fromString(BleConfig.PHOTO_CONTROL_CHAR_UUID))
		if (controlChar != null) {
			controlChar.value = byteArrayOf(0) // 停止拍照
			gatt?.writeCharacteristic(controlChar)
			Log.d("BleInput", "Auto photo stopped")
		}
	}
}

fun saveImageToGallery(context: Context, imageBytes: ByteArray, fileName: String) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BlePhotos")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        resolver.openOutputStream(it)?.use { outputStream: OutputStream ->
            outputStream.write(imageBytes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
		}
	}
}

