package com.example.myapp.input.camera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class CameraInput(private val context: Context) {
	private var imageCapture: ImageCapture? = null

	suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
		Log.d("[PHOTO_PREVIEW] CameraInput", "开始绑定相机预览")
		try {
			ensureCameraPermission()
			Log.d("[PHOTO_PREVIEW] CameraInput", "相机权限检查通过")
			
			val cameraProvider = getCameraProvider()
			Log.d("[PHOTO_PREVIEW] CameraInput", "获取相机提供者成功")
			
			val preview = androidx.camera.core.Preview.Builder().build().also {
				it.setSurfaceProvider(previewView.surfaceProvider)
			}
			Log.d("[PHOTO_PREVIEW] CameraInput", "创建预览用例成功")
			
			imageCapture = ImageCapture.Builder()
				.setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
				.build()
			Log.d("[PHOTO_PREVIEW] CameraInput", "创建图像捕获用例成功")
			
			try {
				cameraProvider.unbindAll()
				cameraProvider.bindToLifecycle(
					lifecycleOwner,
					CameraSelector.DEFAULT_BACK_CAMERA,
					preview,
					imageCapture
				)
				Log.d("[PHOTO_PREVIEW] CameraInput", "相机绑定到生命周期成功")
			} catch (e: Exception) {
				Log.e("[PHOTO_PREVIEW] CameraInput", "相机绑定到生命周期失败", e)
				throw e
			}
		} catch (e: SecurityException) {
			Log.e("[PHOTO_PREVIEW] CameraInput", "相机权限不足", e)
			throw e
		} catch (e: Exception) {
			Log.e("[PHOTO_PREVIEW] CameraInput", "相机绑定失败", e)
			throw e
		}
	}

	fun periodicJpegFlow(intervalMs: Long = 5000L): Flow<ByteArray> = callbackFlow {
		ensureCameraPermission()
		val capture = imageCapture
		if (capture == null) {
			close(IllegalStateException("Camera not bound"))
			return@callbackFlow
		}
		val scope = CoroutineScope(Dispatchers.IO)
		scope.launch {
			while (isActive) {
				try {
					val bytes = takeJpegOnce(capture)
					trySend(bytes)
				} catch (t: Throwable) {
					Log.e("CameraInput", "capture error", t)
				}
				delay(intervalMs)
			}
		}
		awaitClose { }
	}

	private suspend fun takeJpegOnce(capture: ImageCapture): ByteArray = suspendCancellableCoroutine { cont ->
		val tmp = File.createTempFile("cap", ".jpg", context.cacheDir)
		val output = ImageCapture.OutputFileOptions.Builder(tmp).build()
		capture.takePicture(output, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
			override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
				try {
					val data = tmp.readBytes()
					cont.resume(data)
				} catch (e: Exception) {
					cont.cancel(e)
				} finally {
					tmp.delete()
				}
			}

			override fun onError(exception: ImageCaptureException) {
				cont.cancel(exception)
			}
		})
	}

	private suspend fun getCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
		val providerFuture = ProcessCameraProvider.getInstance(context)
		providerFuture.addListener(
			{ cont.resume(providerFuture.get()) },
			ContextCompat.getMainExecutor(context)
		)
	}

	// 对外暴露一次性拍照接口，便于“手动拍照并发送”
	suspend fun captureJpegOnce(): ByteArray {
		ensureCameraPermission()
		val capture = imageCapture ?: throw IllegalStateException("Camera not bound")
		return takeJpegOnce(capture)
	}

	private fun ensureCameraPermission() {
		val granted = ContextCompat.checkSelfPermission(
			context,
			android.Manifest.permission.CAMERA
		) == PackageManager.PERMISSION_GRANTED
		if (!granted) throw SecurityException("Missing CAMERA permission")
	}
}


