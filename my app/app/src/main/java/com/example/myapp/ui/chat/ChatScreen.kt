package com.example.myapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapp.ChatUiState
import com.example.myapp.ChatViewModel
import com.example.myapp.model.ChatMessage
import com.example.myapp.model.Role
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import android.util.Log
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.TextButton
import androidx.compose.ui.zIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    cameraPermissionGranted: Boolean,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showInput by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // 添加循环拍照状态控制
    var isLoopingPhoto by remember { mutableStateOf(false) }
    // AUC 音频链接输入
    var showAucDialog by remember { mutableStateOf(false) }
    var aucUrl by remember { mutableStateOf("") }
    // 录音选择弹窗
    var showRecordPicker by remember { mutableStateOf(false) }
    // 录音时长（秒）
    var recElapsedSec by remember { mutableStateOf(0) }
    // 长按视觉反馈状态
    var pressingDevice by remember { mutableStateOf(false) }
    var pressingPhone by remember { mutableStateOf(false) }

    // 本地消息项（带秒级时间戳）
    @Composable
    fun ChatMessageItemWithTimestamp(message: ChatMessage) {
        val isUser = message.role == Role.User
        val backgroundColor =
            if (isUser) Color.Blue.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.6f)
        val textColor = Color.White
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Text(
                        text = if (isUser) "用户" else "AI助手",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(
                            java.util.Date(message.id.toLongOrNull() ?: System.currentTimeMillis())
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }
    }

    // 本地定义的气泡组件，确保作用域可见
    @Composable
    fun MessageItem(message: ChatMessage) {
        val isUser = message.role == Role.User
        val backgroundColor =
            if (isUser) Color.Blue.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.6f)
        val textColor = Color.White
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Text(
                        text = if (isUser) "用户" else "AI助手",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(
                            java.util.Date(message.id.toLongOrNull() ?: System.currentTimeMillis())
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }
    }
    LaunchedEffect(ui.audioOn) {
        if (ui.audioOn) {
            recElapsedSec = 0
            while (true) {
                kotlinx.coroutines.delay(1000)
                recElapsedSec += 1
            }
        } else {
            recElapsedSec = 0
        }
    }

    // 自动打开相机 - 修改为只在用户手动控制时开启
    LaunchedEffect(cameraPermissionGranted, isLoopingPhoto) {
        if (cameraPermissionGranted && isLoopingPhoto) {
            vm.setCameraEnabled(true)
        } else if (!isLoopingPhoto) {
            vm.setCameraEnabled(false)
        }
    }

    LaunchedEffect(ui.cameraOn, previewView, cameraPermissionGranted, isLoopingPhoto) {
        if (cameraPermissionGranted && ui.cameraOn && previewView != null && isLoopingPhoto) {
            Log.d("ChatScreen", "尝试绑定相机预览")
            vm.bindCamera(lifecycleOwner, previewView!!)
            Log.d("ChatScreen", "相机预览绑定成功")
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 主要内容区域
        Box(modifier = Modifier.weight(1f)) {
            // 相机预览区域 - 默认显示手机摄像头实时画面
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(1f)
            ) {
                // 仅在相机已开启时创建 PreviewView；否则保持空黑底
                if (cameraPermissionGranted && ui.cameraOn) {
                    AndroidView(
                        factory = { context ->
                            PreviewView(context).apply {
                                this.scaleType =
                                    androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                                previewView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!cameraPermissionGranted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "请授予相机权限以使用相机预览",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                // 移除相机未开启时的遮罩与文字提示
                // 保持背景为纯黑，不显示额外 UI
            }

            // 蓝牙图片预览（真实图片）放在AI交互层下面
            ui.blePhotoPreview?.let { photoData ->
                val imageBitmap = remember(photoData) {
                    try {
                        android.graphics.BitmapFactory.decodeByteArray(photoData, 0, photoData.size)
                            ?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
                if (imageBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = imageBitmap,
                        contentDescription = "蓝牙图片预览",
                        modifier = Modifier.fillMaxSize().zIndex(2f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.9f))
                            .zIndex(2f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.BrokenImage,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("图片解码失败 (${photoData.size} bytes)", color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    vm.clearBlePhotoPreview()
                                    isLoopingPhoto = false
                                    vm.stopCameraLoop()
                                    vm.setCameraEnabled(false)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) { Text("关闭预览") }
                        }
                    }
                }
            }

            // AI聊天区域 - 半透明覆盖层（置于最上层）
            if (ui.messages.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .zIndex(3f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = true
                ) {
                    items(ui.messages.reversed()) { message ->
                        MessageItem(message = message)
                    }
                }
            }

            // 统一管理录音相关悬浮UI（最高层）
            Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
                // 录音中提示（右上角）
                if (ui.audioOn) {
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val mm = recElapsedSec / 60
                            val ss = recElapsedSec % 60
                            Text(
                                "录音中  %02d:%02d".format(mm, ss),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // 录音选择弹窗：设备录音 / 手机录音，长按录音（位于相同层，顺序在后→显示更上层）
                if (showRecordPicker) {
                    Dialog(
                        onDismissRequest = { showRecordPicker = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "选择录音方式（长按开始，松开发送）",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // 设备录音（长按）
                                    Box(
                                        modifier = Modifier
                                            .height(56.dp)
                                            .weight(1f)
                                            .background(
                                                if (pressingDevice) Color(0xFFB71C1C) else Color(
                                                    0xFF455A64
                                                ),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        val canRecordByDevice =
                                                            ui.bleConnectionState.isConnected
                                                        if (!canRecordByDevice) {
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "请先连接蓝牙设备后再进行设备录音",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                            return@detectTapGestures
                                                        }
                                                        pressingDevice = true
                                                        vm.startDeviceRecording()
                                                        try {
                                                            // 等待手指松开，手指按住时间过长会自动停止录音
                                                            tryAwaitRelease()
                                                        } finally {
                                                            pressingDevice = false
                                                            vm.stopDeviceRecordingAndSend()
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("设备录音（长按）", color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    // 手机录音（长按）
                                    Box(
                                        modifier = Modifier
                                            .height(56.dp)
                                            .weight(1f)
                                            .background(
                                                if (pressingPhone) Color(0xFF1565C0) else Color(
                                                    0xFF1E88E5
                                                ),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        pressingPhone = true
                                                        vm.startPhoneRecording()
                                                        try {
                                                            // 等待手指松开，手指按住时间过长会自动停止录音
                                                            tryAwaitRelease()
                                                        } finally {
                                                            pressingPhone = false
                                                            vm.stopPhoneRecordingAndSend()
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("手机录音（长按）", color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        showRecordPicker = false
                                    }) { Text("关闭") }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 文字输入区域
        if (showInput) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("输入消息...") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            vm.sendMessage(input)
                            input = ""
                        }
                    }
                ) {
                    Text("发送")
                }
            }
        }

        // 底部控制区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 主要操作按钮（三枚大圆形按钮）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 关闭预览（浅灰大圆）
                    IconButton(
                        onClick = {
                            vm.clearBlePhotoPreview()
                            isLoopingPhoto = false
                            vm.stopCameraLoop()
                            vm.setCameraEnabled(false)
                        },
                        enabled = (ui.blePhotoPreview != null) || ui.cameraOn,
                        modifier = Modifier
                            .size(120.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭预览",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("关闭预览", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // 蓝牙拍照（浅灰大圆）
                    IconButton(
                        onClick = {
                            android.util.Log.d("ChatScreen", "🔘 蓝牙拍照按钮被点击")
                            vm.setCameraEnabled(false)
                            vm.takeBlePhoto()
                        },
                        enabled = ui.bleConnectionState.isConnected,
                        modifier = Modifier
                            .size(120.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "蓝牙拍照",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("蓝牙拍照", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // 手机开始/停止（未激活浅灰；激活主色）
                    IconButton(
                        onClick = {
                            if (!isLoopingPhoto) {
                                android.util.Log.d("ChatScreen", "📘 开始循环拍照上传")
                                isLoopingPhoto = true
                                vm.setCameraEnabled(true)
                                vm.startCameraLoop()
                            } else {
                                android.util.Log.d("ChatScreen", "📘 停止循环拍照上传")
                                isLoopingPhoto = false
                                vm.setCameraEnabled(false)
                                vm.stopCameraLoop()
                            }
                        },
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                if (isLoopingPhoto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (isLoopingPhoto) Icons.Filled.Stop else Icons.Filled.Camera,
                                contentDescription = if (isLoopingPhoto) "停止拍照" else "开始拍照",
                                tint = if (isLoopingPhoto) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (isLoopingPhoto) "停止拍照" else "开始拍照",
                                color = if (isLoopingPhoto) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 快捷操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 蓝牙扫描按钮
                    IconButton(
                        onClick = {
                            android.util.Log.d("ChatScreen", "🔘 蓝牙扫描按钮被点击")
                            vm.scanBleDevices()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Blue, CircleShape)
                    ) {
                        Icon(Icons.Filled.BluetoothSearching, "蓝牙扫描", tint = Color.White)
                    }

                    // 文字输入切换按钮
                    IconButton(
                        onClick = {
                            showInput = !showInput
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Green, CircleShape)
                    ) {
                        Icon(Icons.Filled.Chat, "文字输入", tint = Color.White)
                    }

                    // 录音按钮
                    IconButton(
                        onClick = {
                            showRecordPicker = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Filled.Mic, "录音", tint = Color.White)
                    }

                    // 设置按钮
                    IconButton(
                        onClick = {
                            onSettingsClick()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Gray, CircleShape)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 清除消息按钮
                    IconButton(
                        onClick = {
                            vm.clearMessages()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "清除消息",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 朗读开关按钮 - 移到同一行
                    IconButton(
                        onClick = {
                            vm.setTtsEnabled(!ui.ttsOn)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(if (ui.ttsOn) Color.Yellow else Color.Gray, CircleShape)
                    ) {
                        Icon(
                            if (ui.ttsOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (ui.ttsOn) "关闭朗读" else "开启朗读",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // 错误提示
        ui.error?.let { error ->
            Dialog(
                onDismissRequest = { vm.clearError() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "提示",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { vm.clearError() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("我知道了", color = Color.White)
                        }
                    }
                }
            }
        }

        // AUC 直链对话框（暂保留，如需）
        if (showAucDialog) {
            Dialog(
                onDismissRequest = { showAucDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "音频识别",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = aucUrl,
                            onValueChange = { aucUrl = it },
                            label = { Text("请输入音频直链 URL (mp3/wav/ogg)") },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = { showAucDialog = false }) { Text("取消") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (aucUrl.isNotBlank()) {
                                        // AUC 已移除：提示用户改用百度语音识别
                                        android.widget.Toast.makeText(
                                            context,
                                            "已移除AUC流程，请在设置中填写Baidu信息使用语音识别",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        showAucDialog = false
                                        aucUrl = ""
                                    }
                                }
                            ) { Text("识别") }
                        }
                    }
                }
            }
        }
    }
}