package com.navrot.aifuelassistant.ai

interface AiProvider {
    val name: String

    suspend fun ask(prompt: String): String
}
