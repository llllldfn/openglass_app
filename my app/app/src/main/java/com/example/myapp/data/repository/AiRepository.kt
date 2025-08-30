package com.example.myapp.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapp.data.network.AiApi
import com.example.myapp.model.AiResponse
import com.example.myapp.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import java.io.File
import retrofit2.HttpException
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class LoadState<out T> {
	data object Loading : LoadState<Nothing>()
	data class Success<T>(val data: T) : LoadState<T>()
	data class Error(val throwable: Throwable) : LoadState<Nothing>()
}

sealed class InputData {
	data class Text(val text: String) : InputData()
	data class Audio(val pcm: ByteArray) : InputData()
	data class Image(val jpeg: ByteArray, val text: String? = null, val audio: ByteArray? = null) : InputData()
}

class AiRepository(private val api: AiApi, private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
	private val _state = MutableLiveData<LoadState<AiResponse>>()
	val state: LiveData<LoadState<AiResponse>> = _state

	fun sendToAi(input: InputData, context: Context): LiveData<LoadState<AiResponse>> {
		_state.postValue(LoadState.Loading)
		scope.launch {
			runCatching {
				val settings = Settings.read(context)
				println("🔍 AI设置: baseUrl=${settings.baseUrl}, model=${settings.model}, apiKey=${if (settings.apiKey.isNullOrBlank()) "未设置" else "已设置"}")
				
				when (input) {
					is InputData.Text -> {
						println("📝 发送文本: ${input.text}")
						sendText(input.text, settings.model)
					}
					is InputData.Audio -> {
						println("[AUDIO_UPLOAD] 🎵 发送音频: ${input.pcm.size} bytes")
						sendAudio(input.pcm, context, settings.model)
					}
					is InputData.Image -> {
						println("🖼️ 发送图片: ${input.jpeg.size} bytes")
						// 图片处理时不发送音频数据，避免多模态请求问题
						sendImage(input.jpeg, input.text, null, context, settings.model)
					}
				}
			}.onSuccess { resp ->
				println("✅ AI响应成功: ${resp.choices?.size ?: 0} 个选择")
				_state.postValue(LoadState.Success(resp))
			}.onFailure { t ->
				println("❌ AI请求失败: ${t.message}")
				if (t is HttpException) {
					val code = t.code()
					val err = t.response()?.errorBody()?.string()
					println("🌐 HTTP状态: $code")
					println("🌐 错误详情: $err")
					
					// 针对HTTP 400提供更具体的错误信息
					val detailedError = when (code) {
						400 -> {
							val errorMsg = err ?: "Bad Request"
							when {
								errorMsg.contains("api_key", ignoreCase = true) -> "API Key无效或未设置"
								errorMsg.contains("model", ignoreCase = true) -> "模型不存在或不支持图片处理，请检查模型名称"
								errorMsg.contains("content", ignoreCase = true) -> "请求内容格式错误"
								errorMsg.contains("image", ignoreCase = true) -> "图片格式或大小不支持，请尝试压缩图片"
								errorMsg.contains("base64", ignoreCase = true) -> "图片Base64编码错误"
								errorMsg.contains("vision", ignoreCase = true) -> "当前模型不支持视觉处理，请使用支持图片的模型"
								else -> "请求格式错误: $errorMsg"
							}
						}
						401 -> "API Key无效或未授权"
						403 -> "API访问被拒绝，请检查权限"
						429 -> "请求频率过高，请稍后重试"
						500 -> "AI服务内部错误"
						else -> "HTTP $code: ${err ?: t.message}"
					}
					
					_state.postValue(LoadState.Error(Exception(detailedError, t)))
				} else {
					t.printStackTrace()
					_state.postValue(LoadState.Error(t))
				}
			}
		}
		return state
	}

	private suspend fun sendText(text: String, model: String): AiResponse {
		// 清理模型名称，移除换行符和多余空格
		val cleanModel = model.trim().replace("\n", "").replace("\r", "")
		println("📝 清理后的模型名称: '$cleanModel'")
		
		// 使用 JSON 对象构造，避免字符串拼接错误
		val content = JSONArray().put(
			JSONObject().put("type", "text").put("text", text)
		)
		val messages = JSONArray().put(
			JSONObject().put("role", "user").put("content", content)
		)
		val root = JSONObject()
			.put("model", cleanModel)  // 使用清理后的模型名称
			.put("messages", messages)
			.put("max_tokens", 1000)
			.put("temperature", 0.7)
		val json = root.toString()
		println("📤 文本请求JSON: $json")
		val requestBody = json.toRequestBody("application/json".toMediaType())
		return api.sendText(requestBody)
	}

	private suspend fun sendAudio(audioBytes: ByteArray, context: Context, model: String): AiResponse {
		// 清理模型名称，移除换行符和多余空格
		val cleanModel = model.trim().replace("\n", "").replace("\r", "")
		println("[AUDIO_UPLOAD] 🎵 清理后的模型名称: '$cleanModel'")
		
		val audioBase64 = Base64.getEncoder().encodeToString(audioBytes)

		fun req1(): RequestBody {
			// OpenAI/Ark 常见写法: type=input_audio + input_audio 对象
			val json = org.json.JSONObject()
				.put("model", cleanModel)
				.put(
					"messages",
					org.json.JSONArray().put(
						org.json.JSONObject().put(
							"role",
							"user"
						).put(
							"content",
							org.json.JSONArray()
								.put(org.json.JSONObject().put("type", "input_audio").put("input_audio", org.json.JSONObject().put("data", audioBase64).put("format", "wav")))
								.put(org.json.JSONObject().put("type", "text").put("text", "请将音频内容转写为文本"))
						)
					)
				)
				.put("max_tokens", 1000)
				.put("temperature", 0.2)
				.toString()
			println("[AUDIO_UPLOAD] ▶️ JSON#1: $json")
			return json.toRequestBody("application/json".toMediaType())
		}

		fun req2(): RequestBody {
			// 变体: type=input_audio + audio 对象
			val json = org.json.JSONObject()
				.put("model", cleanModel)
				.put(
					"messages",
					org.json.JSONArray().put(
						org.json.JSONObject().put(
							"role",
							"user"
						).put(
							"content",
							org.json.JSONArray()
								.put(org.json.JSONObject().put("type", "input_audio").put("audio", org.json.JSONObject().put("data", audioBase64).put("format", "wav")))
								.put(org.json.JSONObject().put("type", "text").put("text", "请将音频内容转写为文本"))
						)
					)
				)
				.put("max_tokens", 1000)
				.put("temperature", 0.2)
				.toString()
			println("[AUDIO_UPLOAD] ▶️ JSON#2: $json")
			return json.toRequestBody("application/json".toMediaType())
		}

		fun req3(): RequestBody {
			// 变体: Ark 某些示例: type=audio + audio 对象
			val json = org.json.JSONObject()
				.put("model", cleanModel)
				.put(
					"messages",
					org.json.JSONArray().put(
						org.json.JSONObject().put(
							"role",
							"user"
						).put(
							"content",
							org.json.JSONArray()
								.put(org.json.JSONObject().put("type", "audio").put("audio", org.json.JSONObject().put("data", audioBase64).put("format", "wav")))
						)
					)
				)
				.put("max_tokens", 1000)
				.put("temperature", 0.2)
				.toString()
			println("[AUDIO_UPLOAD] ▶️ JSON#3: $json")
			return json.toRequestBody("application/json".toMediaType())
		}

		println("[AUDIO_UPLOAD] 🎵 发送音频转写请求(Ark via chat/completions)")
		try {
			return api.sendText(req1())
		} catch (e1: HttpException) {
			if (e1.code() == 400) {
				println("[AUDIO_UPLOAD] ❗ JSON#1 返回400，尝试 JSON#2 兼容体")
				try {
					return api.sendText(req2())
				} catch (e2: HttpException) {
					if (e2.code() == 400) {
						println("[AUDIO_UPLOAD] ❗ JSON#2 仍400，尝试 JSON#3 兼容体(type=audio)")
						return api.sendText(req3())
					} else throw e2
				}
			} else throw e1
		}
	}

	// AUC 提交/轮询逻辑已移除（改用 Baidu 短语音识别）

	// ===== Baidu Short Speech Recognition =====
	suspend fun baiduShortSpeech(
		wav: ByteArray,
		apiKey: String,
		secretKey: String,
		devPid: Int,
		sampleRate: Int = 16000
	): Result<String> = runCatching {
		val client = OkHttpClient()
		// 1) get access_token
		val tokenUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=" +
			java.net.URLEncoder.encode(apiKey, "UTF-8") +
			"&client_secret=" + java.net.URLEncoder.encode(secretKey, "UTF-8")
		Log.i("[ASR_BAIDU]", "🔑 获取 access_token ...")
		val tokenReq = Request.Builder().url(tokenUrl).post("".toRequestBody("application/x-www-form-urlencoded".toMediaType())).build()
		val tokenResp = client.newCall(tokenReq).execute()
		if (!tokenResp.isSuccessful) {
			val code = tokenResp.code
			val msg = tokenResp.body?.string()
			throw IllegalStateException(
				if (code == 401) "Baidu token 获取失败(401)。请检查 Baidu API Key/Secret Key 是否正确，且已开通语音识别。"
				else "Baidu token http $code: ${msg ?: ""}"
			)
		}
		val tokenJson = tokenResp.body?.string() ?: throw IllegalStateException("Empty token body")
		val accessToken = org.json.JSONObject(tokenJson).getString("access_token")
		Log.i("[ASR_BAIDU]", "🔑 access_token 获取成功")

		// 2) speech
		val speechB64 = java.util.Base64.getEncoder().encodeToString(wav)
		val bodyJson = org.json.JSONObject()
			.put("format", "wav")
			.put("rate", sampleRate)
			.put("channel", 1)
			.put("cuid", "android-${System.currentTimeMillis()}")
			.put("token", accessToken)
			.put("dev_pid", devPid)
			.put("len", wav.size)
			.put("speech", speechB64)
			.toString()
		val reqBody = bodyJson.toRequestBody("application/json".toMediaType())
		val req = Request.Builder()
			.url("http://vop.baidu.com/server_api")
			.addHeader("Content-Type", "application/json")
			.post(reqBody)
			.build()
		Log.i("[ASR_BAIDU]", "🗣️ 调用识别 devPid=$devPid bytes=${wav.size}")
		val resp = client.newCall(req).execute()
		if (!resp.isSuccessful) throw IllegalStateException("Baidu ASR http ${resp.code}")
		val respStr = resp.body?.string() ?: throw IllegalStateException("Empty ASR body")
		val obj = org.json.JSONObject(respStr)
		val errNo = obj.optInt("err_no", 0)
		if (errNo != 0) throw IllegalStateException("Baidu ASR err_no=$errNo msg=${obj.optString("err_msg")}")
		val text = obj.optJSONArray("result")?.optString(0) ?: ""
		Log.i("[ASR_BAIDU]", "✅ 识别成功 textLen=${text.length}")
		text
	}

	/**
	 * 完全模仿 OpenGlass Web 版本：直接使用原始图片数据，不做任何处理
	 * 这是 Web 版本成功的核心秘密！
	 */
	private suspend fun sendImage(imageBytes: ByteArray, text: String?, audioBytes: ByteArray?, context: Context, model: String): AiResponse {
		val settings = Settings.read(context)
		val cleanModel = model.trim()
		val baseUrl = settings.baseUrl.lowercase()
		
		println("🎯 OpenGlass Web 策略：直接使用原始图片数据")
		println("🖼️ 图片大小: ${imageBytes.size} bytes")
		println("🖼️ 使用模型: '$cleanModel'")
		
		// 直接编码原始数据，带前缀
		val imageBase64 = Base64.getEncoder().encodeToString(imageBytes)
		val imageDataUrl = "data:image/jpeg;base64,$imageBase64"
		println("   - 原始图片Base64大小: ${imageBase64.length} characters")
		
		// 优先用 web 端 vision 专用接口
		if (baseUrl.contains("openai.com") && (cleanModel.contains("vision") || cleanModel.contains("gpt-4"))) {
			println("🌐 使用 /v1/images/descriptions vision 专用接口")
			val json = org.json.JSONObject().put("image", imageDataUrl).toString()
			println("【AI请求体】$json")
			println("【AI请求头】Content-Type: application/json")
			println("【AI请求头】Authorization: Bearer "+settings.apiKey?.take(8)+"... (已隐藏)")
			Log.d("AI请求体", json)
			Log.d("AI请求头", "Content-Type: application/json")
			Log.d("AI请求头", "Authorization: Bearer "+(settings.apiKey?.take(8) ?: "")+"... (已隐藏)")
			val requestBody = json.toRequestBody("application/json".toMediaType())
			return api.sendImageDescription(requestBody) // 你需要在 AiApi.kt 里加对应接口
		}
		// 其他情况 fallback 到 chat/completions
		return sendImageWithModel(imageBytes, text, cleanModel, imageBase64, context)
	}
	
	private suspend fun sendImageWithModel(imageBytes: ByteArray, text: String?, model: String, imageBase64: String, context: Context): AiResponse {
		val settings = Settings.read(context)
		val baseUrl = settings.baseUrl.lowercase()
		
		if (baseUrl.contains("ark.cn-beijing.volces.com") || baseUrl.contains("doubao")) {
			println("🔄 使用豆包API特殊格式处理图片")
			return sendImageWithDoubaoFormat(imageBytes, text, model, imageBase64, context)
		}
		
		// 🎯 使用标准的OpenAI图片处理方式，不进行任何图片处理
		println("✅ 使用标准OpenAI图片处理方式（OpenGlass策略：不处理图片）")
		val content = org.json.JSONArray()
			.put(org.json.JSONObject().put("type", "image_url").put("image_url", org.json.JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")))
		
		// 添加默认的文本提示
		val promptText = text ?: "请描述这张图片中的内容"
		content.put(org.json.JSONObject().put("type", "text").put("text", promptText))
		
		val messages = org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("content", content))
		val root = org.json.JSONObject()
			.put("model", model)
			.put("messages", messages)
			.put("max_tokens", 1000)
			.put("temperature", 0.7)
		
		val json = root.toString()
		println("【AI请求体】$json")
		println("【AI请求头】Content-Type: application/json")
		println("【AI请求头】Authorization: Bearer ${settings.apiKey?.take(8)}... (已隐藏)")
		val requestBody = json.toRequestBody("application/json".toMediaType())
		return api.sendText(requestBody)
	}
	
	/**
	 * 豆包API专用图片处理格式 - 严格按照官方文档
	 */
	private suspend fun sendImageWithDoubaoFormat(imageBytes: ByteArray, text: String?, model: String, imageBase64: String, context: Context): AiResponse {
		println("🔄 使用豆包API官方格式处理图片")
		
		try {
			// 方案1：尝试使用Base64格式（与OpenAI兼容）
			println("   - 尝试方案1: Base64格式")
			try {
				val content1 = org.json.JSONArray()
				
				// 图片部分 - Base64格式
				val imageUrl1 = org.json.JSONObject()
					.put("url", "data:image/jpeg;base64,$imageBase64")
				content1.put(org.json.JSONObject()
					.put("type", "image_url")
					.put("image_url", imageUrl1))
				
				// 文本部分
				val promptText = text ?: "请描述这张图片中的内容"
				content1.put(org.json.JSONObject()
					.put("type", "text")
					.put("text", promptText))
				
				val messages1 = org.json.JSONArray().put(
					org.json.JSONObject()
						.put("role", "user")
						.put("content", content1)
				)
				
				val root1 = org.json.JSONObject()
					.put("model", model)
					.put("messages", messages1)
					.put("max_tokens", 1000)
					.put("temperature", 0.7)
				
				val json1 = root1.toString()
				println("🖼️ 豆包API图片请求JSON长度: ${json1.length} characters")
				
				val requestBody1 = json1.toRequestBody("application/json".toMediaType())
				return api.sendText(requestBody1)
				
			} catch (e: Exception) {
				println("   ❌ Base64格式失败: ${e.message}")
			}
			
			// 方案2：降级为纯文本请求（避免完全失败）
			println("   - 尝试方案2: 降级为文本请求")
			val fallbackText = text ?: "我刚刚拍摄了一张图片，但由于API限制无法直接处理。请告诉我如何更好地描述图片内容以获得帮助。"
			
			val content2 = org.json.JSONArray().put(
				org.json.JSONObject().put("type", "text").put("text", fallbackText)
			)
			
			val messages2 = org.json.JSONArray().put(
				org.json.JSONObject()
					.put("role", "user")
					.put("content", content2)
			)
			
			val root2 = org.json.JSONObject()
				.put("model", model)
				.put("messages", messages2)
				.put("max_tokens", 1000)
				.put("temperature", 0.7)
			
			val json2 = root2.toString()
			println("   - 降级文本请求: $json2")
			
			val requestBody2 = json2.toRequestBody("application/json".toMediaType())
			return api.sendText(requestBody2)
			
		} catch (e: Exception) {
			println("❌ 豆包API图片处理完全失败: ${e.message}")
			throw Exception("豆包API不支持当前图片格式，建议使用支持图片处理的API")
		}
	}

	// 保留原有的 multipart 方法作为备用
	@Suppress("BlockingMethodInNonBlockingContext")
	suspend fun send(
		text: String?,
		imageBytes: ByteArray?,
		audioBytes: ByteArray?,
		context: Context
	): Result<AiResponse> = runCatching {
		withContext(Dispatchers.IO) {
			val textPart = AiApi.textPart(text)
			val imagePart: okhttp3.MultipartBody.Part? = imageBytes?.let { bytes ->
				val tmp = File.createTempFile("img", ".jpg", context.cacheDir)
				tmp.writeBytes(bytes)
				AiApi.filePart("image", tmp.name, tmp)
			}
			val audioPart: okhttp3.MultipartBody.Part? = audioBytes?.let { bytes ->
				val tmp = File.createTempFile("audio", ".pcm", context.cacheDir)
				tmp.writeBytes(bytes)
				AiApi.filePart("audio", tmp.name, tmp)
			}
			api.sendMultimodal(textPart, imagePart, audioPart)
		}
	}
}

private fun escapeJson(input: String): String = input
	.replace("\\", "\\\\")
	.replace("\"", "\\\"")
	.replace("\n", "\\n")
	.replace("\r", "\\r")
	.replace("\t", "\\t")

