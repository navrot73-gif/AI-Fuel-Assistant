package com.navrot.aifuelassistant.ai.router

import android.util.Log
import com.navrot.aifuelassistant.ai.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class AiRouter(
    private val providers: List<AiProvider>,
    private val perProviderTimeoutMs: Long = 8_000L,
    private val logger: ((String, String) -> Unit)? = null
) {

    private fun log(tag: String, msg: String) {
        logger?.invoke(tag, msg) ?: Log.d(tag, msg)
    }

    private fun logError(tag: String, msg: String, e: Exception? = null) {
        if (logger != null) logger.invoke(tag, msg)
        else if (e != null) Log.e(tag, msg, e) else Log.e(tag, msg)
    }

    suspend fun ask(prompt: String): String = coroutineScope {
        if (providers.isEmpty()) {
            throw IllegalStateException("Нет доступных AI-провайдеров")
        }

        log("AiRouter", "Запускаю ${providers.size} AI-провайдеров параллельно, таймаут ${perProviderTimeoutMs}мс")
        val channel = Channel<Result<String>>(providers.size)

        providers.forEach { provider ->
            launch {
                val name = provider.name.ifBlank { provider.javaClass.simpleName }
                try {
                    val answer = withTimeout(perProviderTimeoutMs) { provider.ask(prompt) }
                    if (answer.isBlank()) {
                        channel.send(Result.failure(IllegalStateException("$name вернул пустой ответ")))
                    } else {
                        log("AiRouter", "Победитель: $name")
                        channel.send(Result.success(answer.trim()))
                    }
                } catch (e: TimeoutCancellationException) {
                    logError("AiRouter", "$name не уложился в ${perProviderTimeoutMs}мс")
                    channel.send(Result.failure(e))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logError("AiRouter", "$name завершился ошибкой: ${e.message}", e)
                    channel.send(Result.failure(e))
                }
            }
        }

        var failures = 0
        repeat(providers.size) {
            val result = channel.receive()
            if (result.isSuccess) {
                coroutineContext.cancelChildren()
                return@coroutineScope result.getOrThrow()
            }
            failures++
        }

        throw IllegalStateException("Все AI-провайдеры недоступны ($failures ошибок)")
    }
}
