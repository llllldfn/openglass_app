package com.example.myapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.myapp.agent.AiAgent
import com.example.myapp.data.network.AiApi
// Groq 已移除
import com.example.myapp.data.repository.AiRepository
import com.example.myapp.settings.Settings
import com.example.myapp.utils.ImageUtils
import com.example.myapp.utils.RetryUtils
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.myapp.model.text

class AdvancedAiTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdvancedAiTestScreen()
        }
    }
}

@Composable
fun AdvancedAiTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var inputText by remember { mutableStateOf("Hello, how are you?") }
    var questionText by remember { mutableStateOf("What do you see in the images?") }
    var responseText by remember { mutableStateOf("AI响应将显示在这里...") }
    var isLoading by remember { mutableStateOf(false) }
    
    val settings = Settings.read(context)
    val aiAgent = remember { AiAgent(context) }
    val agentState by aiAgent.state.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "高级AI功能测试",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // 设置信息卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前设置:", style = MaterialTheme.typography.titleMedium)
                Text("Ark/OpenAI API: ${if (settings.apiKey.isNullOrBlank()) "未设置" else "已设置"}")
                // Groq API 状态已移除
                Text("重试机制: ${if (settings.enableRetry) "启用" else "禁用"}")
                Text("图像处理: ${if (settings.imageProcessingEnabled) "启用" else "禁用"}")
            }
        }
        
        // Ark(OpenAI兼容)测试
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("豆包(Ark)测试", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("输入测试文本") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (inputText.isBlank()) return@Button
                        scope.launch {
                            testOpenAI(inputText, context) { response ->
                                responseText = response
                            }
                        }
                    },
                    enabled = !isLoading && !settings.apiKey.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("测试豆包")
                }
            }
        }
        
        // 仅豆包，移除 Groq 测试卡片
        
        // AI代理测试
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI代理测试", style = MaterialTheme.typography.titleMedium)
                Text("处理图像: ${aiAgent.getProcessedImageCount()}")
                agentState.lastImageDescription?.let { desc ->
                    Text("最后描述: $desc", style = MaterialTheme.typography.bodySmall)
                }
                agentState.lastAnswer?.let { answer ->
                    Text("最后回答: $answer", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                testImageProcessing(aiAgent) { response ->
                                    responseText = response
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("测试图像处理")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                testAgentQuestion(aiAgent, questionText) { response ->
                                    responseText = response
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("测试代理问答")
                    }
                }
            }
        }
        
        // 响应显示
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = responseText,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // 状态指示器
        if (agentState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        
        // 错误显示
        agentState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = "错误: $error",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

private suspend fun testOpenAI(
    text: String,
    context: android.content.Context,
    onResponse: (String) -> Unit
) {
    try {
        onResponse("正在测试豆包...")
        val settings = Settings.read(context)
        val api = AiApi.create(context = context, baseUrl = settings.baseUrl)
        val repository = AiRepository(api)
        
        val result = repository.sendToAi(
            com.example.myapp.data.repository.InputData.Text(text),
            context
        )
        
        result.observeForever { state ->
            when (state) {
                is com.example.myapp.data.repository.LoadState.Success -> {
                    onResponse("✅ 豆包响应: ${state.data.text}")
                }
                is com.example.myapp.data.repository.LoadState.Error -> {
                    onResponse("❌ 豆包错误: ${state.throwable.message}")
                }
                is com.example.myapp.data.repository.LoadState.Loading -> {
                    onResponse("⏳ 豆包处理中...")
                }
            }
        }
    } catch (e: Exception) {
        onResponse("💥 豆包异常: ${e.message}")
    }
}

private suspend fun testImageProcessing(
    agent: AiAgent,
    onResponse: (String) -> Unit
) {
    try {
        onResponse("正在测试图像处理...")
        
        // 创建一个模拟图像（实际应用中应该是真实的图像数据）
        val mockImage = ByteArray(100) { it.toByte() }
        
        agent.addImage(mockImage)
        onResponse("✅ 图像处理完成")
    } catch (e: Exception) {
        onResponse("💥 图像处理异常: ${e.message}")
    }
}

private suspend fun testAgentQuestion(
    agent: AiAgent,
    question: String,
    onResponse: (String) -> Unit
) {
    try {
        onResponse("正在测试AI代理问答...")
        agent.answerQuestion(question)
        onResponse("✅ AI代理问答完成")
    } catch (e: Exception) {
        onResponse("💥 AI代理问答异常: ${e.message}")
    }
}
