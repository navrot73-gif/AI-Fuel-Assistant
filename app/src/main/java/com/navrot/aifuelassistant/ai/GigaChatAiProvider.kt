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
import java.util.Base64
import java.util.UUID

class GigaChatAiProvider(
    private val clientId: String? = null,
    private val clientSecret: String? = null,
    private val authorizationKey: String? = null,
    private val model: String = "GigaChat",
    private val httpClient: OkHttpClient = OkHttpClient()
) : AiProvider {

    override val name: String = "GigaChat"

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAt: Long = 0L

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        val token = getAccessToken()
        val requestJson = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Ты полезный помощник по анализу расхода топлива автомобиля. Отвечай кратко и по делу.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
            )

        val request = Request.Builder()
            .url("https://gigachat.devices.sberbank.ru/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("GigaChat request failed: HTTP ${response.code} - $body")
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

        // Определяем способ авторизации
        val authHeader = if (!authorizationKey.isNullOrBlank()) {
            // Если есть готовый authorizationKey (Base64)
            "Basic $authorizationKey"
        } else if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
            // Если есть clientId и clientSecret, кодируем их
            val credentials = "$clientId:$clientSecret"
            val base64Credentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
            "Basic $base64Credentials"
        } else {
            throw IllegalStateException("GigaChat credentials are not configured")
        }

        val formBody = FormBody.Builder()
            .add("scope", "GIGACHAT_API_PERS")
            .build()

        val request = Request.Builder()
            .url("https://ngw.devices.sberbank.ru:9443/api/v2/oauth")
            .addHeader("RqUID", UUID.randomUUID().toString())
            .addHeader("Authorization", authHeader)
            .addHeader("Accept", "application/json")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("GigaChat token request failed: HTTP ${response.code} - $body")
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