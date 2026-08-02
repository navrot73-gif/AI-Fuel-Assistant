package com.navrot.aifuelassistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class YandexGPTProvider(
    private val apiKey: String,
    private val folderId: String
) : AiProvider {

    override val name: String = "YandexGPT"

    private val client = OkHttpClient()
    private val apiUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                // Правильный формат modelUri для YandexGPT: gpt://<folder-id>/yandexgpt/latest
                put("modelUri", "gpt://$folderId/yandexgpt/latest")
                put("completionOptions", JSONObject().apply {
                    put("maxTokens", 1000)
                    put("temperature", 0.7)
                })
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("text", "Ты полезный помощник по анализу расхода топлива автомобиля. Отвечай кратко и по делу.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("text", prompt)
                    })
                })
            }

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Api-Key $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Неизвестная ошибка"
                    throw Exception("YandexGPT API error: ${response.code} - $errorBody")
                }

                val responseBody = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBody)

                jsonResponse.getJSONObject("result")
                    .getJSONArray("alternatives")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("text")
            }
        } catch (e: Exception) {
            throw Exception("Ошибка YandexGPT: ${e.message}", e)
        }
    }
}