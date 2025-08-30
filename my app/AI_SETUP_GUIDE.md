# 🤖 AI功能设置指南

## 概述
您的Android应用现在支持OpenAI的AI功能，包括：
- 文本对话 (GPT-4o)
- 图片分析 (Vision)
- 音频转写 (Whisper)

## 🚀 快速开始

### 1. 获取OpenAI API Key
1. 访问 [OpenAI官网](https://platform.openai.com/)
2. 注册/登录账户
3. 在API Keys页面创建新的API Key
4. 复制API Key

### 2. 在应用中设置
1. 启动应用
2. 点击设置按钮（齿轮图标）
3. 输入以下信息：
   - **Base URL**: `https://api.openai.com/v1/`
   - **API Key**: 您刚才获取的API Key
   - **Model**: `gpt-4o` (推荐)

### 3. 测试AI功能
1. 在设置中点击"AI测试"按钮
2. 输入测试文本（如："Hello, how are you?"）
3. 点击"发送到AI"按钮
4. 查看AI响应

## 🔧 故障排除

### 常见问题

#### ❌ "请先设置OpenAI API Key"
**解决方案**: 在设置中正确配置API Key

#### ❌ 网络连接错误
**解决方案**: 
- 检查网络连接
- 确认API Key有效
- 检查防火墙设置

#### ❌ API配额超限
**解决方案**: 
- 检查OpenAI账户余额
- 升级到付费计划

### 调试信息
应用会在Logcat中输出详细的调试信息，包括：
- API请求内容
- 响应状态
- 错误详情

## 📱 功能特性

### 文本对话
- 支持中英文
- 智能上下文理解
- 快速响应

### 图片分析
- 支持JPEG格式
- 自动识别内容
- 多模态理解

### 音频转写
- 支持PCM格式
- 高精度转写
- 多语言支持

## 💡 使用建议

1. **API Key安全**: 不要在代码中硬编码API Key
2. **请求频率**: 避免过于频繁的API调用
3. **内容质量**: 提供清晰、具体的提示词
4. **错误处理**: 应用会自动处理网络错误和API错误

## 🔗 相关链接

- [OpenAI API文档](https://platform.openai.com/docs)
- [GPT-4o模型说明](https://platform.openai.com/docs/models/gpt-4o)
- [Whisper音频转写](https://platform.openai.com/docs/models/whisper)

## 📞 技术支持

如果遇到问题，请检查：
1. Logcat日志输出
2. 网络连接状态
3. API Key有效性
4. 应用权限设置
