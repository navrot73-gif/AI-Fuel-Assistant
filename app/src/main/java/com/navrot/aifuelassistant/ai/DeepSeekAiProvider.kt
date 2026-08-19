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

/**
 * DeepSeek AI-провайдер (OpenAI-compatible API).
 *
 * URL и model сделаны параметрами конструктора (с дефолтами) — это позволяет
 * в тестах подменять URL на MockWebServer без патчинга статических полей.
 *
 * Обработка ошибок дифференцирована:
 *  - 401 → [AiException.AuthError] (неверный API-ключ, ретраить бессмысленно)
 *  - 429 → [AiException.RateLimit] (превышен лимит, стоит подождать)
 *  - 5xx → [AiException.ApiError] (серверная ошибка, можно ретраить)
 *  - пустой ответ → [AiException.EmptyResponse]
 *  - сетевая ошибка → [AiException.NetworkError]
 */
class DeepSeekAiProvider(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val apiUrl: String = "https://api.deepseek.com/chat/completions",
    private val model: String = "deepseek-chat",
    private val apiKey: String = BuildConfig.DEEPSEEK_API_KEY
) : AiProvider {
    override val name: String = "DeepSeek"

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

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
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw httpError(response.code, responseBody ?: response.message)
                }

                if (responseBody.isNullOrBlank()) {
                    throw AiException.EmptyResponse("DeepSeek response body is empty")
                }

                JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException.NetworkError("DeepSeek error: ${e.message}", e)
        }
    }

    /** Преобразует HTTP status code в соответствующий тип [AiException]. */
    private fun httpError(code: Int, body: String): AiException = when (code) {
        401, 403 -> AiException.AuthError("DeepSeek auth error: $code $body")
        429 -> AiException.RateLimit("DeepSeek rate limit: $code $body")
        in 500..599 -> AiException.ApiError("DeepSeek server error: $code $body")
        else -> AiException.ApiError("DeepSeek API error: $code $body")
    }
}
