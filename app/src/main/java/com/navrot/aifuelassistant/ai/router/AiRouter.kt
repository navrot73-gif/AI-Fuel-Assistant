package com.navrot.aifuelassistant.ai.router

class AiRouter(private val providers: List<AiProvider>) {

    suspend fun ask(prompt: String): String {
        for (provider in providers) {
            try {
                return provider.ask(prompt)
            } catch (e: Exception) {
                // пробуем следующий провайдер
            }
        }
        return "AI временно недоступен"
    }
}