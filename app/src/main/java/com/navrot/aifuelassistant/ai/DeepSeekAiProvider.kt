package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class DeepSeekAiProvider : AiProvider {
    override val name: String = "DeepSeek"

    private val client = OkHttpClient()
    private val apiUrl = "https://api.deepseek.com/chat/completions"
    private val model = "deepseek-chat"

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Ты полезный помощник по анализу расхода топлива автомобиля. Отвечай кратко и по делу.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val jsonBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("DeepSeek API error: ${response.code} ${response.message}")
                }

                val responseBody = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBody)

                jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            throw Exception("Ошибка DeepSeek: ${e.message}", e)
        }
    }
}