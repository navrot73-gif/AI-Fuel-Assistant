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

class DeepSeekAiProvider(
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {
    override val name: String = "DeepSeek"

    private val apiUrl = "https://api.deepseek.com/chat/completions"
    private val model = "deepseek-chat"

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", AiProvider.SYSTEM_PROMPT)
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

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw Exception("DeepSeek API error: ${response.code} ${responseBody ?: response.message}")
                }

                if (responseBody.isNullOrBlank()) {
                    throw Exception("DeepSeek response body is empty")
                }

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
