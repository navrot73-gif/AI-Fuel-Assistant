package com.navrot.aifuelassistant.ai.router

import com.navrot.aifuelassistant.BuildConfig

object AiConfig {
    val gigaChatClientId: String get() = BuildConfig.GIGACHAT_CLIENT_ID
    val gigaChatClientSecret: String get() = BuildConfig.GIGACHAT_CLIENT_SECRET

    val yandexApiKey: String get() = BuildConfig.YANDEX_API_KEY
    val yandexFolderId: String get() = BuildConfig.YANDEX_FOLDER_ID

    val deepSeekApiKey: String get() = BuildConfig.DEEPSEEK_API_KEY

    val qwenApiKey: String get() = BuildConfig.QWEN_API_KEY
}