package com.example.myapp.data.network

import com.example.myapp.model.AiResponse
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface GroqApi {
    @POST("openai/v1/chat/completions")
    suspend fun sendText(@Body body: RequestBody): AiResponse

    companion object {
        fun create(apiKey: String?): GroqApi {
            val moshi = Moshi.Builder().build()
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val auth = Interceptor { chain ->
                val req = if (apiKey.isNullOrBlank()) chain.request() else chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
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
                .baseUrl("https://api.groq.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GroqApi::class.java)
        }
    }
}
