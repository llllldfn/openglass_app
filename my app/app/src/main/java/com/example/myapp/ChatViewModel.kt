package com.example.myapp

import android.app.Application
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.myapp.data.repository.AiRepository
import com.example.myapp.data.repository.InputData
import com.example.myapp.data.repository.LoadState
import com.example.myapp.input.audio.AudioInput
import com.example.myapp.input.ble.BleInput
import com.example.myapp.input.camera.CameraInput
import com.example.myapp.model.ChatMessage
import com.example.myapp.model.Role
import com.example.myapp.output.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.Observer
import com.example.myapp.model.AiResponse
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.content.Context
import android.media.AudioManager

data class ChatUiState(
	val messages: List<ChatMessage> = emptyList(),
	val inputText: String = "",
	val error: String? = null,
	val cameraOn: Boolean = false,
	val cameraMode: String = "手机相机", // 新增相机模式
	val audioOn: Boolean = false,
	val bleOn: Boolean = false,
	val ttsOn: Boolean = true,
	val lastImageTimestampMs: Long? = null,
	val lastAudioTimestampMs: Long? = null,
	val bleConnectionState: com.example.myapp.input.ble.BleConnectionState = com.example.myapp.input.ble.BleConnectionState(),
	val audioUploadEnabled: Boolean = false,  // 新增音频上传开关
	val blePhotoPreview: ByteArray? = null,  // 新增蓝牙图片预览
	val blePhotoTimestamp: Long? = null
 
)

