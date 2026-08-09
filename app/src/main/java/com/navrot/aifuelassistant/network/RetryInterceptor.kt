package com.navrot.aifuelassistant.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Простой интерсептор повторных попыток для OkHttp.
 * Повторяет запросы при сетевых ошибках и при 5xx ответах.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val initialBackoffMs: Long = 250
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var lastException: IOException? = null
        val request = chain.request()

        while (true) {
            try {
                val response = chain.proceed(request)
                if (!shouldRetryResponse(response) || attempt >= maxRetries) {
                    return response
                }
                // close body before retry
                try { response.close() } catch (_: Exception) {}
            } catch (e: IOException) {
                lastException = e
                if (attempt >= maxRetries) throw e
            }

            attempt++
            val backoff = initialBackoffMs * (1L shl (attempt - 1))
            try { Thread.sleep(backoff.coerceAtMost(5000L)) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    private fun shouldRetryResponse(response: Response): Boolean {
        val code = response.code
        return code in 500..599
    }
}
