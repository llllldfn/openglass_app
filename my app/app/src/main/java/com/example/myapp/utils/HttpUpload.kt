package com.example.myapp.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.io.File
import java.util.concurrent.TimeUnit

object HttpUpload {
	private val client: OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(20, TimeUnit.SECONDS)
		.readTimeout(120, TimeUnit.SECONDS)
		.build()

	/**
	 * Tries several anonymous file hosts and returns a public URL.
	 * Order: tmpfiles.org → file.io → 0x0.st
	 */
	fun uploadPublicUrl(bytes: ByteArray, filename: String, contentType: String = "audio/wav"): Result<String> = runCatching {
		// Prefer services that return raw file links directly
		tryUpload0x0(bytes, filename, contentType)
			.orElse { tryUploadTmpFiles(bytes, filename, contentType) }
			.orElse { tryUploadFileIo(bytes, filename, contentType) }
			.getOrThrow()
	}

	private fun tryUploadTmpFiles(bytes: ByteArray, filename: String, contentType: String): Result<String> = runCatching {
		// tmpfiles.org requires multipart form-data under key "file"
		val tmp = File.createTempFile("upload_", "_" + filename)
		tmp.writeBytes(bytes)
		val body = MultipartBody.Builder().setType(MultipartBody.FORM)
			.addFormDataPart(
				"file",
				filename,
				tmp.asRequestBody(contentType.toMediaType())
			)
			.build()
		val req = Request.Builder()
			.url("https://tmpfiles.org/api/v1/upload")
			.post(body)
			.build()
		client.newCall(req).execute().use { resp ->
			if (!resp.isSuccessful) throw IllegalStateException("tmpfiles upload failed: HTTP ${resp.code}")
			val json = resp.body?.string().orEmpty()
			// response like: {"status":true,"data":{"url":"https:\/\/tmpfiles.org\/XXXXX"}}
			val pageUrl = Regex("""\"url\"\s*:\s*\"(.*?)\"""").find(json)?.groupValues?.getOrNull(1)
				?: throw IllegalStateException("tmpfiles: url not found")
			// convert to direct download URL: https://tmpfiles.org/dl/<id>/${filename}
			val id = pageUrl.trimEnd('/').substringAfterLast('/')
			val direct = "https://tmpfiles.org/dl/$id/$filename"
			direct
		}
	}

	private fun tryUploadFileIo(bytes: ByteArray, filename: String, contentType: String): Result<String> = runCatching {
		val tmp = File.createTempFile("upload_", "_" + filename)
		tmp.writeBytes(bytes)
		val body = MultipartBody.Builder().setType(MultipartBody.FORM)
			.addFormDataPart(
				"file",
				filename,
				tmp.asRequestBody(contentType.toMediaType())
			)
			.build()
		val req = Request.Builder()
			.url("https://file.io")
			.post(body)
			.build()
		client.newCall(req).execute().use { resp ->
			if (!resp.isSuccessful) throw IllegalStateException("file.io upload failed: HTTP ${resp.code}")
			val json = resp.body?.string().orEmpty()
			// response like: {"success":true,"link":"https://file.io/abcd"}
			val base = Regex("""\"link\"\s*:\s*\"(.*?)\"""").find(json)?.groupValues?.getOrNull(1)
				?: throw IllegalStateException("file.io: link not found")
			// ensure direct download
			val url = if (base.contains("?")) "$base&download=1" else "$base?download=1"
			url
		}
	}

	private fun tryUpload0x0(bytes: ByteArray, filename: String, contentType: String): Result<String> = runCatching {
		val tmp = File.createTempFile("upload_", "_" + filename)
		tmp.writeBytes(bytes)
		val body = MultipartBody.Builder().setType(MultipartBody.FORM)
			.addFormDataPart("file", filename, tmp.asRequestBody(contentType.toMediaType()))
			.build()
		val req = Request.Builder()
			.url("https://0x0.st")
			.post(body)
			.build()
		client.newCall(req).execute().use { resp ->
			if (!resp.isSuccessful) throw IllegalStateException("0x0.st upload failed: HTTP ${resp.code}")
			val text = resp.body?.string()?.trim().orEmpty()
			if (text.isBlank()) throw IllegalStateException("0x0.st empty body")
			text
		}
	}

	// Helper to chain Results
	private inline fun <T> Result<T>.orElse(block: () -> Result<T>): Result<T> =
		if (this.isSuccess) this else block()
}