class ChatViewModel(
	application: Application,
	private val repository: AiRepository,
	private val tts: TtsManager
) : AndroidViewModel(application) {
	private val _ui = MutableStateFlow(ChatUiState())
	val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

	private val camera = CameraInput(getApplication())
	private val audio = AudioInput(getApplication())
	private val ble = BleInput(getApplication())

	private fun newUuid(): String = java.util.UUID.randomUUID().toString()

	private var cameraJob: Job? = null
	private var audioJob: Job? = null
	private var bleJob: Job? = null

    // Phone recording state
    private var phoneRecorder: AudioRecord? = null
    private var phoneRecJob: Job? = null
    private var phoneRecBuffer: ByteArrayOutputStream? = null
    private var phoneSampleRate = 16000

    // Device recording state
    private var deviceRecJob: Job? = null
	
	private var isTakingPhoto = false
	
	init {
		// 监听蓝牙图片数据流
		viewModelScope.launch(Dispatchers.IO) {
			ble.photoData.collect { imageBytes ->
				Log.i("[PHOTO_PREVIEW] ChatViewModel", "📸 BLE_PHOTO_STREAM: 收到蓝牙图片数据流 ${imageBytes.size} bytes")
				processBleImage(imageBytes)
			}
		}
	}
	
	private suspend fun processBleImage(imageBytes: ByteArray) {
		Log.i("[PHOTO_PREVIEW] ChatViewModel", "🔄 BLE_PHOTO_PROCESS: 收到完整蓝牙图片 ${imageBytes.size} bytes")
		
		// 打印图片数据的前几个字节用于调试
		if (imageBytes.size > 0) {
			val hexString = imageBytes.take(16).joinToString("") { "%02X".format(it) }
			Log.i("[PHOTO_PREVIEW] ChatViewModel", "🔍 BLE_PHOTO_PROCESS: 收到图片前16字节: $hexString")
		}
		
		// BleInput已经组装好了完整图片，直接处理
		processCompleteBleImage(imageBytes)
	}
	
	private suspend fun processCompleteBleImage(completeImage: ByteArray) {
		Log.i("[PHOTO_PREVIEW] ChatViewModel", "🖼️ BLE_PHOTO_PROCESS: 处理完整蓝牙图片 ${completeImage.size} bytes")
		
		// 图片已经在BleInput中组装完成，直接使用
		Log.i("[PHOTO_PREVIEW] ChatViewModel", "✅ BLE_PHOTO_PROCESS: 图片数据已就绪，总大小: ${completeImage.size} bytes")
		
		// 验证图片数据
		val isValid = com.example.myapp.utils.ImageUtils.isValidImage(completeImage)
		val imageInfo = com.example.myapp.utils.ImageUtils.getImageInfo(completeImage)
		Log.i("[PHOTO_PREVIEW] ChatViewModel", "🔍 BLE_PHOTO_PROCESS: 图片验证结果: $isValid, 图片信息: $imageInfo")
		
		// 检查图片大小是否合理 - 2180 bytes对于OpenGlass来说完全正常
		if (completeImage.size < 500) { // 降低到500 bytes，因为OpenGlass的图片通常较小
			Log.e("[PHOTO_PREVIEW] ChatViewModel", "❌ BLE_PHOTO_PROCESS: 图片太小 (${completeImage.size} bytes)，可能只包含JPEG头")
			_ui.value = _ui.value.copy(error = "蓝牙图片数据太小 (${completeImage.size} bytes)，请重新拍照")
			return
		}
		
		// 保存图片预览
		_ui.value = _ui.value.copy(
			blePhotoPreview = completeImage,
			blePhotoTimestamp = System.currentTimeMillis()
		)
		Log.i("[PHOTO_PREVIEW] ChatViewModel", "🖼️ BLE_PHOTO_PROCESS: 图片预览已保存到UI状态")
		
		// 强制刷新UI状态
		viewModelScope.launch(Dispatchers.Main) {
			// 延迟一下确保状态更新
			kotlinx.coroutines.delay(100)
			// 再次确认状态
			Log.i("[PHOTO_PREVIEW] ChatViewModel", "🔄 UI状态确认: blePhotoPreview=${_ui.value.blePhotoPreview?.size ?: "null"} bytes, blePhotoTimestamp=${_ui.value.blePhotoTimestamp}")
		}
		
		// 添加用户消息显示收到图片
		val userMessage = ChatMessage(
			id = System.currentTimeMillis().toString(),
			role = Role.User,
			text = "📷 蓝牙拍照完成 (${completeImage.size} bytes)"
		)
		_ui.value = _ui.value.copy(messages = _ui.value.messages + userMessage)
		
		// 发送图片给AI处理
		try {
			Log.i("[PHOTO_PREVIEW] ChatViewModel", "🤖 BLE_PHOTO_PROCESS: 发送完整图片给AI处理")
			val live = repository.sendToAi(InputData.Image(completeImage), getApplication())
			val observer = object : Observer<LoadState<AiResponse>> {
				override fun onChanged(state: LoadState<AiResponse>) {
					when (state) {
						is LoadState.Success -> {
							val replyText = state.data.choices?.firstOrNull()?.message?.content ?: ""
							Log.i("[PHOTO_PREVIEW] ChatViewModel", "✅ BLE_PHOTO_PROCESS: AI处理成功，回复长度: ${replyText.length}")
							val reply = ChatMessage(
								id = System.currentTimeMillis().toString(), 
								role = Role.Assistant, 
								text = replyText
							)
							_ui.value = _ui.value.copy(messages = _ui.value.messages + reply)
							if (replyText.isNotBlank() && _ui.value.ttsOn) tts.speak(replyText)
							live.removeObserver(this)
						}
						is LoadState.Error -> {
							Log.e("[PHOTO_PREVIEW] ChatViewModel", "❌ BLE_PHOTO_PROCESS: AI处理失败: ${state.throwable.message}", state.throwable)
							_ui.value = _ui.value.copy(error = "AI处理失败: ${state.throwable.message}")
							live.removeObserver(this)
						}
						is LoadState.Loading -> {
							Log.d("[PHOTO_PREVIEW] ChatViewModel", "⏳ BLE_PHOTO_PROCESS: AI处理中...")
						}
					}
				}
			}
			viewModelScope.launch(Dispatchers.Main) { live.observeForever(observer) }
		} catch (e: Exception) {
			Log.e("[PHOTO_PREVIEW] ChatViewModel", "💥 BLE_PHOTO_PROCESS: AI处理异常", e)
			_ui.value = _ui.value.copy(error = "AI处理异常: ${e.message}")
		}
	}

	fun bindCamera(owner: LifecycleOwner, preview: PreviewView) {
		viewModelScope.launch(Dispatchers.Main) {
			runCatching { camera.bind(owner, preview) }
				.onSuccess {
					// 绑定成功后再启动相机循环
					if (_ui.value.cameraOn && _ui.value.cameraMode == "手机相机") {
						startCameraLoop()
					}
				}
				.onFailure { e -> _ui.value = _ui.value.copy(error = e.message) }
		}
	}

	fun setCameraEnabled(enabled: Boolean) {
		_ui.value = _ui.value.copy(cameraOn = enabled)
		// 移除这里的自动启动相机循环逻辑，由 bindCamera 成功后触发
	}
	
	fun clearBlePhotoPreview() {
		_ui.value = _ui.value.copy(
			blePhotoPreview = null,
			blePhotoTimestamp = null
		)
	}
	
	fun onCameraPermissionGranted() {
		Log.d("[PHOTO_PREVIEW] ChatViewModel", "相机权限已授予，可以启动相机预览")
		// 如果相机模式是手机相机且相机已启用，尝试重新绑定相机
		if (_ui.value.cameraMode == "手机相机" && _ui.value.cameraOn) {
			Log.d("[PHOTO_PREVIEW] ChatViewModel", "尝试重新绑定相机预览")
			// 这里不需要立即绑定，因为LaunchedEffect会处理
		}
	}
	
	fun takePhonePhoto() {
		if (isTakingPhoto) return
		isTakingPhoto = true
		Log.d("[PHOTO_PREVIEW] ChatViewModel", "📸 开始手机拍照")
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val photoData = camera.captureJpegOnce()
				Log.d("[PHOTO_PREVIEW] ChatViewModel", "📸 手机拍照成功，图片大小: ${photoData.size} bytes")
				
				// 发送图片给AI处理
				val live = repository.sendToAi(InputData.Image(photoData), getApplication())
				val observer = object : Observer<LoadState<AiResponse>> {
					override fun onChanged(state: LoadState<AiResponse>) {
						when (state) {
							is LoadState.Loading -> { /* 可选：显示loading */ }
							is LoadState.Success, is LoadState.Error -> {
								isTakingPhoto = false // 处理完毕后允许再次拍照
								live.removeObserver(this)
							}
						}
					}
				}
				live.observeForever(observer)
			} catch (e: Exception) {
				isTakingPhoto = false
			}
		}
	}
	
	fun setCameraMode(mode: String) {
		_ui.value = _ui.value.copy(cameraMode = mode)
		// 如果相机已启用，根据新模式调整
		if (_ui.value.cameraOn) {
			if (mode == "手机相机") {
				startCameraLoop()
			} else {
				stopCameraLoop()
			}
		}
	}

	fun setBleEnabled(enabled: Boolean) {
		_ui.value = _ui.value.copy(bleOn = enabled)
		if (enabled) startBleLoop() else stopBleLoop()
	}

	private fun startBleLoop() {
		bleJob?.cancel()
		
		// 启动状态监听
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				ble.connectionState.collect { state ->
					Log.d("ChatViewModel", "BLE连接状态更新: isConnected=${state.isConnected}, deviceName=${state.deviceName}, error=${state.error}")
					_ui.value = _ui.value.copy(bleConnectionState = state)
					
					when {
						state.isScanning -> {
							_ui.value = _ui.value.copy(error = "正在扫描OpenGlass设备...")
						}
						state.isConnected -> {
							_ui.value = _ui.value.copy(error = "已连接到设备: ${state.deviceName}")
							// 连接成功时自动打开蓝牙开关
							_ui.value = _ui.value.copy(bleOn = true)
						}
						state.error != null -> {
							_ui.value = _ui.value.copy(error = "蓝牙错误: ${state.error}")
							_ui.value = _ui.value.copy(bleOn = false)
						}
					}
				}
			}.onFailure { e -> 
				Log.e("ChatViewModel", "BLE状态监听失败", e)
				_ui.value = _ui.value.copy(error = e.message, bleOn = false) 
			}
		}
		
		// 启动数据接收（现在只负责连接，图片数据通过photoData流处理）
		bleJob = viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				ble.scanAndConnect("OpenGlass").collect { bytes ->
					// 注意：现在图片数据通过photoData流处理，这里只处理音频数据
					// 音频数据来自AUDIO_CHAR_UUID
					
					// 只处理音频数据
					if (bytes.size <= 1000 && _ui.value.audioUploadEnabled) {
						Log.i("ChatViewModel", "🎵 BLE_AUDIO: 接收到音频数据 ${bytes.size} 字节")
						val message = ChatMessage(
							id = System.currentTimeMillis().toString(),
							role = Role.User,
							text = "🎵 收到蓝牙音频数据: ${bytes.size} 字节"
						)
						_ui.value = _ui.value.copy(messages = _ui.value.messages + message)
						
						// 发送音频给AI处理
						try {
							val live = repository.sendToAi(InputData.Audio(bytes), getApplication())
							val observer = object : Observer<LoadState<AiResponse>> {
								override fun onChanged(state: LoadState<AiResponse>) {
									when (state) {
										is LoadState.Success -> {
											val replyText = state.data.choices?.firstOrNull()?.message?.content ?: ""
											val reply = ChatMessage(id = System.currentTimeMillis().toString(), role = Role.Assistant, text = replyText)
											_ui.value = _ui.value.copy(messages = _ui.value.messages + reply)
											if (replyText.isNotBlank() && _ui.value.ttsOn) tts.speak(replyText)
											live.removeObserver(this)
										}
										is LoadState.Error -> { 
											_ui.value = _ui.value.copy(error = "AI处理失败: ${state.throwable.message}")
											live.removeObserver(this) 
										}
										is LoadState.Loading -> {
											Log.d("ChatViewModel", "AI处理中...")
										}
									}
								}
							}
							viewModelScope.launch(Dispatchers.Main) { live.observeForever(observer) }
						} catch (e: Exception) {
							_ui.value = _ui.value.copy(error = "AI处理异常: ${e.message}")
						}
					} else {
						// 如果音频上传关闭，则不显示任何消息，只记录日志
						Log.d("ChatViewModel", "音频数据已忽略: ${bytes.size} 字节")
				}
				}
			}.onFailure { e -> 
				_ui.value = _ui.value.copy(error = "蓝牙连接失败: ${e.message}")
				_ui.value = _ui.value.copy(bleOn = false)
			}
		}
	}

	private fun stopBleLoop() { 
		bleJob?.cancel(); 
		bleJob = null 
		ble.disconnect() // 断开蓝牙连接
	}

	fun setTtsEnabled(enabled: Boolean) {
		Log.d("[PHOTO_PREVIEW] ChatViewModel", "🔊 TTS状态切换: ${_ui.value.ttsOn} -> $enabled")
		_ui.value = _ui.value.copy(ttsOn = enabled)
		
		// 如果关闭TTS，立即停止当前播放
		if (!enabled) {
			tts.stop()
			Log.d("[PHOTO_PREVIEW] ChatViewModel", "🔊 TTS已关闭，停止当前播放")
		}
		
		Log.d("[PHOTO_PREVIEW] ChatViewModel", "🔊 TTS状态已更新: ${_ui.value.ttsOn}")
	}
	
	fun clearError() {
		_ui.value = _ui.value.copy(error = null)
	}

	// 新增功能方法
	fun takePhoto() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				// 这里应该调用相机的拍照方法，暂时用注释替代
				// camera.takePhoto()?.let { photo ->
				// 	_ui.value = _ui.value.copy(lastImageTimestampMs = System.currentTimeMillis())
				// 	val live = repository.sendToAi(InputData.Image(photo), getApplication())
				// 	// ... 处理拍照结果
				// }
				_ui.value = _ui.value.copy(error = "拍照功能暂未实现")
			}.onFailure { e -> _ui.value = _ui.value.copy(error = "拍照失败: ${e.message}") }
		}
	}
	
	fun takeBlePhoto() {
		if (_ui.value.bleOn && _ui.value.bleConnectionState.isConnected) {
			Log.i("ChatViewModel", "🚀 BLE_PHOTO_TRIGGER: 用户触发蓝牙拍照")
			ble.takePhoto()
			_ui.value = _ui.value.copy(error = "📸 已触发蓝牙设备拍照，等待图片数据...")
			
			// 启动蓝牙数据接收循环（如果还没有启动）
			if (bleJob?.isActive != true) {
				Log.d("ChatViewModel", "🔄 BLE_PHOTO_TRIGGER: 启动蓝牙数据接收循环")
				startBleLoop()
			}
		} else {
			Log.w("ChatViewModel", "⚠️ BLE_PHOTO_TRIGGER: 蓝牙未连接或未启用")
			_ui.value = _ui.value.copy(error = "蓝牙未连接或未启用")
		}
	}
	
	fun setAudioUploadEnabled(enabled: Boolean) {
		_ui.value = _ui.value.copy(audioUploadEnabled = enabled)
	}

	fun startRecording() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				// audio.startRecording()
				_ui.value = _ui.value.copy(audioOn = true)
				_ui.value = _ui.value.copy(error = "录音功能暂未实现")
			}.onFailure { e -> _ui.value = _ui.value.copy(error = "开始录音失败: ${e.message}") }
		}
	}

	fun stopRecording() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				// audio.stopRecording()?.let { audioData ->
				// 	_ui.value = _ui.value.copy(lastAudioTimestampMs = System.currentTimeMillis())
				// 	val live = repository.sendToAi(InputData.Audio(audioData), getApplication())
				// 	// ... 处理录音结果
				// }
				_ui.value = _ui.value.copy(audioOn = false)
			}.onFailure { e -> _ui.value = _ui.value.copy(error = "停止录音失败: ${e.message}") }
		}
	}



	fun startVoiceInput() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				// audio.startVoiceInput()
				_ui.value = _ui.value.copy(audioOn = true)
				_ui.value = _ui.value.copy(error = "语音输入功能暂未实现")
			}.onFailure { e -> _ui.value = _ui.value.copy(error = "开始语音输入失败: ${e.message}") }
		}
	}

	fun stopVoiceInput() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				// audio.stopVoiceInput()?.let { audioData ->
				// 	_ui.value = _ui.value.copy(lastAudioTimestampMs = System.currentTimeMillis())
				// 	val live = repository.sendToAi(InputData.Audio(audioData), getApplication())
				// 	// ... 处理语音输入结果
				// }
				_ui.value = _ui.value.copy(audioOn = false)
			}.onFailure { e -> _ui.value = _ui.value.copy(error = "停止语音输入失败: ${e.message}") }
		}
	}

    // ===== Phone recording (press-and-hold) =====
    fun startPhoneRecording() {
        if (phoneRecJob?.isActive == true) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i("ChatVM", "[AUDIO_REC] 🎙️ 开始手机录音: targetSampleRate=$phoneSampleRate")
                // 权限检查
                val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    getApplication(), android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    Log.w("ChatVM", "[AUDIO_REC] 缺少 RECORD_AUDIO 权限")
                    _ui.value = _ui.value.copy(error = "请授予麦克风权限后再试")
                    return@launch
                }

                // 请求音频焦点并设置通信模式
                val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val focus = am.requestAudioFocus(
                    AudioManager.OnAudioFocusChangeListener { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                Log.i("ChatVM", "[AUDIO_REC] 音频焦点请求结果=$focus")
                try { am.mode = AudioManager.MODE_IN_COMMUNICATION } catch (_: Exception) {}
                try { am.isMicrophoneMute = false } catch (_: Exception) {}

                // 立即选择可用的(音源, 采样率)组合
                val tryRates = intArrayOf(8000, 11025, 16000, 44100, 48000)
                val trySources = intArrayOf(
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.DEFAULT
                )
                var minBuf = 0
                var chosenSource = -1
                var finalRecorder: AudioRecord? = null
                outer@ for (src in trySources) {
                    for (rate in tryRates) {
                        val mb = AudioRecord.getMinBufferSize(
                            rate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        if (mb <= 0) continue
                        val r = AudioRecord(
                            src,
                            rate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            mb * 2
                        )
                        if (r.state == AudioRecord.STATE_INITIALIZED) {
                            finalRecorder = r
                            minBuf = mb
                            phoneSampleRate = rate
                            chosenSource = src
                            break@outer
                        } else {
                            try { r.release() } catch (_: Exception) {}
                        }
                    }
                }
                if (finalRecorder == null) throw IllegalStateException("AudioRecord init failed for all sources+rates")

                val sourceName = when (chosenSource) {
                    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
                    MediaRecorder.AudioSource.MIC -> "MIC"
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
                    else -> "DEFAULT"
                }
                Log.i("ChatVM", "[AUDIO_REC] 选用音源=$sourceName rate=$phoneSampleRate minBuf=$minBuf")

                phoneRecorder = finalRecorder
                try { finalRecorder.startRecording() } catch (_: Exception) {}
                Log.i("ChatVM", "[AUDIO_REC] startRecording called, state=${finalRecorder.state}, recState=${finalRecorder.recordingState}")
                _ui.value = _ui.value.copy(audioOn = true)
                phoneRecBuffer = ByteArrayOutputStream()

                phoneRecJob = viewModelScope.launch(Dispatchers.IO) {
                    Log.i("ChatVM", "[AUDIO_REC] 进入录音读循环")
                    val buf = ByteArray(minBuf * 2)
                    var total = 0
                    val startTs = System.currentTimeMillis()
                    while (this.isActive && finalRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                finalRecorder.read(buf, 0, buf.size, android.media.AudioRecord.READ_BLOCKING)
                            } else {
                                finalRecorder.read(buf, 0, buf.size)
                            }
                        } catch (e: Exception) { -1 }
                        Log.v("ChatVM", "[AUDIO_REC] loop read=$read state=${finalRecorder.recordingState}")
                        if (read > 0) {
                            phoneRecBuffer?.write(buf, 0, read)
                            total += read
                        } else if (read == 0) {
                            if ((System.currentTimeMillis() - startTs) % 1000L < 50L) {
                                Log.w("ChatVM", "[AUDIO_REC] read返回0，仍在等待音频... total=$total")
                            }
                        } else {
                            Log.e("ChatVM", "[AUDIO_REC] read返回错误: $read")
                        }
                        if ((System.currentTimeMillis() - startTs) % 1000L < 50L) {
                            Log.d("ChatVM", "[AUDIO_REC] 捕获中: totalBytes=$total")
                        }
                    }
                    Log.i("ChatVM", "[AUDIO_REC] 录音读循环结束 totalBytes=$total")
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "[AUDIO_REC] 开始手机录音失败", e)
                _ui.value = _ui.value.copy(error = "开始录音失败: ${e.message}")
            }
        }
    }

    // ===== Device recording (BLE) =====
    fun startDeviceRecording() {
        if (deviceRecJob?.isActive == true) return
        if (!_ui.value.bleConnectionState.isConnected) {
            _ui.value = _ui.value.copy(error = "请先连接蓝牙设备后再进行设备录音")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i("ChatVM", "[ASR_DEV] ▶️ 设备录音开始")
                ble.startAudioCapture()
                // 打开全局录音指示器（与手机录音共用），用于显示“录音中 mm:ss”
                _ui.value = _ui.value.copy(audioOn = true)
            } catch (e: Exception) {
                Log.e("ChatVM", "[ASR_DEV] 开始设备录音失败", e)
                _ui.value = _ui.value.copy(error = "开始设备录音失败: ${e.message}")
            }
        }
    }

    fun stopDeviceRecordingAndSend() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i("ChatVM", "[ASR_DEV] 🛑 停止设备录音并发送")
                val pcm = ble.stopAudioCapture()
                if (pcm.isEmpty()) {
                    Log.w("ChatVM", "[ASR_DEV] 设备录音为空")
                    _ui.value = _ui.value.copy(error = "设备录音为空")
                    _ui.value = _ui.value.copy(audioOn = false)
                    return@launch
                }
                // OpenGlass固件使用16kHz, 16-bit, 单声道PCM，小端序
                // 固件每帧160样本，但BLE包可能包含多个帧
                Log.i("ChatVM", "[ASR_DEV] 📊 PCM原始数据: ${pcm.size} bytes, 预期16kHz 16-bit mono")
                
                // 音频质量检测
                val audioQuality = analyzeAudioQuality(pcm)
                Log.i("ChatVM", "[ASR_DEV] 🎵 音频质量分析: $audioQuality")
                
                val cfg = com.example.myapp.settings.Settings.read(getApplication())
                Log.i("ChatVM", "[ASR_DEV] ▶️ 使用Baidu识别 devPid=${cfg.baiduDevPid}")
                
                // 尝试多种采样率以提高识别准确率
                val sampleRates = listOf(16000, 8000, 44100, 22050)
                var finalText = ""
                var bestResult = ""
                
                for (sampleRate in sampleRates) {
                    Log.i("ChatVM", "[ASR_DEV] 🔄 尝试采样率: ${sampleRate}Hz")
                    val wav = buildWavFromPcm16Mono(pcm, sampleRate)
                    val result = repository.baiduShortSpeech(wav, cfg.baiduApiKey, cfg.baiduSecretKey, cfg.baiduDevPid, sampleRate)
                    
                    result.onSuccess { text ->
                        Log.i("ChatVM", "[ASR_DEV] ✅ ${sampleRate}Hz 识别成功 textLen=${text.length}, text='$text'")
                        if (text.isNotBlank()) {
                            if (text.length > bestResult.length) {
                                bestResult = text
                            }
                            if (finalText.isBlank()) {
                                finalText = text
                            }
                        }
                    }.onFailure { e ->
                        Log.w("ChatVM", "[ASR_DEV] ❌ ${sampleRate}Hz 识别失败: ${e.message}")
                    }
                }
                
                // 使用最佳结果
                if (bestResult.isNotBlank()) {
                    finalText = bestResult
                }
                
                if (finalText.isBlank()) {
                    Log.w("ChatVM", "[ASR_DEV] ⚠️ 所有采样率识别均失败")
                    finalText = "音频识别失败，请重试"
                }
                
                val reply = ChatMessage(id = System.currentTimeMillis().toString(), role = Role.User, text = "📝 设备语音转写: $finalText")
                _ui.value = _ui.value.copy(messages = _ui.value.messages + reply)
                
                if (com.example.myapp.settings.Settings.read(getApplication()).forwardAsrToDoubao && finalText.isNotBlank() && finalText != "音频识别失败，请重试") {
                    try {
                        val live = repository.sendToAi(InputData.Text(finalText), getApplication())
                        val observer = object : Observer<LoadState<AiResponse>> {
                            override fun onChanged(state: LoadState<AiResponse>) {
                                when (state) {
                                    is LoadState.Success -> {
                                        val replyText = state.data.choices?.firstOrNull()?.message?.content ?: ""
                                        val aiMsg = ChatMessage(id = System.currentTimeMillis().toString(), role = Role.Assistant, text = replyText)
                                        _ui.value = _ui.value.copy(messages = _ui.value.messages + aiMsg)
                                        if (replyText.isNotBlank() && _ui.value.ttsOn) tts.speak(replyText)
                                        live.removeObserver(this)
                                    }
                                    is LoadState.Error -> { live.removeObserver(this) }
                                    is LoadState.Loading -> {}
                                }
                            }
                        }
                        viewModelScope.launch(Dispatchers.Main) { live.observeForever(observer) }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "[ASR_DEV] 停止设备录音失败", e)
                _ui.value = _ui.value.copy(error = "停止设备录音失败: ${e.message}")
            } finally {
                // 关闭全局录音指示器
                _ui.value = _ui.value.copy(audioOn = false)
            }
        }
    }

    fun stopPhoneRecordingAndSend() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i("ChatVM", "[AUDIO_REC] stopPhoneRecordingAndSend invoked")
                // stop and collect
                phoneRecorder?.let { rec ->
                    try { rec.stop() } catch (_: Exception) {}
                    try { rec.release() } catch (_: Exception) {}
                }
                phoneRecorder = null
                phoneRecJob?.cancel()
                val pcmBytes = phoneRecBuffer?.toByteArray() ?: ByteArray(0)
                phoneRecBuffer = null
                _ui.value = _ui.value.copy(audioOn = false)

                Log.i("ChatVM", "[AUDIO_REC] 🛑 结束手机录音: pcm=${pcmBytes.size} bytes")

                if (pcmBytes.isEmpty()) {
                    Log.w("ChatVM", "[AUDIO_REC] 录音为空，取消发送")
                    _ui.value = _ui.value.copy(error = "录音为空")
                    return@launch
                }

                // wrap to WAV (PCM16 mono)
                val wav = buildWavFromPcm16Mono(pcmBytes, phoneSampleRate)
                Log.i("ChatVM", "[AUDIO_REC] 📦 PCM 封装为 WAV: wav=${wav.size} bytes")

                // push user message
                val msg = ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    role = Role.User,
                    text = "🎙️ 发送语音 (${wav.size} bytes)"
                )
                _ui.value = _ui.value.copy(messages = _ui.value.messages + msg, lastAudioTimestampMs = System.currentTimeMillis())

                // send to AI
                val cfg = com.example.myapp.settings.Settings.read(getApplication())
                if (cfg.baiduApiKey.isNotBlank() && cfg.baiduSecretKey.isNotBlank()) {
                    Log.i("ChatVM", "[ASR_BAIDU] ▶️ 开始识别 devPid=${cfg.baiduDevPid}")
                    val result = repository.baiduShortSpeech(wav, cfg.baiduApiKey, cfg.baiduSecretKey, cfg.baiduDevPid, phoneSampleRate)
                    result.onSuccess { text ->
                        Log.i("ChatVM", "[ASR_BAIDU] ✅ 识别成功 textLen=${text.length}")
                        val reply = ChatMessage(id = System.currentTimeMillis().toString(), role = Role.User, text = "📝 语音转写: $text")
                        _ui.value = _ui.value.copy(messages = _ui.value.messages + reply, error = null)
                        // 若开启转发到豆包，继续把文本走AI聊天
                        if (com.example.myapp.settings.Settings.read(getApplication()).forwardAsrToDoubao && text.isNotBlank()) {
                            try {
                                val live = repository.sendToAi(InputData.Text(text), getApplication())
                                val observer = object : androidx.lifecycle.Observer<LoadState<AiResponse>> {
                                    override fun onChanged(state: LoadState<AiResponse>) {
                                        when (state) {
                                            is LoadState.Success -> {
                                                val replyText = state.data.choices?.firstOrNull()?.message?.content ?: ""
                                                val aiMsg = ChatMessage(id = System.currentTimeMillis().toString(), role = Role.Assistant, text = replyText)
                                                _ui.value = _ui.value.copy(messages = _ui.value.messages + aiMsg)
                                                if (replyText.isNotBlank() && _ui.value.ttsOn) tts.speak(replyText)
                                                live.removeObserver(this)
                                            }
                                            is LoadState.Error -> { live.removeObserver(this) }
                                            is LoadState.Loading -> {}
                                        }
                                    }
                                }
                                viewModelScope.launch(Dispatchers.Main) { live.observeForever(observer) }
                            } catch (_: Exception) {}
                        }
                    }.onFailure {
                        Log.e("ChatVM", "[ASR_BAIDU] ❌ 识别失败", it)
                        _ui.value = _ui.value.copy(error = "Baidu 识别失败: ${it.message}")
                    }
                } else {
                    Log.i("ChatVM", "[ASR_BAIDU] ⚠️ 未配置Baidu密钥，已移除AUC，无法识别")
                    try {
                        val filename = "speech_${System.currentTimeMillis()}.wav"
                        val urlResult = com.example.myapp.utils.HttpUpload.uploadPublicUrl(wav, filename)
                        val url = urlResult.getOrThrow()
                        Log.i("ChatVM", "[ASR_BAIDU] 🌐 已上传到临时URL：$url（仅调试）")
                        _ui.value = _ui.value.copy(error = "已移除AUC流程，请在设置中填写Baidu信息使用语音识别")
                    } catch (e: Exception) {
                        Log.e("ChatVM", "[ASR_BAIDU] 上传调试URL失败", e)
                        _ui.value = _ui.value.copy(error = "音频上传或AUC调用失败: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "[AUDIO_REC] 停止手机录音并发送失败", e)
                _ui.value = _ui.value.copy(error = "停止录音失败: ${e.message}")
            }
        }
    }

    private fun buildWavFromPcm16Mono(pcm: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcm.size + 36
        val byteRate = sampleRate * 2 // mono, 16-bit
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk descriptor
        header.put("RIFF".toByteArray())
        header.order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        // fmt sub-chunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1) // AudioFormat PCM
        header.putShort(1) // NumChannels = 1
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(2) // BlockAlign = NumChannels * BitsPerSample/8
        header.putShort(16) // BitsPerSample
        // data sub-chunk
        header.put("data".toByteArray())
        header.putInt(pcm.size)
        val out = ByteArrayOutputStream()
        out.write(header.array())
        out.write(pcm)
        return out.toByteArray()
	}

	fun switchCamera() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				// camera.switchCamera()
				_ui.value = _ui.value.copy(error = "切换相机功能暂未实现")
			}.onFailure { e -> _ui.value = _ui.value.copy(error = "切换相机失败: ${e.message}") }
		}
	}

	fun scanBleDevices() {
		// 启动蓝牙扫描和连接
		setBleEnabled(true)
	}

	fun clearMessages() {
		_ui.value = _ui.value.copy(messages = emptyList())
	}

	fun deleteMessage(messageId: String) {
		_ui.value = _ui.value.copy(
			messages = _ui.value.messages.filter { it.id != messageId }
		)
	}

	fun resendMessage(messageId: String) {
		val message = _ui.value.messages.find { it.id == messageId }
		message?.let {
			// 重新发送消息
			val newMessage = ChatMessage(
				id = System.currentTimeMillis().toString(),
				role = it.role,
				text = it.text
			)
			_ui.value = _ui.value.copy(messages = _ui.value.messages + newMessage)
			
			// 发送给AI处理
				viewModelScope.launch(Dispatchers.IO) {
				try {
						val live = repository.sendToAi(InputData.Text(it.text), getApplication())
						val observer = object : Observer<LoadState<AiResponse>> {
							override fun onChanged(state: LoadState<AiResponse>) {
								when (state) {
									is LoadState.Success -> {
										val replyText = state.data.choices?.firstOrNull()?.message?.content ?: ""
									val reply = ChatMessage(
										id = System.currentTimeMillis().toString(),
										role = Role.Assistant,
										text = replyText
									)
										_ui.value = _ui.value.copy(messages = _ui.value.messages + reply)
										if (replyText.isNotBlank() && _ui.value.ttsOn) tts.speak(replyText)
										live.removeObserver(this)
									}
								is LoadState.Error -> {
									_ui.value = _ui.value.copy(error = "AI处理失败: ${state.throwable.message}")
									live.removeObserver(this)
								}
								is LoadState.Loading -> {
									Log.d("ChatViewModel", "AI处理中...")
								}
								}
							}
						}
						viewModelScope.launch(Dispatchers.Main) { live.observeForever(observer) }
				} catch (e: Exception) {
					_ui.value = _ui.value.copy(error = "AI处理异常: ${e.message}")
				}
			}
		}
	}

	fun sendMessage(text: String) {
		if (text.isBlank()) return
		
		val message = ChatMessage(
			id = System.currentTimeMillis().toString(),
			role = Role.User,
			text = text
		)
		_ui.value = _ui.value.copy(messages = _ui.value.messages + message, inputText = "")
		
		// 发送给AI处理
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val live = repository.sendToAi(InputData.Text(text), getApplication())
				val observer = object : Observer<LoadState<AiResponse>> {
					override fun onChanged(state: LoadState<AiResponse>) {
						when (state) {
							is LoadState.Success -> {
								val replyText = state.data.choices?.firstOrNull()?.message?.content ?: ""
								val reply = ChatMessage(
									id = System.currentTimeMillis().toString(),
									role = Role.Assistant,
									text = replyText
								)
								_ui.value = _ui.value.copy(messages = _ui.value.messages + reply)
								if (replyText.isNotBlank() && _ui.value.ttsOn) tts.speak(replyText)
								live.removeObserver(this)
							}
							is LoadState.Error -> {
								_ui.value = _ui.value.copy(error = "AI处理失败: ${state.throwable.message}")
								live.removeObserver(this)
							}
							is LoadState.Loading -> {
								Log.d("ChatViewModel", "AI处理中...")
							}
						}
					}
				}
				viewModelScope.launch(Dispatchers.Main) { live.observeForever(observer) }
			} catch (e: Exception) {
				_ui.value = _ui.value.copy(error = "AI处理异常: ${e.message}")
			}
		}
	}

	fun startCameraLoop() {
		cameraJob?.cancel()
		cameraJob = viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				camera.periodicJpegFlow(intervalMs = 5000L).collect { photo ->
					_ui.value = _ui.value.copy(lastImageTimestampMs = System.currentTimeMillis())
					val message = ChatMessage(
						id = System.currentTimeMillis().toString(),
						role = Role.User,
						text = "📷 手机拍照 (${photo.size} bytes)"
					)
					_ui.value = _ui.value.copy(messages = _ui.value.messages + message)
					// 发送图片给AI处理
					try {
						val live = repository.sendToAi(InputData.Image(photo), getApplication())
						val observer = object : Observer<LoadState<AiResponse>> {
							override fun onChanged(state: LoadState<AiResponse>) {
								when (state) {
									is LoadState.Success -> {
										val replyText = state.data.choices?.firstOrNull()?.message?.content ?: ""
										val reply = ChatMessage(
											id = System.currentTimeMillis().toString(),
											role = Role.Assistant,
											text = replyText
										)
										_ui.value = _ui.value.copy(messages = _ui.value.messages + reply)
										if (replyText.isNotBlank() && _ui.value.ttsOn) tts.speak(replyText)
										live.removeObserver(this)
									}
									is LoadState.Error -> {
										_ui.value = _ui.value.copy(error = "AI处理失败: ${state.throwable.message}")
										live.removeObserver(this)
									}
									is LoadState.Loading -> {
										Log.d("ChatViewModel", "AI处理中...")
									}
								}
							}
						}
						viewModelScope.launch(Dispatchers.Main) { live.observeForever(observer) }
					} catch (e: Exception) {
						_ui.value = _ui.value.copy(error = "AI处理异常: ${e.message}")
					}
				}
			}.onFailure { e ->
				_ui.value = _ui.value.copy(error = "相机循环启动失败: ${e.message}")
			}
		}
	}

	fun stopCameraLoop() {
		cameraJob?.cancel()
		cameraJob = null
	}
	
	// 音频质量分析函数
	private fun analyzeAudioQuality(pcm: ByteArray): String {
		if (pcm.size < 4) return "数据过短"
		
		val samples = pcm.size / 2 // 16-bit = 2 bytes per sample
		val duration = samples / 16000.0 // 假设16kHz采样率
		
		// 计算音频统计信息
		var sum = 0.0
		var maxAmplitude = 0.0
		var zeroCrossings = 0
		
		for (i in 0 until pcm.size - 1 step 2) {
			val sample = (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)
			val amplitude = kotlin.math.abs(sample.toDouble())
			
			sum += amplitude
			if (amplitude > maxAmplitude) maxAmplitude = amplitude
			
			// 检测过零点
			if (i > 0) {
				val prevSample = (pcm[i - 1].toInt() shl 8) or (pcm[i - 2].toInt() and 0xFF)
				if ((prevSample < 0 && sample >= 0) || (prevSample >= 0 && sample < 0)) {
					zeroCrossings++
				}
			}
		}
		
		val avgAmplitude = sum / samples
		val dynamicRange = if (avgAmplitude > 0) 20 * kotlin.math.log10(maxAmplitude / avgAmplitude) else 0.0
		val frequency = if (duration > 0) zeroCrossings / (2.0 * duration) else 0.0
		
		return "时长=${String.format("%.2f", duration)}s, 样本数=$samples, " +
			   "平均幅度=${String.format("%.0f", avgAmplitude)}, " +
			   "最大幅度=${String.format("%.0f", maxAmplitude)}, " +
			   "动态范围=${String.format("%.1f", dynamicRange)}dB, " +
			   "主频=${String.format("%.0f", frequency)}Hz"
	}
}


