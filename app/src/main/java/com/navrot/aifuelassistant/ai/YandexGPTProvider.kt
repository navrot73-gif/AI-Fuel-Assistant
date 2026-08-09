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
    private val folderId: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {

    override val name: String = "YandexGPT"

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
                        put("text", AiProvider.SYSTEM_PROMPT)
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

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw AiException.ApiError("YandexGPT API error: ${response.code} - ${responseBody ?: "Неизвестная ошибка"}")
                }

                if (responseBody.isNullOrBlank()) {
                    throw AiException.EmptyResponse("YandexGPT response body is empty")
                }

                val jsonResponse = JSONObject(responseBody)

                jsonResponse.getJSONObject("result")
                    .getJSONArray("alternatives")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("text")
            }
        } catch (e: Exception) {
            throw AiException.NetworkError("YandexGPT error: ${e.message}", e)
        }
    }
}
