package com.navrot.aifuelassistant.ai

class AiRouter(
    private val providers: List<AiProvider> = emptyList()
) {
    suspend fun ask(prompt: String): String {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        var lastError: Throwable? = null

        for (provider in providers) {
            try {
                return provider.ask(prompt)
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw IllegalStateException(
            "No AI provider is currently available",
            lastError
        )
    }
}
