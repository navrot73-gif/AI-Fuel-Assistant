package com.navrot.aifuelassistant.ai

import android.util.Log
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
 * Qwen (Alibaba Cloud DashScope) — OpenAI-compatible API.
 * Модели: qwen-plus (быстрый), qwen-max (умный), qwen-turbo (дешёвый).
 */
class QwenAiProvider(
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {
    override val name: String = "Qwen"

    companion object {
        private const val TAG = "QwenAiProvider"

        // Международный endpoint (рекомендуется для РФ)
        // Для ключей из Китая: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
        const val BASE_URL = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions"
        const val MODEL = "qwen-plus"
    }

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
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.QWEN_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Qwen API error: ${response.code} ${responseBody ?: response.message}")
                    throw AiException.ApiError("Qwen API error: ${response.code}")
                }

                if (responseBody.isNullOrBlank()) {
                    throw AiException.EmptyResponse("Qwen response body is empty")
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
            Log.w(TAG, "Qwen network error: ${e.message}")
            throw AiException.NetworkError("Qwen error: ${e.message}", e)
        }
    }
}