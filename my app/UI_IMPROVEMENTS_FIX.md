# UI改进修复

## 问题描述

1. **音频数据频繁弹出**：一直显示"收到蓝牙音频数据: 20 字节"
2. **手动拍照按钮被AI输出框挡住**：聊天界面覆盖了相机预览区域
3. **时间戳不精确**：只显示"21:30"，没有精确到秒

## 解决方案

### 1. 添加音频数据上传开关

#### 1.1 在ChatUiState中添加音频上传开关
```kotlin
data class ChatUiState(
    // ... 其他字段
    val audioUploadEnabled: Boolean = false  // 新增音频上传开关
)
```

#### 1.2 修改BLE数据处理逻辑
```kotlin
} else {
    // 可能是音频数据
    if (_ui.value.audioUploadEnabled) {
        val message = ChatMessage(
            id = System.currentTimeMillis().toString(),
            role = Role.User,
            text = "收到蓝牙音频数据: ${bytes.size} 字节"
        )
        _ui.value = _ui.value.copy(messages = _ui.value.messages + message)
        
        // 发送音频给AI处理
        try {
            val live = repository.sendToAi(InputData.Audio(bytes), getApplication())
            // ... AI处理逻辑
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(error = "AI处理异常: ${e.message}")
        }
    }
    // 如果音频上传关闭，则不处理音频数据
}
```

#### 1.3 添加控制方法
```kotlin
fun setAudioUploadEnabled(enabled: Boolean) {
    _ui.value = _ui.value.copy(audioUploadEnabled = enabled)
}
```

#### 1.4 在设备控制面板中添加开关
```kotlin
// 音频上传开关
Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
    Icon(
        Icons.Filled.AudioFile,
        contentDescription = "音频上传",
        tint = if (ui.audioUploadEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Switch(
        checked = ui.audioUploadEnabled,
        onCheckedChange = onAudioUploadToggle,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
```

### 2. 修复手动拍照按钮被AI输出框挡住的问题

#### 2.1 限制AI交互区域高度
```kotlin
// AI交互区 - 底部半透明覆盖在相机预览上，但留出空间给拍照按钮
Box(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(16.dp)
) {
    ModernAiInteractionArea(
        // ... 其他参数
        maxHeight = if (ui.cameraMode == "蓝牙相机") 200.dp else null  // 蓝牙相机模式下限制高度
    )
}
```

#### 2.2 更新ModernAiInteractionArea函数签名
```kotlin
@Composable
fun ModernAiInteractionArea(
    // ... 其他参数
    maxHeight: Dp? = null
) {
    // ...
}
```

#### 2.3 应用高度限制
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxHeight ?: 200.dp)
        .shadow(4.dp, RoundedCornerShape(12.dp)),
    // ...
) {
    // ...
}
```

### 3. 修复时间戳精确到秒

#### 3.1 修改时间格式
```kotlin
val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
```

## 修复后的效果

### 1. 音频数据控制
- ✅ **开关控制**：用户可以通过设备控制面板的音频上传开关控制是否处理音频数据
- ✅ **减少干扰**：关闭音频上传后，不再显示"收到蓝牙音频数据"消息
- ✅ **灵活控制**：可以随时开启/关闭音频数据处理

### 2. UI布局优化
- ✅ **拍照按钮可见**：AI交互区域不再覆盖手动拍照按钮
- ✅ **合理布局**：蓝牙相机模式下AI交互区域高度限制为200dp
- ✅ **用户体验**：手动拍照按钮完全可见且可点击

### 3. 时间戳精确度
- ✅ **精确到秒**：时间戳显示格式从"HH:mm"改为"HH:mm:ss"
- ✅ **更详细**：用户可以准确知道消息的发送时间
- ✅ **调试友好**：便于调试和问题追踪

## 使用说明

### 1. 音频上传开关
1. 打开设备控制面板
2. 找到"音频上传"开关
3. 关闭开关：停止处理蓝牙音频数据
4. 开启开关：恢复音频数据处理

### 2. 手动拍照
1. 在蓝牙相机模式下
2. 确保已连接到OpenGlass设备
3. 点击"手动拍照"按钮
4. 按钮现在完全可见且可点击

### 3. 时间戳查看
- 所有消息现在显示精确到秒的时间戳
- 格式：HH:mm:ss（例如：21:30:45）

## 技术细节

### 音频数据处理流程
```
蓝牙音频数据 → 检查audioUploadEnabled → 
├─ true: 显示消息 + 发送给AI处理
└─ false: 忽略数据
```

### UI布局层次
```
相机预览区域
├─ 手动拍照按钮（蓝牙相机模式）
└─ AI交互区域（限制高度）
    ├─ 消息列表
    └─ 输入区域
```

### 时间戳处理
```kotlin
// 时间戳格式：HH:mm:ss
val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
val timeString = dateFormat.format(Date(timestamp))
```

## 测试建议

### 1. 音频上传开关测试
1. 连接OpenGlass设备
2. 关闭音频上传开关
3. 观察是否不再显示音频数据消息
4. 开启音频上传开关
5. 观察是否恢复音频数据处理

### 2. 手动拍照按钮测试
1. 切换到蓝牙相机模式
2. 连接OpenGlass设备
3. 确认手动拍照按钮完全可见
4. 点击按钮测试功能

### 3. 时间戳测试
1. 发送消息
2. 观察时间戳是否显示到秒
3. 验证时间格式为HH:mm:ss

## 预期效果

- ✅ **音频控制**：不再频繁弹出音频数据消息
- ✅ **UI优化**：手动拍照按钮完全可见
- ✅ **时间精确**：时间戳精确到秒
- ✅ **用户体验**：界面更加清晰和易用
