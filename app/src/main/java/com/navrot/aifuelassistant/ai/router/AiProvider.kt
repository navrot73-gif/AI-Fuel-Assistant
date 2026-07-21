package com.navrot.aifuelassistant.ai.router

interface AIProvider {
    val name: String

    suspend fun ask(prompt: String): String
}
