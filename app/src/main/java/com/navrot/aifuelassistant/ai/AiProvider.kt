package com.navrot.aifuelassistant.ai

/**
 * Общий интерфейс AI-провайдера.
 * Все реализации используют единый системный промпт из [SYSTEM_PROMPT].
 */
interface AiProvider {
    val name: String

    suspend fun ask(prompt: String): String

    companion object {
        /**
  * Единый системный промпт для всех AI-провайдеров.
  * Ранее дублировался в 4 файлах (DeepSeek, HuggingFace, GigaChat, YandexGPT).
  */
        const val SYSTEM_PROMPT =
            "Ты ассистент топливного приложения. Используй позицию и список АЗС пользователя. " +
            "Если просят маршрут — назови ближайшую АЗС с ценой и расстоянием. " +
            "Отвечай кратко и по делу."
    }
}
