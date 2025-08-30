# 蓝牙状态同步修复

## 问题描述
虽然控制面板显示"已连接到设备: OpenGlass"，但主界面仍然显示"等待蓝牙设备连接..."。

## 问题原因
相机预览界面（ModernCameraPreview）没有获取到最新的蓝牙连接状态，导致显示状态不一致。

## 解决方案

### 1. 传递蓝牙连接状态
将蓝牙连接状态传递给相机预览组件：

```kotlin
// 修复前
ModernCameraPreview(
    lifecycleOwner = lifecycleOwner,
    onPreviewViewCreated = { previewView -> vm.bindCamera(lifecycleOwner, previewView) },
    onLongPress = { showCameraControls = true },
    onDoubleTap = { vm.takePhoto() },
    cameraMode = ui.cameraMode
)

// 修复后
ModernCameraPreview(
    lifecycleOwner = lifecycleOwner,
    onPreviewViewCreated = { previewView -> vm.bindCamera(lifecycleOwner, previewView) },
    onLongPress = { showCameraControls = true },
    onDoubleTap = { vm.takePhoto() },
    cameraMode = ui.cameraMode,
    bleConnectionState = ui.bleConnectionState  // 新增蓝牙连接状态
)
```

### 2. 更新相机预览函数签名
添加蓝牙连接状态参数：

```kotlin
@Composable
fun ModernCameraPreview(
    lifecycleOwner: LifecycleOwner,
    onPreviewViewCreated: (PreviewView) -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    cameraMode: String = "手机相机",
    bleConnectionState: BleConnectionState = BleConnectionState()  // 新增参数
) {
    // ...
}
```

### 3. 根据连接状态显示不同内容
在蓝牙相机模式下，根据连接状态显示相应的信息：

```kotlin
if (bleConnectionState.isConnected) {
    // 已连接状态
    Text(
        text = "已连接到: ${bleConnectionState.deviceName ?: "OpenGlass"}",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Green
    )
    Text(
        text = "等待图像数据...",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f)
    )
} else if (bleConnectionState.isScanning) {
    // 扫描中状态
    Text(
        text = "正在扫描蓝牙设备...",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Yellow
    )
} else if (bleConnectionState.error != null) {
    // 错误状态
    Text(
        text = "连接错误: ${bleConnectionState.error}",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Red
    )
} else {
    // 等待连接状态
    Text(
        text = "等待蓝牙设备连接...",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.7f)
    )
}
```

## 修复后的效果

### 1. 状态一致性
- ✅ 控制面板和相机预览显示相同的连接状态
- ✅ 蓝牙连接状态实时同步
- ✅ 不同状态显示不同的颜色和文字

### 2. 用户体验提升
- ✅ 已连接时显示绿色图标和"已连接到: OpenGlass"
- ✅ 扫描中时显示黄色文字"正在扫描蓝牙设备..."
- ✅ 错误时显示红色文字"连接错误: [错误信息]"
- ✅ 等待连接时显示白色文字"等待蓝牙设备连接..."

### 3. 视觉反馈
- 蓝牙图标颜色：
  - 未连接：白色
  - 已连接：绿色
- 状态文字颜色：
  - 已连接：绿色
  - 扫描中：黄色
  - 错误：红色
  - 等待：白色（半透明）

## 状态映射

| 连接状态 | 图标颜色 | 显示文字 | 文字颜色 |
|---------|---------|---------|---------|
| 已连接 | 绿色 | "已连接到: OpenGlass" + "等待图像数据..." | 绿色 + 白色 |
| 扫描中 | 白色 | "正在扫描蓝牙设备..." | 黄色 |
| 错误 | 白色 | "连接错误: [错误信息]" | 红色 |
| 等待连接 | 白色 | "等待蓝牙设备连接..." | 白色（半透明） |

## 测试建议

### 1. 连接状态测试
1. 开启蓝牙功能
2. 观察相机预览界面的状态变化
3. 确认控制面板和预览界面显示一致

### 2. 状态切换测试
1. 断开蓝牙连接
2. 重新连接
3. 观察状态文字和颜色的变化

### 3. 错误处理测试
1. 模拟连接错误
2. 确认错误信息正确显示
3. 测试错误恢复后的状态更新
