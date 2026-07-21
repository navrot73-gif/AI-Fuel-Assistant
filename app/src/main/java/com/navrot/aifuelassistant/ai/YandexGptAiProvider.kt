package com.navrot.aifuelassistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class YandexGptAiProvider(
    private val apiKey: String,
    private val folderId: String,
    private val model: String = "yandexgpt/latest",
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {

    override val name: String = "YandexGPT"

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }
        require(apiKey.isNotBlank()) { "Yandex API key is not configured" }
        require(folderId.isNotBlank()) { "Yandex folder ID is not configured" }

        val requestJson = JSONObject()
            .put("modelUri", "gpt://$folderId/$model")
            .put(
                "completionOptions",
                JSONObject()
                    .put("stream", false)
                    .put("temperature", 0.2)
                    .put("maxTokens", 2000)
            )
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("text", prompt)
                )
            )

        val request = Request.Builder()
            .url("https://llm.api.cloud.yandex.net/foundationModels/v1/completion")
            .addHeader("Authorization", "Api-Key $apiKey")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("YandexGPT request failed: HTTP ${response.code}")
            }

            JSONObject(body)
                .getJSONObject("result")
                .getJSONArray("alternatives")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("text")
        }
    }
}
