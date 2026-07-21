package com.navrot.aifuelassistant.ai.router

import com.navrot.aifuelassistant.ai.AiProvider

class AiRouter(private val providers: List<AiProvider>) {

    suspend fun ask(prompt: String): String {
        for (provider in providers) {
            try {
                return provider.ask(prompt)
            } catch (e: Exception) {
                // пробуем следующий провайдер
            }
        }
        throw IllegalStateException("AI providers unavailable")
    }
}