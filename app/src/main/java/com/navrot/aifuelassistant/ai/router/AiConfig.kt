package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.BuildConfig

object AiConfig {
    val deepSeekApiKey: String get() = BuildConfig.DEEPSEEK_API_KEY

    // Используем HuggingFace токен для доступа к Qwen
    val huggingFaceToken: String get() = BuildConfig.HUGGINGFACE_TOKEN

    val gigaChatClientId: String get() = BuildConfig.GIGACHAT_CLIENT_ID
    val gigaChatClientSecret: String get() = BuildConfig.GIGACHAT_CLIENT_SECRET

    val yandexApiKey: String get() = BuildConfig.YANDEX_API_KEY
    val yandexFolderId: String get() = BuildConfig.YANDEX_FOLDER_ID
}