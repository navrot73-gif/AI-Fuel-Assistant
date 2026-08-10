package com.navrot.aifuelassistant.ai.router

import android.util.Log
import com.navrot.aifuelassistant.ai.AiProvider
import com.navrot.aifuelassistant.ai.AiResponseCache
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class AiRouter(
    private val providers: List<AiProvider>,
    private val perProviderTimeoutMs: Long = 8_000L,
    private val logger: ((String, String) -> Unit)? = null,
    private val cache: AiResponseCache = AiResponseCache()
) {

    private fun log(tag: String, msg: String) {
        logger?.invoke(tag, msg) ?: Log.d(tag, msg)
    }

    private fun logError(tag: String, msg: String, e: Exception? = null) {
        if (logger != null) {
            logger?.invoke(tag, msg)
        } else {
            if (e != null) Log.e(tag, msg, e) else Log.e(tag, msg)
        }
    }

    /**
     * Запрашивает ответ у AI-провайдеров с кэшированием.
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
        isGasStationQuery: Boolean = false
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

        log("AiRouter", "🚀 Гонка: запускаю ${providers.size} провайдеров параллельно (таймаут ${perProviderTimeoutMs}мс)")
        
        val channel = Channel<Result<String>>(providers.size)
        
        providers.forEach { provider ->
            launch {
                val name = provider.javaClass.simpleName
                try {
                    log("AiRouter", "→ $name стартует")
                    val answer = withTimeout(perProviderTimeoutMs) { provider.ask(prompt) }
                    log("AiRouter", "✅ $name финишировал первым!")
                    channel.send(Result.success(answer))
                } catch (e: TimeoutCancellationException) {
                    logError("AiRouter", "⏱ $name не уложился в ${perProviderTimeoutMs}мс")
                    channel.send(Result.failure(e))
                } catch (e: Exception) {
                    logError("AiRouter", "❌ $name упал: ${e.message}", e)
                    channel.send(Result.failure(e))
                }
            }
        }
        
        var failures = 0
        repeat(providers.size) {
            val result = channel.receive()
            if (result.isSuccess) {
                coroutineContext.cancelChildren()
                val answer = result.getOrThrow()
                log("AiRouter", "🏆 Победитель найден, отменяю остальных")
                cache.put(prompt, lat, lon, answer, ttlMs)
                return@coroutineScope answer
            } else {
                failures++
            }
        }
        
        throw IllegalStateException("Все AI провайдеры недоступны ($failures ошибок)")
    }
}