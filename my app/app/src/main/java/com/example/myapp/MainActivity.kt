package com.example.myapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.example.myapp.data.network.AiApi
import com.example.myapp.data.repository.AiRepository
import com.example.myapp.output.tts.TtsManager
import com.example.myapp.ui.chat.ChatScreen
import com.example.myapp.ChatViewModel
import com.example.myapp.ui.settings.SettingsScreen
import com.example.myapp.ui.settings.BluetoothManagerScreen
import com.example.myapp.ui.settings.ImageSettingsScreen
import com.example.myapp.ui.settings.ImageSettings
import com.example.myapp.ui.theme.MyAppTheme
import com.example.myapp.settings.Settings

class MainActivity : ComponentActivity() {
	private val viewModelFactory by lazy {
		val cfg = Settings.read(applicationContext)
		val api = AiApi.create(context = applicationContext, baseUrl = cfg.baseUrl)
		val repo = AiRepository(api)
		val tts = TtsManager(applicationContext)
		ChatViewModelFactory(application, repo, tts)
	}

	private val vm: ChatViewModel by viewModels { viewModelFactory }

	// 添加权限状态跟踪
	private var cameraPermissionGranted by mutableStateOf(false)
	private var audioPermissionGranted by mutableStateOf(false)
	private var bluetoothPermissionsGranted by mutableStateOf(false)

	private val permissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		// 处理权限请求结果
		cameraPermissionGranted = permissions[Manifest.permission.CAMERA] == true
		audioPermissionGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			bluetoothPermissionsGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] == true && 
										 permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
		} else {
			bluetoothPermissionsGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
		}
		
		// 记录权限状态
		android.util.Log.d("MainActivity", "权限状态更新: 相机=$cameraPermissionGranted, 音频=$audioPermissionGranted, 蓝牙=$bluetoothPermissionsGranted")
		
		// 如果相机权限被授予，通知ChatViewModel
		if (cameraPermissionGranted) {
			vm.onCameraPermissionGranted()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		requestRuntimePermissions()
		setContent {
			MyAppTheme {
				Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
					// 使用 remember 来管理导航状态
					var showSettings by remember { mutableStateOf(false) }
					var showBluetoothManager by remember { mutableStateOf(false) }
					var showImageSettings by remember { mutableStateOf(false) }
					var currentImageSettings by remember { mutableStateOf(ImageSettings()) }
					
					// 添加相机模式和蓝牙模式的状态管理
					var cameraMode by remember { mutableStateOf(true) }
					var bleMode by remember { mutableStateOf(false) }
					var bleConnected by remember { mutableStateOf(false) }
					
					// 添加状态持久化
					DisposableEffect(Unit) {
						android.util.Log.d("MainActivity", "MainActivity DisposableEffect - Component entered")
						onDispose {
							android.util.Log.d("MainActivity", "MainActivity DisposableEffect - Component disposed")
						}
					}
					
					// 添加状态变化监控
					LaunchedEffect(showSettings, showBluetoothManager, showImageSettings) {
						android.util.Log.d("MainActivity", "Navigation state changed - showSettings: $showSettings, showBluetoothManager: $showBluetoothManager, showImageSettings: $showImageSettings")
					}
					
					// 添加调试信息
					android.util.Log.d("MainActivity", "Current state - showSettings: $showSettings, showBluetoothManager: $showBluetoothManager, showImageSettings: $showImageSettings")
					
					// 添加调试信息
					LaunchedEffect(showSettings) {
						android.util.Log.d("MainActivity", "showSettings changed to: $showSettings")
					}
					
					// 添加更多调试信息
					LaunchedEffect(Unit) {
						android.util.Log.d("MainActivity", "MainActivity onCreate/LaunchedEffect triggered")
					}
					
					when {
						showImageSettings -> {
							ImageSettingsScreen(
								onBack = { 
									android.util.Log.d("MainActivity", "ImageSettings back button clicked")
									showImageSettings = false 
								},
								onSettingsChanged = { settings -> 
									currentImageSettings = settings
									// TODO: 保存图片设置到本地存储
								},
								currentSettings = currentImageSettings
							)
						}
						showBluetoothManager -> {
							BluetoothManagerScreen(
								onBack = { 
									android.util.Log.d("MainActivity", "BluetoothManager back button clicked")
									showBluetoothManager = false 
								},
								onDeviceSelected = { device ->
									// 处理蓝牙设备连接
									showBluetoothManager = false
								},
								onPairDevice = { device ->
									// 处理蓝牙设备配对 - 调用系统配对功能
									try {
										// 创建配对
										if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
											if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
												device.createBond()
											}
										} else {
											device.createBond()
										}
										// 配对完成后关闭蓝牙管理器
										showBluetoothManager = false
									} catch (e: Exception) {
										// 处理配对失败
									}
								}
							)
						}
						showSettings -> {
							android.util.Log.d("MainActivity", "Showing SettingsScreen")
							// 获取ChatViewModel的UI状态
							val chatUi by vm.ui.collectAsState()
							SettingsScreen(
								onBack = { 
									// 添加调试信息
									android.util.Log.d("MainActivity", "Settings back button clicked")
									showSettings = false 
									// 确保返回到主界面，而不是重新加载应用
								},
								onCameraModeChange = { mode -> 
									cameraMode = mode
									vm.setCameraMode(if (mode) "手机相机" else "蓝牙相机")
								},
								onBleModeChange = { mode -> 
									bleMode = mode
									vm.setBleEnabled(mode)
								},
								onImageSettingsClick = { showImageSettings = true },
								onBluetoothManagerClick = { showBluetoothManager = true },
								cameraMode = cameraMode,
								bleMode = bleMode,
								bleConnected = chatUi.bleConnectionState.isConnected
							)
						}
						else -> {
							// 使用优化后的聊天界面
							android.util.Log.d("MainActivity", "Showing ChatScreen")
							ChatScreen(
								vm = vm,
								onSettingsClick = { 
									android.util.Log.d("MainActivity", "Settings button clicked")
									showSettings = true 
								},
								cameraPermissionGranted = cameraPermissionGranted
							)
						}
					}
				}
			}
		}
	}

	private fun requestRuntimePermissions() {
		val perms = mutableListOf(
			Manifest.permission.CAMERA,
			Manifest.permission.RECORD_AUDIO
		)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			perms += listOf(
				Manifest.permission.BLUETOOTH_SCAN,
				Manifest.permission.BLUETOOTH_CONNECT
			)
		} else {
			perms += Manifest.permission.ACCESS_FINE_LOCATION
		}
		permissionLauncher.launch(perms.toTypedArray())
	}
}

class ChatViewModelFactory(
	private val application: android.app.Application,
	private val repository: com.example.myapp.data.repository.AiRepository,
	private val ttsManager: com.example.myapp.output.tts.TtsManager
) : androidx.lifecycle.ViewModelProvider.Factory {
	@Suppress("UNCHECKED_CAST")
	override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
		return ChatViewModel(application, repository, ttsManager) as T
	}
}