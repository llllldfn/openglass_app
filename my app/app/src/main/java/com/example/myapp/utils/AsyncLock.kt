package com.example.myapp.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

/**
 * 异步锁，用于确保同一时间只有一个协程执行特定代码块
 * 借鉴OpenGlass项目的实现
 */
class AsyncLock {
    private var permits: Int = 1
    private val promiseResolverQueue = mutableListOf<() -> Unit>()

    /**
     * 在锁内执行函数
     * @param func 要执行的函数
     * @return 函数执行结果
     */
    suspend fun <T> inLock(func: suspend () -> T): T {
        try {
            lock()
            return func()
        } finally {
            unlock()
        }
    }

    private suspend fun lock() {
        if (permits > 0) {
            permits -= 1
            return
        }
        
        suspendCancellableCoroutine<Unit> { continuation ->
            promiseResolverQueue.add {
                continuation.resume(Unit)
            }
        }
    }

    private fun unlock() {
        permits += 1
        if (permits > 1 && promiseResolverQueue.isNotEmpty()) {
            throw Error("permits should never be > 0 when there is someone waiting.")
        } else if (permits == 1 && promiseResolverQueue.isNotEmpty()) {
            // 如果有其他等待者，立即消费释放的许可并让等待的函数恢复
            permits -= 1
            val nextResolver = promiseResolverQueue.removeAt(0)
            // 在下一个tick解析
            GlobalScope.launch { nextResolver() }
        }
    }
}
