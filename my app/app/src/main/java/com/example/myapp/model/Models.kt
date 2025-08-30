package com.example.myapp.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
enum class Role {
	@Json(name = "user") User,
	@Json(name = "assistant") Assistant,
	@Json(name = "system") System
}

@JsonClass(generateAdapter = true)
data class ChatMessage(
	val id: String,
	val role: Role,
	val text: String,
	val timestampMs: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class AiResponse(
	@Json(name = "choices") val choices: List<Choice>? = null,
	@Json(name = "usage") val usage: Usage? = null
)

@JsonClass(generateAdapter = true)
data class Choice(
	@Json(name = "message") val message: Message,
	@Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class Message(
	@Json(name = "role") val role: String,
	@Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class Usage(
	@Json(name = "prompt_tokens") val promptTokens: Int,
	@Json(name = "completion_tokens") val completionTokens: Int,
	@Json(name = "total_tokens") val totalTokens: Int
)

// 扩展属性，方便获取文本内容
val AiResponse.text: String
	get() = choices?.firstOrNull()?.message?.content ?: ""
