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

class YandexGPTProvider(
    private val apiKey: String,
    private val folderId: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {

    override val id: String = "yandexgpt"
    override val name: String = "YandexGPT"

    private val apiUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

    override suspend fun ask(prompt: String, systemPrompt: String): String = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        val folder = folderId.trim()

        if (key.isBlank() || folder.isBlank()) {
            throw AiException.NetworkError("YandexGPT error: API key or folder ID is empty")
        }

        // 🔒 Защита: вырезаем кириллицу и невидимые символы из ключа
        val sanitizedApiKey = key.filter { it.code in 33..126 }
        val authValue = "Api-Key $sanitizedApiKey"
        require(authValue.all { it.code < 128 }) {
            "YandexGPT error: Authorization header contains non-ASCII characters"
        }

        try {
            val jsonBody = JSONObject().apply {
                put("modelUri", "gpt://$folder/yandexgpt/latest")
                put("completionOptions", JSONObject().apply {
                    put("stream", false)
                    put("maxTokens", 2000)
                    put("temperature", 0.6)
                })
                put("messages", JSONArray().apply {
                    if (systemPrompt.isNotBlank()) {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("text", systemPrompt)
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("text", prompt)
                    })
                })
            }

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", authValue)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-folder-id", folder)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw AiException.ApiError("YandexGPT API error: ${response.code} - $responseBody")
                }
                parseResponse(responseBody)
            }
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException.NetworkError("YandexGPT error: ${e.message}", e)
        }
    }

    private fun parseResponse(json: String): String {
        return try {
            val obj = JSONObject(json)
            val alternatives = obj.optJSONObject("result")?.optJSONArray("alternatives")
            val firstMessage = alternatives?.optJSONObject(0)?.optJSONObject("message")
            firstMessage?.optString("text", "")
                ?: throw AiException.ApiError("YandexGPT: empty response")
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException.ApiError("YandexGPT parse error: ${e.message}")
        }
    }
}