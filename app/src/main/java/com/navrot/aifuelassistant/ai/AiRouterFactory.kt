package com.navrot.aifuelassistant.ai

object AiRouterFactory {
    fun create(): AiRouter {
        return AiRouter(
            providers = listOf(
                UnavailableAiProvider("primary")
            )
        )
    }
}
