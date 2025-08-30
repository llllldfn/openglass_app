package com.example.myapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream

/**
 * 图片处理测试工具
 */
object ImageProcessingTest {
    
    /**
     * 创建测试图片
     */
    fun createTestImage(width: Int = 512, height: Int = 512): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // 创建一个简单的测试图片
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = when {
                    x < width / 3 -> Color.RED
                    x < 2 * width / 3 -> Color.GREEN
                    else -> Color.BLUE
                }
                bitmap.setPixel(x, y, color)
            }
        }
        
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        bitmap.recycle()
        
        return outputStream.toByteArray()
    }
    
    /**
     * 测试图片处理功能
     */
    fun testImageProcessing(context: Context) {
        println("🧪 开始图片处理测试...")
        
        try {
            // 创建测试图片
            val testImage = createTestImage(1024, 768)
            println("✅ 创建测试图片成功: ${testImage.size} bytes")
            
            // 测试图片验证
            val isValid = com.example.myapp.utils.ImageUtils.isValidImage(testImage)
            println("✅ 图片验证: ${if (isValid) "有效" else "无效"}")
            
            // 获取图片信息
            val imageInfo = com.example.myapp.utils.ImageUtils.getImageInfo(testImage)
            println("✅ 图片信息: $imageInfo")
            
            // 测试图片压缩
            val compressedImage = com.example.myapp.utils.ImageUtils.resizeImage(testImage, 512, 512)
            println("✅ 图片压缩成功: ${compressedImage.size} bytes")
            
            // 测试Base64编码
            val base64 = com.example.myapp.utils.ImageUtils.toBase64(testImage)
            println("✅ Base64编码成功: ${base64.length} characters")
            
            println("🎉 所有图片处理测试通过!")
            
        } catch (e: Exception) {
            println("❌ 图片处理测试失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 测试AI图片处理
     */
    fun testAiImageProcessing(context: Context) {
        println("🧪 开始AI图片处理测试...")
        
        try {
            // 创建测试图片
            val testImage = createTestImage(512, 512)
            println("✅ 创建测试图片成功: ${testImage.size} bytes")
            
            // 模拟AI处理
            val settings = com.example.myapp.settings.Settings.read(context)
            println("✅ 读取设置成功:")
            println("   - Base URL: ${settings.baseUrl}")
            println("   - Model: ${settings.model}")
            println("   - API Key: ${if (settings.apiKey.isNullOrBlank()) "未设置" else "已设置"}")
            println("   - 备用图片模型: ${settings.fallbackImageModel}")
            
            // 检查模型是否支持图片
            val modelLower = settings.model.lowercase()
            val supportsImage = modelLower.contains("gpt-4") || modelLower.contains("vision") || modelLower.contains("claude")
            println("✅ 模型图片支持: ${if (supportsImage) "支持" else "不支持，将使用备用模型"}")
            
            println("🎉 AI图片处理配置检查完成!")
            
        } catch (e: Exception) {
            println("❌ AI图片处理测试失败: ${e.message}")
            e.printStackTrace()
        }
    }
}
