package com.navrot.aifuelassistant.ai.router

import android.util.Log
import com.navrot.aifuelassistant.ai.AiProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class AiRouter(
    private val providers: List<AiProvider>,
    private val perProviderTimeoutMs: Long = 8_000L,
    private val logger: ((String, String) -> Unit)? = null
) {

    private fun log(tag: String, msg: String) {
        logger?.invoke(tag, msg) ?: Log.d(tag, msg)
    }

    private fun logError(tag: String, msg: String, e: Exception? = null) {
        if (logger != null) {
            logger(tag, msg)
        } else {
            if (e != null) Log.e(tag, msg, e) else Log.e(tag, msg)
        }
    }

    suspend fun ask(prompt: String): String = coroutineScope {
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
                log("AiRouter", "🏆 Победитель найден, отменяю остальных")
                return@coroutineScope result.getOrThrow()
            } else {
                failures++
            }
        }
        
        throw IllegalStateException("Все AI провайдеры недоступны ($failures ошибок)")
    }
}
