package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.BuildConfig

object AiRouterFactory {
    fun create(): AiRouter {
        val gigaChatProvider = BuildConfig.GIGACHAT_AUTHORIZATION_KEY
            .takeIf { it.isNotBlank() }
            ?.let { GigaChatAiProvider(authorizationKey = it) }

        val yandexProvider = if (
            BuildConfig.YANDEX_API_KEY.isNotBlank() &&
            BuildConfig.YANDEX_FOLDER_ID.isNotBlank()
        ) {
            YandexGptAiProvider(
                apiKey = BuildConfig.YANDEX_API_KEY,
                folderId = BuildConfig.YANDEX_FOLDER_ID
            )
        } else {
            null
        }

        val deepSeekProvider = BuildConfig.DEEPSEEK_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let {
                OpenAiCompatibleAiProvider(
                    name = "DeepSeek",
                    apiKey = it,
                    baseUrl = "https://api.deepseek.com",
                    model = "deepseek-chat"
                )
            }

        val qwenProvider = BuildConfig.QWEN_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let {
                OpenAiCompatibleAiProvider(
                    name = "Qwen",
                    apiKey = it,
                    baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
                    model = "qwen-plus"
                )
            }

        return AiRouter(
            providers = listOfNotNull(
                gigaChatProvider,
                yandexProvider,
                deepSeekProvider,
                qwenProvider,
                UnavailableAiProvider("fallback")
            )
        )
    }
}
