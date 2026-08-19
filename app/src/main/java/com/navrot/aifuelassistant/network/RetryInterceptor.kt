package com.navrot.aifuelassistant.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.random.Random

/**
 * Интерсептор повторных попыток для OkHttp.
 *
 * Повторяет запросы при:
 *  - сетевых ошибках (IOException)
 *  - HTTP 5xx (серверные ошибки)
 *  - HTTP 429 (Too Many Requests / Rate Limit) — добавлено, т.к. AI-провайдеры
 *    часто возвращают 429 с заголовком Retry-After, и именно этот код означает
 *    «подожди и попробуй снова».
 *
 * НЕ повторяет: 4xx (кроме 429) — это клиентские ошибки (401, 403, 404, 400),
 * повтор не имеет смысла.
 *
 * Для запросов к Cloudflare Worker (host содержит "navrot73.workers.dev")
 * используется maxRetries = 1, чтобы не нагружать единый прокси.
 *
 * Backoff: экспоненциальный с jitter'ом — initialBackoffMs * 2^(attempt-1),
 * ограничено 5 сек. Jitter (±25%) предотвращает thundering herd, когда
 * множество клиентов одновременно получают 5xx и пытаются ретраить синхронно.
 *
 * Потокобезопасность: интерсептор не имеет состояния (stateless), каждый
 * вызов [intercept] независим.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val initialBackoffMs: Long = 250,
    /**
     * Функция ожидания. По умолчанию [Thread.sleep], но может быть заменена
     * в тестах на заглушку, которая записывает длительности без реального ожидания.
     */
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    /**
     * Источник случайности для jitter'а. По умолчанию [Random.Default], в тестах
     * можно передать детерминированный [Random] для предсказуемых результатов.
     */
    private val random: Random = Random.Default
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var lastException: IOException? = null
        val request = chain.request()

        val host = request.url.host
        val path = request.url.encodedPath
        // Для proxy запросов ограничиваем ретраи до 1, чтобы не нагружать единый прокси.
        val isProxyRequest = host.contains("navrot73.workers.dev")
        // Endpoint /route имеет жёсткий 3-сек таймаут в FuelApiImpl (callTimeout=3s).
        // Ретраи с backoff 250ms+500ms = 750ms+ съедают почти весь бюджет → запрос
        // падает по SocketTimeoutException → MapViewModel fallback'ит на прямую линию,
        // которая визуально может «выходить за конечную точку» при неточной геопозиции.
        // Поэтому /route вообще не ретраим — лучше быстро упасть и дать fallback,
        // чем ждать и таймаутить.
        val isRouteEndpoint = isProxyRequest && (path == "/route" || path.startsWith("/route/"))
        val effectiveMaxRetries = when {
            isRouteEndpoint -> 0
            isProxyRequest -> 1
            else -> maxRetries
        }

        while (true) {
            try {
                val response = chain.proceed(request)
                if (!shouldRetryResponse(response) || attempt >= effectiveMaxRetries) {
                    return response
                }
                // close body before retry
                try { response.close() } catch (_: Exception) {}
            } catch (e: IOException) {
                lastException = e
                if (attempt >= effectiveMaxRetries) throw e
            }

            attempt++
            val backoff = computeBackoff(attempt)
            try {
                sleep(backoff)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw lastException ?: IOException("Retry interrupted")
            }
        }
    }

    /**
     * Экспоненциальный backoff с jitter'ом: initialBackoffMs * 2^(attempt-1),
     * ограничено 5 сек. Jitter: ±25% от базового backoff.
     */
    internal fun computeBackoff(attempt: Int): Long {
        val base = initialBackoffMs * (1L shl (attempt - 1))
        val capped = base.coerceAtMost(5000L)
        // Jitter: random в диапазоне [0.75 * capped, 1.25 * capped]
        val jitterRange = capped / 4 // 25% от capped
        val jitter = if (jitterRange > 0) random.nextLong(0, 2 * jitterRange + 1) - jitterRange else 0L
        return (capped + jitter).coerceAtLeast(0L)
    }

    /**
     * Коды, для которых имеет смысл повторить запрос.
     * 5xx — серверная ошибка, возможно временная.
     * 429 — Too Many Requests, сервер явно просит подождать.
     */
    private fun shouldRetryResponse(response: Response): Boolean {
        val code = response.code
        return code in 500..599 || code == 429
    }
}
