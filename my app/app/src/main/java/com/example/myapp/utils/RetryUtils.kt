package com.example.myapp.utils

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * 重试和退避机制工具
 */
object RetryUtils {

    suspend fun delayFor(milliseconds: Long) {
        kotlinx.coroutines.delay(milliseconds)
    }

    fun exponentialBackoffDelay(
        currentFailureCount: Int,
        minDelay: Long,
        maxDelay: Long,
        maxFailureCount: Int
    ): Long {
        val base = minDelay + ((maxDelay - minDelay) / maxFailureCount) *
            max(currentFailureCount, maxFailureCount)
        // Rand哈哈哈哈omize 50%-100% of computed base
        val randomized = base * (0.5 + Math.random() * 0.5)
        return randomized.roundToLong()
    }

    /**
     * 简单重试（带退避）
     */
    suspend fun <T> retry(
        maxAttempts: Int = 50,
        minDelayMs: Long = 250,
        maxDelayMs: Long = 1000,
        onError: ((Throwable, Int) -> Unit)? = null,
        block: suspend () -> T
    ): T {
        var failures = 0
        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                failures = (failures + 1).coerceAtMost(maxAttempts)
                onError?.invoke(e, failures)
                val wait = exponentialBackoffDelay(failures, minDelayMs, maxDelayMs, maxAttempts)
                delayFor(wait)
            }
        }
    }

    /**
     * 带条件的重试
     */
    suspend fun <T> retryWithCondition(
        maxAttempts: Int = 3,
        condition: (T) -> Boolean = { true },
        onRetry: (Int, Throwable) -> Unit = { _, _ -> },
        operation: suspend () -> T
    ): T {
        var lastException: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                val result = operation()
                if (condition(result)) return result
            } catch (e: Throwable) {
                lastException = e
                onRetry(attempt + 1, e)
                if (attempt < maxAttempts - 1) {
                    delayFor(exponentialBackoffDelay(attempt + 1, 100, 1000, maxAttempts))
                }
            }
        }
        throw lastException ?: RuntimeException("重试失败，达到最大尝试次数")
    }
}

