import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

fun testAiConnection() {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // 测试默认的API端点
    val request = Request.Builder()
        .url("https://ark.cn-beijing.volces.com/api/v3/")
        .build()
    
    try {
        val response = client.newCall(request).execute()
        println("连接状态: ${response.code}")
        println("响应头: ${response.headers}")
    } catch (e: Exception) {
        println("连接失败: ${e.message}")
    }
}

// 测试API请求格式
fun testApiRequest() {
    val jsonBody = """
        {
            "model": "gpt-4o",
            "messages": [
                {"role": "user", "content": "Hello"}
            ],
            "max_tokens": 100
        }
    """.trimIndent()
    
    println("测试请求体:")
    println(jsonBody)
}
