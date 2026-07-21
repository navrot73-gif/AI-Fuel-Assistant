package com.navrot.aifuelassistant.ai

import android.util.Log
import com.navrot.aifuelassistant.BuildConfig
import com.navrot.aifuelassistant.ai.router.AiRouter

object AiRouterFactory {
    fun create(): AiRouter {
        Log.d("AiRouterFactory", "DEEPSEEK_API_KEY: '${BuildConfig.DEEPSEEK_API_KEY.take(10)}...'")
        Log.d("AiRouterFactory", "HUGGINGFACE_TOKEN: '${BuildConfig.HUGGINGFACE_TOKEN.take(10)}...'")
        Log.d("AiRouterFactory", "GIGACHAT_CLIENT_ID: '${BuildConfig.GIGACHAT_CLIENT_ID.take(10)}...'")
        Log.d("AiRouterFactory", "GIGACHAT_CLIENT_SECRET: '${BuildConfig.GIGACHAT_CLIENT_SECRET.take(10)}...'")
        Log.d("AiRouterFactory", "GIGACHAT_AUTHORIZATION_KEY: '${BuildConfig.GIGACHAT_AUTHORIZATION_KEY.take(10)}...'")
        Log.d("AiRouterFactory", "YANDEX_API_KEY: '${BuildConfig.YANDEX_API_KEY.take(10)}...'")
        Log.d("AiRouterFactory", "YANDEX_FOLDER_ID: '${BuildConfig.YANDEX_FOLDER_ID.take(10)}...'")

        // 1. DeepSeek
        val deepSeekProvider = BuildConfig.DEEPSEEK_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let {
                Log.d("AiRouterFactory", "Создаём DeepSeek провайдер")
                DeepSeekAiProvider()
            }

        // 2. HuggingFace (Qwen 2.5)
        val huggingFaceProvider = BuildConfig.HUGGINGFACE_TOKEN
            .takeIf { it.isNotBlank() }
            ?.let {
                Log.d("AiRouterFactory", "Создаём HuggingFace провайдер")
                HuggingFaceAiProvider()
            }

        // 3. GigaChat (Сбер) - поддерживает оба варианта
        val gigaChatProvider = if (BuildConfig.GIGACHAT_AUTHORIZATION_KEY.isNotBlank()) {
            Log.d("AiRouterFactory", "Создаём GigaChat провайдер (authorizationKey)")
            GigaChatAiProvider(authorizationKey = BuildConfig.GIGACHAT_AUTHORIZATION_KEY)
        } else if (BuildConfig.GIGACHAT_CLIENT_ID.isNotBlank() && BuildConfig.GIGACHAT_CLIENT_SECRET.isNotBlank()) {
            Log.d("AiRouterFactory", "Создаём GigaChat провайдер (clientId + clientSecret)")
            GigaChatAiProvider(
                clientId = BuildConfig.GIGACHAT_CLIENT_ID,
                clientSecret = BuildConfig.GIGACHAT_CLIENT_SECRET
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
                        Log.d("AiRouterFactory", "Создаём YandexGPT провайдер")
                        YandexGPTProvider(
                            apiKey = apiKey,
                            folderId = folderId
                        )
                    }
            }

        val providers = listOfNotNull(
            deepSeekProvider,
            huggingFaceProvider,
            gigaChatProvider,
            yandexProvider,
            UnavailableAiProvider("Нет настроенных провайдеров")
        )

        Log.d("AiRouterFactory", "Всего провайдеров: ${providers.size}")

        return AiRouter(providers = providers)
    }
}