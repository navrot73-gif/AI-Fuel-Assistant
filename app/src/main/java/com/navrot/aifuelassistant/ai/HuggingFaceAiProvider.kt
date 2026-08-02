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

class HuggingFaceAiProvider : AiProvider {
    override val name: String = "HuggingFace (Qwen 2.5)"

    private val client = OkHttpClient()
    private val modelUrl = "https://api-inference.huggingface.co/models/Qwen/Qwen2.5-72B-Instruct/v1/chat/completions"

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
                put("model", "Qwen/Qwen2.5-72B-Instruct")
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 1000)
            }

            val request = Request.Builder()
                .url(modelUrl)
                .addHeader("Authorization", "Bearer ${BuildConfig.HUGGINGFACE_TOKEN}")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Неизвестная ошибка"
                    throw Exception("HuggingFace API error: ${response.code} - $errorBody")
                }

                val responseBody = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBody)

                jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            throw Exception("Ошибка HuggingFace: ${e.message}", e)
        }
    }
}