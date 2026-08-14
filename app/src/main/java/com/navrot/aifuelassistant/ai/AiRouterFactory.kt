package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.BuildConfig
import com.navrot.aifuelassistant.ai.providers.AiProxyProvider
import com.navrot.aifuelassistant.ai.router.AiRouter
import okhttp3.OkHttpClient

object AiRouterFactory {
    fun create(okHttpClient: OkHttpClient = OkHttpClient()): AiRouter {

        // 0. Cloudflare Worker прокси — первичный провайдер.
        // Прокси сам делает fallback (DeepSeek → Qwen → GigaChat) внутри.
        val proxyProvider = AiProxyProvider(httpClient = okHttpClient)

        // 1. DeepSeek — резервный fallback, если прокси недоступен
        val deepSeekProvider = BuildConfig.DEEPSEEK_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { DeepSeekAiProvider(httpClient = okHttpClient) }

        // 2. HuggingFace (Qwen 2.5) - Disabled 2026-08-11: 402 credits depleted, re-enable next month
        val huggingFaceProvider = null

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

        // 4. Qwen (Alibaba Cloud DashScope)
        val qwenProvider = BuildConfig.QWEN_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let { QwenAiProvider(httpClient = okHttpClient) }

        // 5. YandexGPT - Disabled 2026-08-13: 403 Permission denied (IAM-роль без прав на модель)
        val yandexProvider = null

        val providers = listOfNotNull(
            proxyProvider,
            deepSeekProvider,
            gigaChatProvider,
            qwenProvider
        ).ifEmpty {
            listOf(UnavailableAiProvider("Нет настроенных провайдеров"))
        }

        return AiRouter(providers = providers)
    }
}