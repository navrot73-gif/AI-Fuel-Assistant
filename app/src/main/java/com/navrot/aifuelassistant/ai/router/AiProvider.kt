package com.navrot.aifuelassistant.ai.router

interface AiProvider {
    suspend fun ask(prompt: String): String
}
