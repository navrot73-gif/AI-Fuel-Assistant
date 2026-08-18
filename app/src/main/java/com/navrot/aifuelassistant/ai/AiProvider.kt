package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.features.dashboard.ChatMessage

/**
 * Общий интерфейс AI-провайдера.
 * Все реализации используют единый системный промпт из [SYSTEM_PROMPT].
 */
interface AiProvider {
    val name: String

    suspend fun ask(prompt: String): String

    // Overload with history support
    suspend fun ask(prompt: String, history: List<ChatMessage>): String = ask(prompt)

    companion object {
        /**
   * Единый системный промпт для всех AI-провайдеров.
   * Ранее дублировался в 4 файлах (DeepSeek, HuggingFace, GigaChat, YandexGPT).
   */
        const val SYSTEM_PROMPT =
            "Ты ассистент топливного приложения. Ты работаешь ВНУТРИ приложения. " +
            "НИКОГДА не предлагай Google Maps, Яндекс, 2ГИС и внешние сервисы. Маршрут строится автоматически на карте приложения. " +
            "Используй позицию и список АЗС пользователя. " +
            "Если просят маршрут — назови ближайшую АЗС с ценой и расстоянием. " +
            "Отвечай кратко и по делу."
    }
}
