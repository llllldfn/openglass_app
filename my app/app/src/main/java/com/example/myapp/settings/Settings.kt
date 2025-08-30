package com.example.myapp.settings

import android.content.Context

data class SettingsData(
	val baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3/",
	val apiKey: String? = null,
	val endpointId: String? = null,
	val model: String = "gpt-4o",
	val enableRetry: Boolean = true,
	val maxRetryAttempts: Int = 3,
	val imageProcessingEnabled: Boolean = true,
	val maxImageSize: Int = 1024,
	val fallbackImageModel: String = "gpt-4o", // 备用图片处理模型
	val aucAppKey: String = "",
	val aucAccessKey: String = "",
	// Baidu Short Speech
	val baiduApiKey: String = "",
	val baiduSecretKey: String = "",
	val baiduDevPid: Int = 1537
    ,
    val forwardAsrToDoubao: Boolean = false
)

object Settings {
	private const val PREFS = "app_settings"
	private const val KEY_BASE_URL = "base_url"
	private const val KEY_API_KEY = "api_key"
	private const val KEY_ENDPOINT_ID = "endpoint_id"
	private const val KEY_MODEL = "model"
	private const val KEY_ENABLE_RETRY = "enable_retry"
	private const val KEY_MAX_RETRY_ATTEMPTS = "max_retry_attempts"
	private const val KEY_IMAGE_PROCESSING_ENABLED = "image_processing_enabled"
	private const val KEY_MAX_IMAGE_SIZE = "max_image_size"
	private const val KEY_FALLBACK_IMAGE_MODEL = "fallback_image_model"
	private const val KEY_AUC_APP_KEY = "auc_app_key"
	private const val KEY_AUC_ACCESS_KEY = "auc_access_key"
	private const val KEY_BAIDU_API_KEY = "baidu_api_key"
	private const val KEY_BAIDU_SECRET_KEY = "baidu_secret_key"
	private const val KEY_BAIDU_DEV_PID = "baidu_dev_pid"
    private const val KEY_FORWARD_ASR_TO_DOUBAO = "forward_asr_to_doubao"

	// CamelCase keys for compatibility with expected names
	private const val KEY2_BASE_URL = "baseUrl"
	private const val KEY2_API_KEY = "apiKey"
	private const val KEY2_ENDPOINT_ID = "endpointId"
	private const val KEY2_MODEL = "model" // same as snake-case value, kept for clarity
	private const val KEY2_ENABLE_RETRY = "enableRetry"
	private const val KEY2_MAX_RETRY_ATTEMPTS = "maxRetryAttempts"
	private const val KEY2_IMAGE_PROCESSING_ENABLED = "imageProcessingEnabled"
	private const val KEY2_MAX_IMAGE_SIZE = "maxImageSize"
	private const val KEY2_FALLBACK_IMAGE_MODEL = "fallbackImageModel"
	private const val KEY2_AUC_APP_KEY = "aucAppKey"
	private const val KEY2_AUC_ACCESS_KEY = "aucAccessKey"

