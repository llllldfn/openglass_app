package com.example.myapp.agent

import android.content.Context
import android.util.Log
import com.example.myapp.data.network.AiApi
// Removed Groq: use Ark only
import com.example.myapp.data.repository.AiRepository
import com.example.myapp.settings.Settings
import com.example.myapp.utils.AsyncLock
import com.example.myapp.utils.ImageUtils
import com.example.myapp.utils.RetryUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.myapp.model.text

/**
 * AI代理状态
 */
data class AgentState(
    val lastImageDescription: String? = null,
    val lastAnswer: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 智能AI代理系统
 * 借鉴OpenGlass项目的Agent实现
 */
class AiAgent(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val lock = AsyncLock()
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()
    
    private val settings = Settings.read(context)
    private val openaiApi = AiApi.create(context = context, baseUrl = settings.baseUrl)
    
    private val openaiRepository = AiRepository(openaiApi)
    private val processedImages = mutableListOf<ProcessedImage>()
    
    /**
     * 处理过的图像
     */
    data class ProcessedImage(
        val imageBytes: ByteArray,
        val description: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 添加图像进行处理
     */
    suspend fun addImage(imageBytes: ByteArray) = lock.inLock {
        try {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            // 图像预处理
            val processedImage = ImageUtils.preprocessImage(imageBytes, rotation = 0, maxSize = 1024)
            
            // 获取图像描述
            val description = RetryUtils.retryWithCondition(maxAttempts = 3) {
                getImageDescription(processedImage)
            }
            
            // 保存处理结果
            val processed = ProcessedImage(processedImage, description)
            processedImages.add(processed)
            
            // 更新状态
            _state.value = _state.value.copy(
                lastImageDescription = description,
                isLoading = false
            )
            
            Log.d("AiAgent", "图像处理完成: $description")
            
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = "图像处理失败: ${e.message}"
            )
            Log.e("AiAgent", "图像处理失败", e)
        }
    }
    
    /**
     * 回答问题
     */
    suspend fun answerQuestion(question: String) = lock.inLock {
        try {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            if (processedImages.isEmpty()) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "没有可用的图像信息"
                )
                // Early-exit within lock by throwing a controlled exception to satisfy Kotlin's Unit expectation
                throw IllegalStateException("没有可用的图像信息")
            }
            
            // 构建图像描述
            val imageDescriptions = processedImages.joinToString("\n\n") { 
                "Image: ${it.description}" 
            }
            
            // 使用豆包（Ark）回答问题
            val answer = RetryUtils.retryWithCondition(maxAttempts = 3) {
                getAnswerFromArk(question, imageDescriptions)
            }
            
            _state.value = _state.value.copy(
                lastAnswer = answer,
                isLoading = false
            )
            
            Log.d("AiAgent", "问题回答完成: $answer")
            
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = "回答问题失败: ${e.message}"
            )
            Log.e("AiAgent", "回答问题失败", e)
        }
    }
    
    /**
     * 获取图像描述
     */
    private suspend fun getImageDescription(imageBytes: ByteArray): String {
        val result = openaiRepository.sendToAi(
            com.example.myapp.data.repository.InputData.Image(imageBytes),
            context
        )
        
        // 等待结果
        var description = ""
        result.observeForever { state ->
            when (state) {
                is com.example.myapp.data.repository.LoadState.Success -> {
                    description = state.data.text
                }
                is com.example.myapp.data.repository.LoadState.Error -> {
                    throw state.throwable
                }
                is com.example.myapp.data.repository.LoadState.Loading -> {
                    // 等待中
                }
            }
        }
        
        // 等待描述完成
        while (description.isEmpty()) {
            kotlinx.coroutines.delay(100)
        }
        
        return description
    }
    
    /**
     * 从豆包（Ark）获取答案
     */
    private suspend fun getAnswerFromArk(question: String, imageDescriptions: String): String {
        val systemPrompt = """
            You are a smart AI that needs to read through descriptions of images and answer user's questions.
            
            These are the provided images:
            $imageDescriptions
            
            DO NOT mention the images, scenes or descriptions in your answer, just answer the question.
            DO NOT try to generalize or provide possible scenarios.
            ONLY use the information in the description of the images to answer the question.
            BE concise and specific.
        """.trimIndent()
        
        val body = """
            {
                "model": "${settings.model}",
                "messages": [
                    {"role": "system", "content": "$systemPrompt"},
                    {"role": "user", "content": "$question"}
                ],
                "max_tokens": 500,
                "temperature": 0.7
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())
        val response = openaiApi.sendText(body)
        return response.text
    }
    
    /**
     * 清除所有数据
     */
    fun clear() {
        processedImages.clear()
        _state.value = AgentState()
    }
    
    /**
     * 获取处理过的图像数量
     */
    fun getProcessedImageCount(): Int = processedImages.size
    
    /**
     * 获取最近的图像描述
     */
    fun getRecentDescriptions(count: Int = 3): List<String> {
        return processedImages.takeLast(count).map { it.description }
    }
}
