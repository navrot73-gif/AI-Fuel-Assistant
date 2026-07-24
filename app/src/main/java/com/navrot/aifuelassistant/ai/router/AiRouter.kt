package com.navrot.aifuelassistant.ai.router

import android.util.Log
import com.navrot.aifuelassistant.ai.AiProvider

class AiRouter(private val providers: List<AiProvider>) {

    suspend fun ask(prompt: String): String {
        for (provider in providers) {
            val name = provider.javaClass.simpleName
            try {
                Log.d("AiRouter", "→ Пробую провайдера: $name")
                val result = provider.ask(prompt)
                Log.d("AiRouter", "✅ Успех от $name")
                return result
            } catch (e: Exception) {
                Log.e("AiRouter", "❌ $name упал: ${e.message}", e)
                // пробуем следующий провайдер
            }
        }
        throw IllegalStateException("AI providers unavailable")
    }
}