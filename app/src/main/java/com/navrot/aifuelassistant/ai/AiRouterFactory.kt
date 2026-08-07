package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.BuildConfig
import com.navrot.aifuelassistant.ai.router.AiRouter
import okhttp3.OkHttpClient

object AiRouterFactory {
    fun create(okHttpClient: OkHttpClient = OkHttpClient()): AiRouter {

        // 1. DeepSeek
        val deepSeekProvider = BuildConfig.DEEPSEEK_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { DeepSeekAiProvider(httpClient = okHttpClient) }

        // 2. HuggingFace (Qwen 2.5)
        val huggingFaceProvider = BuildConfig.HUGGINGFACE_TOKEN
            .takeIf { it.isNotBlank() }
            ?.let { HuggingFaceAiProvider(httpClient = okHttpClient) }

        // 3. GigaChat (Сбер) - поддерживает оба варианта
        val gigaChatProvider = if (BuildConfig.GIGACHAT_AUTHORIZATION_KEY.isNotBlank()) {
            GigaChatAiProvider(
                authorizationKey = BuildConfig.GIGACHAT_AUTHORIZATION_KEY,
                httpClient = okHttpClient
            )
        } else if (BuildConfig.GIGACHAT_CLIENT_ID.isNotBlank() && BuildConfig.GIGACHAT_CLIENT_SECRET.isNotBlank()) {
            GigaChatAiProvider(
                clientId = BuildConfig.GIGACHAT_CLIENT_ID,
                clientSecret = BuildConfig.GIGACHAT_CLIENT_SECRET,
                httpClient = okHttpClient
            )
        } else {
            null
        }

        // 4. YandexGPT (Яндекс)
        val yandexProvider = BuildConfig.YANDEX_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { apiKey ->
                BuildConfig.YANDEX_FOLDER_ID
                    .takeIf { it.isNotBlank() }
                    ?.let { folderId ->
                        YandexGPTProvider(
                            apiKey = apiKey,
                            folderId = folderId,
                            httpClient = okHttpClient
                        )
                    }
            }

        val configuredProviders = listOfNotNull(
            deepSeekProvider,
            huggingFaceProvider,
            gigaChatProvider,
            yandexProvider
        )

        val providers = configuredProviders.ifEmpty {
            listOf(UnavailableAiProvider("Нет настроенных провайдеров"))
        }

        return AiRouter(providers = providers)
    }
}