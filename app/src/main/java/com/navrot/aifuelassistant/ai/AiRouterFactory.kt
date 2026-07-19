package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.BuildConfig

object AiRouterFactory {
    fun create(): AiRouter {
        val gigaChatProvider = BuildConfig.GIGACHAT_AUTHORIZATION_KEY
            .takeIf { it.isNotBlank() }
            ?.let { GigaChatAiProvider(authorizationKey = it) }

        return AiRouter(
            providers = listOfNotNull(
                gigaChatProvider,
                UnavailableAiProvider("primary")
            )
        )
    }
}
