# AI智能眼镜聊天应用

一个基于Android的智能AI聊天应用，支持与OpenGlass智能眼镜设备进行交互，集成了多种AI服务和语音识别功能。

## 🌟 主要功能

### 🤖 AI对话
- **多模态AI支持**: 支持文本、图片、音频输入
- **豆包API集成**: 默认使用豆包(Doubao) AI服务
- **智能对话**: 支持上下文对话，记忆对话历史
- **TTS语音合成**: 支持AI回复的语音播放

### 📷 相机功能
- **实时相机预览**: 集成CameraX，支持实时预览
- **拍照上传**: 支持拍照并上传给AI分析
- **蓝牙设备拍照**: 支持通过OpenGlass设备远程拍照

### 🎤 语音识别
- **手机录音**: 使用设备麦克风录音
- **设备录音**: 通过OpenGlass设备录音
- **百度语音识别**: 集成百度短语音识别API
- **多录音模式**: 支持不同音频源和采样率

### 📡 蓝牙连接
- **BLE连接**: 支持OpenGlass智能眼镜设备
- **实时数据流**: 接收设备图片和音频数据
- **设备控制**: 远程控制设备拍照和录音

### ⚙️ 设置管理
- **API配置**: 支持自定义AI服务端点
- **语音识别配置**: 百度API密钥管理
- **设备管理**: 蓝牙设备连接状态监控

## 🏗️ 技术架构

### 核心技术栈
- **UI框架**: Jetpack Compose
- **架构模式**: MVVM + Repository
- **异步处理**: Kotlin Coroutines
- **网络请求**: Retrofit + OkHttp
- **图像处理**: CameraX
- **蓝牙通信**: Android BLE API

### 项目结构
```
app/src/main/java/com/example/myapp/
├── MainActivity.kt                 # 主活动
├── ChatViewModel.kt               # 聊天视图模型
├── data/                          # 数据层
│   ├── network/                   # 网络相关
│   │   └── AiApi.kt              # AI API接口
│   └── repository/                # 数据仓库
│       └── AiRepository.kt       # AI服务仓库
├── input/                         # 输入模块
│   ├── audio/                     # 音频输入
│   ├── ble/                       # 蓝牙输入
│   └── camera/                    # 相机输入
├── output/                        # 输出模块
│   └── tts/                       # 语音合成
├── ui/                           # UI组件
│   ├── chat/                      # 聊天界面
│   └── settings/                  # 设置界面
├── settings/                      # 设置管理
└── utils/                         # 工具类
```

## 📱 界面功能

### 主聊天界面
- **相机预览区域**: 实时显示相机画面
- **消息列表**: 显示对话历史
- **输入区域**: 文本输入框
- **控制按钮**: 
  - 📷 拍照上传
  - 🎤 手机录音
  - 📱 设备录音
  - ⚙️ 设置

### 设置界面
- **AI服务配置**: API密钥、端点URL、模型选择
- **语音识别配置**: 百度API密钥设置
- **设备管理**: 蓝牙连接状态
- **功能开关**: 各种功能的启用/禁用

## 🔧 安装和配置

### 系统要求
- Android 7.0+ (API 24+)
- 支持BLE的Android设备
- 网络连接

### 权限要求
```xml
<!-- 相机权限 -->
<uses-permission android:name="android.permission.CAMERA" />
<!-- 录音权限 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<!-- 蓝牙权限 -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />
```

### 构建步骤
1. 克隆项目
```bash
git clone <repository-url>
cd my-app
```

2. 配置API密钥
   - 在设置界面配置豆包API密钥
   - 配置百度语音识别API密钥（可选）

3. 构建应用
```bash
./gradlew assembleDebug
```

## 🚀 使用方法

### 基本使用
1. **启动应用**: 打开应用，授予必要权限
2. **配置API**: 进入设置，配置AI服务API密钥
3. **开始对话**: 使用文本输入或语音输入与AI对话
4. **拍照分析**: 点击拍照按钮，AI会分析图片内容

### 语音识别使用
1. **手机录音**: 长按录音按钮进行手机录音
2. **设备录音**: 连接OpenGlass设备后，长按设备录音按钮
3. **语音转文字**: 录音完成后自动转换为文字并发送给AI

### 蓝牙设备连接
1. **打开蓝牙**: 确保设备蓝牙已开启
2. **连接设备**: 在设置中管理蓝牙设备连接
3. **设备控制**: 连接成功后可使用设备拍照和录音功能

## 🔌 API集成

### 豆包API配置
```kotlin
// 默认配置
baseUrl = "https://ark.cn-beijing.volces.com/api/v3/"
model = "gpt-4o"
```

### 百度语音识别API
- 支持短语音识别
- 可配置DevPid参数
- 支持语音识别结果转发到豆包API

## 🐛 故障排除

### 常见问题
1. **API密钥无效**: 检查设置中的API密钥配置
2. **蓝牙连接失败**: 确保设备支持BLE且已开启
3. **录音为空**: 检查录音权限和音频源配置
4. **图片上传失败**: 检查网络连接和API配置

### 调试模式
应用包含详细的日志输出，可通过Logcat查看：
- `[AUDIO_REC]`: 音频录制相关日志
- `[BLE]`: 蓝牙连接相关日志
- `[AI_API]`: AI API调用相关日志

## 📄 许可证

本项目采用 [MIT License](LICENSE) 许可证。

## 🤝 贡献

欢迎提交Issue和Pull Request来改进这个项目。

## 📞 支持

如有问题或建议，请通过以下方式联系：
- 提交GitHub Issue
- 发送邮件至项目维护者

---

**注意**: 使用本应用需要配置相应的API密钥，请确保遵守相关服务的使用条款和隐私政策。
