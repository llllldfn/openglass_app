package com.example.myapp.data.network

import android.content.Context
import com.example.myapp.model.AiResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Header
import retrofit2.Response
import java.io.File
import java.util.concurrent.TimeUnit

interface AiApi {
	@POST("chat/completions")
	suspend fun sendText(@Body body: RequestBody): AiResponse

	@POST("images/descriptions")
	suspend fun sendImageDescription(@Body body: RequestBody): AiResponse

	@POST("audio/transcriptions")
	suspend fun transcribeAudio(@Body body: RequestBody): AiResponse

	@Multipart
	@POST("chat/completions")
	suspend fun sendMultimodal(
		@Part("text") text: RequestBody?,
		@Part image: MultipartBody.Part?,
		@Part audio: MultipartBody.Part?
	): AiResponse

	// ===== Bytedance AUC Bigmodel ASR (submit/query) =====
	@POST("https://openspeech.bytedance.com/api/v3/auc/bigmodel/submit")
	suspend fun aucSubmit(
		@Header("X-Api-App-Key") appKey: String,
		@Header("X-Api-Access-Key") accessKey: String,
		@Header("X-Api-Resource-Id") resourceId: String = "volc.bigasr.auc",
		@Header("X-Api-Request-Id") requestId: String,
		@Header("X-Api-Sequence") sequence: String = "-1",
		@Body body: RequestBody
	): Response<Unit>

	@POST("https://openspeech.bytedance.com/api/v3/auc/bigmodel/query")
	suspend fun aucQuery(
		@Header("X-Api-App-Key") appKey: String,
		@Header("X-Api-Access-Key") accessKey: String,
		@Header("X-Api-Resource-Id") resourceId: String = "volc.bigasr.auc",
		@Header("X-Api-Request-Id") requestId: String,
		@Header("X-Api-Sequence") sequence: String = "-1",
		@Header("X-Tt-Logid") logId: String? = null,
		@Body body: RequestBody
	): Response<com.example.myapp.model.AucQueryResponse>

	companion object {
		fun create(context: Context, baseUrl: String): AiApi {
			val moshi = Moshi.Builder()
				.add(KotlinJsonAdapterFactory())
				.build()
			val logging = HttpLoggingInterceptor().apply {
				level = HttpLoggingInterceptor.Level.BASIC
			}
			val auth = Interceptor { chain ->
				// 运行时读取最新的 API Key，避免必须重启应用才能生效
				val key = com.example.myapp.settings.Settings.read(context).apiKey
				val req = if (key.isNullOrBlank()) chain.request() else chain.request().newBuilder()
					.addHeader("Authorization", "Bearer $key")
					.build()
				chain.proceed(req)
			}
			val client = OkHttpClient.Builder()
				.addInterceptor(logging)
				.addInterceptor(auth)
				.connectTimeout(20, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.build()
			return Retrofit.Builder()
				.baseUrl(baseUrl)
				.client(client)
				.addConverterFactory(MoshiConverterFactory.create(moshi))
				.build()
				.create(AiApi::class.java)
		}

		fun textPart(text: String?): RequestBody? =
			text?.toRequestBody("text/plain".toMediaType())

		fun filePart(name: String, filename: String, file: File): MultipartBody.Part =
			MultipartBody.Part.createFormData(
				name,
				filename,
				file.asRequestBody("application/octet-stream".toMediaType())
			)
	}
}

