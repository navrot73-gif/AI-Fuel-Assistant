package com.navrot.aifuelassistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class GigaChatAiProvider(
    private val authorizationKey: String,
    private val model: String = "GigaChat-3-Ultra",
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {

    override val name: String = "GigaChat"

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAt: Long = 0L

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }
        require(authorizationKey.isNotBlank()) { "GigaChat authorization key is not configured" }

        val token = getAccessToken()
        val requestJson = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", prompt)
                )
            )

        val request = Request.Builder()
            .url("https://api.giga.chat/v1/chat/completions")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("GigaChat request failed: HTTP ${response.code}")
            }

            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private fun getAccessToken(): String {
        val cachedToken = accessToken
        if (!cachedToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAt) {
            return cachedToken
        }

        val formBody = FormBody.Builder()
            .add("scope", "GIGACHAT_API_PERS")
            .build()

        val request = Request.Builder()
            .url("https://ngw.devices.sberbank.ru:9443/api/v2/oauth")
            .addHeader("RqUID", UUID.randomUUID().toString())
            .addHeader("Authorization", "Basic $authorizationKey")
            .addHeader("Accept", "application/json")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("GigaChat token request failed: HTTP ${response.code}")
            }

            val json = JSONObject(body)
            val newToken = json.getString("access_token")
            val expiresAtSeconds = json.optLong("expires_at", 0L)

            accessToken = newToken
            tokenExpiresAt = if (expiresAtSeconds > 0L) {
                expiresAtSeconds * 1000L - 60_000L
            } else {
                System.currentTimeMillis() + 25 * 60_000L
            }

            return newToken
        }
    }
}
