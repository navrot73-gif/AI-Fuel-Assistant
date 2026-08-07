package com.navrot.aifuelassistant.ai.router

import android.util.Log
import com.navrot.aifuelassistant.ai.AiProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class AiRouter(
    private val providers: List<AiProvider>,
    private val perProviderTimeoutMs: Long = 8_000L
) {

    suspend fun ask(prompt: String): String = coroutineScope {
        Log.d("AiRouter", "🚀 Гонка: запускаю ${providers.size} провайдеров параллельно (таймаут ${perProviderTimeoutMs}мс)")
        
        val channel = Channel<Result<String>>(providers.size)
        
        providers.forEach { provider ->
            launch {
                val name = provider.javaClass.simpleName
                try {
                    Log.d("AiRouter", "→ $name стартует")
                    val answer = withTimeout(perProviderTimeoutMs) { provider.ask(prompt) }
                    Log.d("AiRouter", "✅ $name финишировал первым!")
                    channel.send(Result.success(answer))
                } catch (e: TimeoutCancellationException) {
                    Log.e("AiRouter", "⏱ $name не уложился в ${perProviderTimeoutMs}мс")
                    channel.send(Result.failure(e))
                } catch (e: Exception) {
                    Log.e("AiRouter", "❌ $name упал: ${e.message}", e)
                    channel.send(Result.failure(e))
                }
            }
        }
        
        // Ждём первый успешный результат или все ошибки
        var failures = 0
        repeat(providers.size) {
            val result = channel.receive()
            if (result.isSuccess) {
                // Отменяем остальные запросы — они больше не нужны
                coroutineContext.cancelChildren()
                Log.d("AiRouter", "🏆 Победитель найден, отменяю остальных")
                return@coroutineScope result.getOrThrow()
            } else {
                failures++
            }
        }
        
        throw IllegalStateException("Все AI провайдеры недоступны ($failures ошибок)")
    }
}
