# TTS朗读功能实现

## 概述
TTS（Text-to-Speech）朗读功能已完整实现，可将AI回复自动转换为语音播放。

## 功能特点

### 1. 自动朗读
- ✅ AI回复自动朗读
- ✅ 智能过滤空消息
- ✅ 状态同步

### 2. 语音配置
- ✅ 语速调节（默认1.0倍速）
- ✅ 音调调节（默认1.0倍音调）
- ✅ 语言设置（默认系统语言）

### 3. 用户体验
- ✅ 顶部控制栏开关
- ✅ 视觉反馈
- ✅ 状态持久

## 使用方法

### 1. 开启/关闭TTS
1. 在顶部控制栏找到"朗读"开关
2. 点击开关即可控制TTS功能
3. 开关状态立即生效

### 2. 自动朗读
1. 开启TTS后发送消息给AI
2. AI回复时自动朗读
3. 完全自动化，无需额外操作

## 技术实现

### 1. TtsManager核心类
```kotlin
class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    fun speak(text: String)  // 播放文本
    fun configure(rate: Float, pitch: Float, locale: Locale)  // 配置参数
    fun shutdown()  // 关闭TTS
}
```

### 2. ChatViewModel集成
```kotlin
// TTS开关控制
fun setTtsEnabled(enabled: Boolean)

// AI回复时自动朗读
if (replyText.isNotBlank() && _ui.value.ttsOn) tts.speak(replyText)
```

### 3. UI界面
- 顶部控制栏提供TTS开关
- VolumeUp图标表示朗读功能
- 开关状态通过图标颜色变化显示

## 工作流程

1. **消息发送**: 用户发送消息 → AI处理 → 生成回复
2. **TTS检查**: 检查TTS开关状态
3. **自动朗读**: 如果开启则朗读AI回复

## 总结

TTS功能已完整实现，用户可通过顶部控制栏开关控制，AI回复时自动朗读，提供完整的语音交互体验。
