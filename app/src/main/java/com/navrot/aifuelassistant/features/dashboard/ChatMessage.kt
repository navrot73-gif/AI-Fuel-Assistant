package com.navrot.aifuelassistant.features.dashboard

import kotlinx.serialization.Serializable

/**
 * Сообщение чата для истории диалога с AI.
 * role: "user" | "ai" (при отправке в прокси "ai" → "assistant")
 */
@Serializable
data class ChatMessage(
    val role: String,  // "user" или "ai"
    val text: String,
    val ts: Long = System.currentTimeMillis()
) {
    /**
     * Преобразует роль для API: "ai" → "assistant"
     */
    fun apiRole(): String = if (role == "ai") "assistant" else role
}