	fun read(context: Context): SettingsData {
		val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val rawModel = (
			p.getString(KEY2_MODEL, null)
				?: p.getString(KEY_MODEL, "gpt-4o")
		) ?: "gpt-4o"
		// 清理模型名称，移除换行符和多余空格
		val cleanModel = rawModel.trim().replace("\n", "").replace("\r", "")
		
		return SettingsData(
			baseUrl = (
				p.getString(KEY2_BASE_URL, null)
					?: p.getString(KEY_BASE_URL, "https://ark.cn-beijing.volces.com/api/v3/")
			) ?: "https://ark.cn-beijing.volces.com/api/v3/",
			apiKey = p.getString(KEY2_API_KEY, null) ?: p.getString(KEY_API_KEY, null),
			endpointId = p.getString(KEY2_ENDPOINT_ID, null) ?: p.getString(KEY_ENDPOINT_ID, null),
			model = cleanModel,
			enableRetry = p.getBoolean(KEY2_ENABLE_RETRY, p.getBoolean(KEY_ENABLE_RETRY, true)),
			maxRetryAttempts = p.getInt(KEY2_MAX_RETRY_ATTEMPTS, p.getInt(KEY_MAX_RETRY_ATTEMPTS, 3)),
			imageProcessingEnabled = p.getBoolean(KEY2_IMAGE_PROCESSING_ENABLED, p.getBoolean(KEY_IMAGE_PROCESSING_ENABLED, true)),
			maxImageSize = p.getInt(KEY2_MAX_IMAGE_SIZE, p.getInt(KEY_MAX_IMAGE_SIZE, 1024)),
			fallbackImageModel = (
				p.getString(KEY2_FALLBACK_IMAGE_MODEL, null)
					?: p.getString(KEY_FALLBACK_IMAGE_MODEL, "gpt-4o")
			) ?: "gpt-4o",
			aucAppKey = (
				p.getString(KEY2_AUC_APP_KEY, null)
					?: p.getString(KEY_AUC_APP_KEY, "")
			) ?: "",
			aucAccessKey = (
				p.getString(KEY2_AUC_ACCESS_KEY, null)
					?: p.getString(KEY_AUC_ACCESS_KEY, "")
			) ?: ""
			,
			baiduApiKey = p.getString(KEY_BAIDU_API_KEY, "") ?: "",
			baiduSecretKey = p.getString(KEY_BAIDU_SECRET_KEY, "") ?: "",
			baiduDevPid = p.getInt(KEY_BAIDU_DEV_PID, 1537),
			forwardAsrToDoubao = p.getBoolean(KEY_FORWARD_ASR_TO_DOUBAO, false)
		)
	}

	fun write(context: Context, data: SettingsData) {
		val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val sanitizedBase = sanitizeBaseUrl(data.baseUrl)
		// 清理模型名称，移除换行符和多余空格
		val cleanModel = data.model.trim().replace("\n", "").replace("\r", "")
		
		p.edit()
			// write both camelCase and snake_case for compatibility
			.putString(KEY2_BASE_URL, ensureTrailingSlash(sanitizedBase))
			.putString(KEY_BASE_URL, ensureTrailingSlash(sanitizedBase))
			.putString(KEY2_API_KEY, data.apiKey)
			.putString(KEY_API_KEY, data.apiKey)
			.putString(KEY2_ENDPOINT_ID, data.endpointId)
			.putString(KEY_ENDPOINT_ID, data.endpointId)
			.putString(KEY2_MODEL, cleanModel)
			.putString(KEY_MODEL, cleanModel)
			.putBoolean(KEY2_ENABLE_RETRY, data.enableRetry)
			.putBoolean(KEY_ENABLE_RETRY, data.enableRetry)
			.putInt(KEY2_MAX_RETRY_ATTEMPTS, data.maxRetryAttempts)
			.putInt(KEY_MAX_RETRY_ATTEMPTS, data.maxRetryAttempts)
			.putBoolean(KEY2_IMAGE_PROCESSING_ENABLED, data.imageProcessingEnabled)
			.putBoolean(KEY_IMAGE_PROCESSING_ENABLED, data.imageProcessingEnabled)
			.putInt(KEY2_MAX_IMAGE_SIZE, data.maxImageSize)
			.putInt(KEY_MAX_IMAGE_SIZE, data.maxImageSize)
			.putString(KEY2_FALLBACK_IMAGE_MODEL, data.fallbackImageModel)
			.putString(KEY_FALLBACK_IMAGE_MODEL, data.fallbackImageModel)
			.putString(KEY2_AUC_APP_KEY, data.aucAppKey)
			.putString(KEY_AUC_APP_KEY, data.aucAppKey)
			.putString(KEY2_AUC_ACCESS_KEY, data.aucAccessKey)
			.putString(KEY_AUC_ACCESS_KEY, data.aucAccessKey)
			.putString(KEY_BAIDU_API_KEY, data.baiduApiKey)
			.putString(KEY_BAIDU_SECRET_KEY, data.baiduSecretKey)
			.putInt(KEY_BAIDU_DEV_PID, data.baiduDevPid)
			.putBoolean(KEY_FORWARD_ASR_TO_DOUBAO, data.forwardAsrToDoubao)
			.apply()
	}

	private fun ensureTrailingSlash(url: String): String = if (url.endsWith('/')) url else "$url/"

	private fun sanitizeBaseUrl(url: String): String {
		val trimmed = url.trim().trim('"').trim('\'')
		return if (trimmed.endsWith("/api/v3")) "$trimmed/" else trimmed
	}
}


