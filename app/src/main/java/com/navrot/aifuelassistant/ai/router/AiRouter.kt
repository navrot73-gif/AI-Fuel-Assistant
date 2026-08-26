package com.navrot.aifuelassistant.ai.router

import timber.log.Timber
import com.navrot.aifuelassistant.ai.AiProvider
import com.navrot.aifuelassistant.ai.AiResponseCache
import com.navrot.aifuelassistant.features.dashboard.ChatMessage
import kotlinx.coroutines.*

class AiRouter(
    private val providers: List<AiProvider>,
    private val perProviderTimeoutMs: Long = 8_000L,
    private val logger: ((String, String) -> Unit)? = null,
    private val cache: AiResponseCache = AiResponseCache()
) {

    private fun log(tag: String, msg: String) {
        logger?.invoke(tag, msg) ?: Timber.tag(tag).d(msg)
    }

    private fun logError(tag: String, msg: String, e: Exception? = null) {
        if (logger != null) {
            logger?.invoke(tag, msg)
        } else {
            if (e != null) Timber.tag(tag).e(e, msg) else Timber.tag(tag).e(msg)
        }
    }

    /**
     * Запрашивает ответ у AI-провайдеров с кэшированием.
     *
     * Последовательный fallback: провайдеры перебираются по порядку,
     * первый успешный ответ возвращается. Прокси идёт первым — он уже
     * делает fallback внутри, поэтому клиент не дублирует параллельную гонку.
     *
     * Перед вызовом провайдеров проверяется кэш:
     * - [lat]/[lon] участвуют в ключе (MD5(prompt + lat + lon))
     * - [isGasStationQuery] = true → TTL 15 минут (АЗС-запросы)
     * - [isGasStationQuery] = false → TTL 24 часа (общие вопросы)
     *
     * При промахе кэша успешный ответ сохраняется для повторных запросов.
     */
    suspend fun ask(
        prompt: String,
        lat: Double? = null,
        lon: Double? = null,
        isGasStationQuery: Boolean = false,
        history: List<ChatMessage> = emptyList()
    ): String = coroutineScope {
        val ttlMs = if (isGasStationQuery) {
            AiResponseCache.TTL_GAS_STATION_MS
        } else {
            AiResponseCache.TTL_GENERAL_MS
        }

        cache.get(prompt, lat, lon, ttlMs)?.let { cached ->
            log("AiRouter", "💾 Кэш: ответ найден (${if (isGasStationQuery) "АЗС, 15 мин" else "общий, 24 ч"})")
            return@coroutineScope cached
        }

        log("AiRouter", "🔁 Fallback: перебираю ${providers.size} провайдеров (таймаут ${perProviderTimeoutMs}мс на провайдера)")

        var lastError: Throwable? = null
        for (provider in providers) {
            val name = provider.javaClass.simpleName
            try {
                log("AiRouter", "→ $name стартует")
                val answer = withTimeout(perProviderTimeoutMs) { provider.ask(prompt, history) }
                log("AiRouter", "✅ $name вернул ответ")
                cache.put(prompt, lat, lon, answer, ttlMs)
                return@coroutineScope answer
            } catch (e: TimeoutCancellationException) {
                logError("AiRouter", "⏱ $name не уложился в ${perProviderTimeoutMs}мс")
                lastError = e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logError("AiRouter", "❌ $name упал: ${e.message}", e)
                lastError = e
            }
        }

        throw IllegalStateException(
            "Все AI провайдеры недоступны (${providers.size} ошибок): ${lastError?.message}"
        )
    }
}
