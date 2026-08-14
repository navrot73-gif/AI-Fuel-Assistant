package com.navrot.aifuelassistant.ai.providers

import android.util.Log
import com.navrot.aifuelassistant.BuildConfig
import com.navrot.aifuelassistant.ai.AiException
import com.navrot.aifuelassistant.ai.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Cloudflare Worker прокси. Проксирует запрос к нескольким AI-провайдерам
 * и возвращает ответ первого успешного (fallback внутри прокси).
 *
 * POST /  body: {"prompt": "...", "context": "..."}
 * Response: {"provider": "qwen", "text": "...", "ms": 1075}
 */
class AiProxyProvider(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://ai-fuel-proxy.navrot73.workers.dev/"
) : AiProvider {

    override val name: String = "AiProxy"

    companion object {
        private const val TAG = "AiProxyProvider"
    }

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("prompt", prompt)
            .put("context", AiProvider.SYSTEM_PROMPT)
            .toString()

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("X-Proxy-Token", BuildConfig.PROXY_TOKEN)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw AiException.ApiError(
                        "AiProxy API error: ${response.code} - ${responseBody ?: response.message}"
                    )
                }
                if (responseBody.isNullOrBlank()) {
                    throw AiException.EmptyResponse("AiProxy response body is empty")
                }
                val json = JSONObject(responseBody)
                val text = json.optString("text")
                if (text.isBlank()) {
                    throw AiException.EmptyResponse("AiProxy: empty text in response")
                }
                val viaProvider = json.optString("provider", "unknown")
                val ms = json.optLong("ms", 0L)
                Log.i(TAG, "OK (${ms}ms) via $viaProvider")
                text
            }
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "network error: ${e.message}")
            throw AiException.NetworkError("AiProxy error: ${e.message}", e)
        }
    }
}