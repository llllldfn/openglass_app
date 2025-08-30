package com.example.myapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapp.data.network.AiApi
import com.example.myapp.data.repository.AiRepository
import com.example.myapp.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.myapp.model.text

class TestAiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestAiScreen()
        }
    }
}

@Composable
fun TestAiScreen() {
    var inputText by remember { mutableStateOf("Hello, how are you?") }
    var responseText by remember { mutableStateOf("AI响应将显示在这里...") }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AI功能测试 (OpenAI)",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // 显示当前设置
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "当前设置:",
                    style = MaterialTheme.typography.titleMedium
                )
                val settings = Settings.read(context)
                Text("API: ${settings.baseUrl}")
                Text("模型: ${settings.model}")
                Text("API Key: ${if (settings.apiKey.isNullOrBlank()) "未设置" else "已设置"}")
            }
        }
        
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("输入测试文本") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Button(
            onClick = {
                if (inputText.isBlank()) return@Button
                isLoading = true
                scope.launch { 
                    sendToAi(inputText, context) { response -> 
                        responseText = response
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "发送中..." else "发送到AI")
        }
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = responseText,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private suspend fun sendToAi(
	text: String,
	context: android.content.Context,
	onResponse: (String) -> Unit
) {
	try {
		onResponse("正在发送请求...")
		
		val settings = Settings.read(context)
		Log.d("TestAi", "设置: $settings")
		
		// 检查API Key
		if (settings.apiKey.isNullOrBlank()) {
			onResponse("❌ 错误: 请先设置OpenAI API Key")
			return
		}
		
		val api = AiApi.create(context = context, baseUrl = settings.baseUrl)
		val repository = AiRepository(api)
		
		val result = withContext(Dispatchers.IO) {
			repository.sendToAi(
				com.example.myapp.data.repository.InputData.Text(text),
				context
			)
		}
		
		result.observeForever { state ->
			when (state) {
				is com.example.myapp.data.repository.LoadState.Success -> {
					val response = state.data.text
					onResponse("✅ AI响应: $response")
					Log.d("TestAi", "成功: $response")
				}
				is com.example.myapp.data.repository.LoadState.Error -> {
					onResponse("❌ 错误: ${state.throwable.message}")
					Log.e("TestAi", "错误", state.throwable)
				}
				is com.example.myapp.data.repository.LoadState.Loading -> {
					onResponse("⏳ 正在处理...")
				}
			}
		}
	} catch (e: Exception) {
		onResponse("💥 异常: ${e.message}")
		Log.e("TestAi", "异常", e)
	}
}
