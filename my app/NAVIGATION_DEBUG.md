# 导航问题调试指南

## 问题描述
设置页面返回按钮点击后重新打开应用，而不是返回到主页面。

## 可能的原因

### 1. Activity生命周期问题
- Activity可能被系统销毁并重新创建
- 状态没有正确保存和恢复

### 2. 状态管理问题
- `rememberSaveable` 可能没有正确工作
- 状态在Activity重建时丢失

### 3. 导航逻辑问题
- 返回回调可能触发了其他操作
- 状态更新可能导致了意外的重新渲染

## 调试步骤

### 1. 检查日志输出
运行应用并查看以下日志：
```
MainActivity: MainActivity onCreate/LaunchedEffect triggered
MainActivity: Current state - showSettings: false, showBluetoothManager: false, showImageSettings: false
MainActivity: Settings button clicked
MainActivity: Navigation state changed - showSettings: true, showBluetoothManager: false, showImageSettings: false
MainActivity: Showing SettingsScreen
MainActivity: Settings back button clicked
MainActivity: Navigation state changed - showSettings: false, showBluetoothManager: false, showImageSettings: false
MainActivity: Showing ChatScreen
```

### 2. 检查Activity生命周期
- 查看是否有 `MainActivity DisposableEffect - Component disposed` 日志
- 检查是否有 `MainActivity DisposableEffect - Component entered` 日志

### 3. 状态变化监控
- 监控 `showSettings` 状态的变化
- 确认状态变化是否按预期进行

## 解决方案

### 方案1: 使用更明确的状态管理
```kotlin
// 使用 remember 而不是 rememberSaveable
var showSettings by remember { mutableStateOf(false) }
```

### 方案2: 添加状态持久化
```kotlin
// 使用 SharedPreferences 保存状态
val prefs = context.getSharedPreferences("navigation_state", Context.MODE_PRIVATE)
var showSettings by remember { 
    mutableStateOf(prefs.getBoolean("show_settings", false)) 
}
```

### 方案3: 使用 ViewModel 管理状态
```kotlin
// 将导航状态移到 ViewModel 中
class NavigationViewModel : ViewModel() {
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()
    
    fun showSettings() { _showSettings.value = true }
    fun hideSettings() { _showSettings.value = false }
}
```

## 测试步骤

### 1. 基本导航测试
1. 打开应用
2. 点击设置按钮
3. 检查是否进入设置页面
4. 点击返回按钮
5. 检查是否返回主页面

### 2. 生命周期测试
1. 在设置页面时旋转屏幕
2. 检查状态是否正确恢复
3. 点击返回按钮
4. 检查是否正常返回

### 3. 状态持久化测试
1. 在设置页面时切换到其他应用
2. 返回应用
3. 检查是否仍在设置页面
4. 点击返回按钮
5. 检查是否正常返回

## 预期行为

### 正常情况
- 点击设置按钮 → 进入设置页面
- 点击返回按钮 → 返回主页面
- 状态正确保存和恢复

### 异常情况
- 点击返回按钮 → 重新打开应用
- 状态丢失
- Activity重新创建

## 修复建议

### 1. 立即修复
- 添加更多调试信息
- 检查状态变化
- 确认Activity生命周期

### 2. 长期修复
- 使用 ViewModel 管理导航状态
- 实现更稳定的状态持久化
- 添加错误处理和恢复机制
