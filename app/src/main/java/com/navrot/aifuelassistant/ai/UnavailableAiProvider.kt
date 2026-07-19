package com.navrot.aifuelassistant.ai

class UnavailableAiProvider(
    override val name: String
) : AiProvider {
    override suspend fun ask(prompt: String): String {
        throw IllegalStateException("AI provider $name is not configured")
    }
}
