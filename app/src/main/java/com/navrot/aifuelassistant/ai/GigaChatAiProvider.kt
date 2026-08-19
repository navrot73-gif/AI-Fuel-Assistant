package com.navrot.aifuelassistant.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val clientId: String? = null,
    private val clientSecret: String? = null,
    private val authorizationKey: String? = null,
    private val model: String = "GigaChat",
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val chatUrl: String = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
    private val oauthUrl: String = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
) : AiProvider {

    override val name: String = "GigaChat"

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAt: Long = 0L

    // Мьютекс предотвращает одновременное обновление токена из нескольких корутин
    private val tokenMutex = Mutex()

    override suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        try {
            val token = getAccessToken()
            val requestJson = JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", AiProvider.SYSTEM_PROMPT)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                )

            val request = Request.Builder()
                .url(chatUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    throw httpError(response.code, body.orEmpty(), "GigaChat request")
                }

                if (body.isNullOrBlank()) {
                    throw AiException.EmptyResponse("GigaChat response body is empty")
                }

                JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException.NetworkError("GigaChat error: ${e.message}", e)
        }
    }

    private suspend fun getAccessToken(): String = tokenMutex.withLock {
        val cachedToken = accessToken
        if (!cachedToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAt) {
            return@withLock cachedToken
        }

        // Определяем способ авторизации
        val authHeader = if (!authorizationKey.isNullOrBlank()) {
            // Если есть готовый authorizationKey (Base64)
            "Basic $authorizationKey"
        } else if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
            // Если есть clientId и clientSecret, кодируем их
            val credentials = "$clientId:$clientSecret"
            val base64Credentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            "Basic $base64Credentials"
        } else {
            throw AiException.AuthError("GigaChat credentials are not configured")
        }

        val formBody = FormBody.Builder()
            .add("scope", "GIGACHAT_API_PERS")
            .build()

        val request = Request.Builder()
            .url(oauthUrl)
            .addHeader("RqUID", UUID.randomUUID().toString())
            .addHeader("Authorization", authHeader)
            .addHeader("Accept", "application/json")
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw httpError(response.code, body.orEmpty(), "GigaChat token request")
            }

            if (body.isNullOrBlank()) {
                throw AiException.EmptyResponse("GigaChat token response body is empty")
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

            newToken
        }
    }

    /** Преобразует HTTP status code в соответствующий тип [AiException]. */
    private fun httpError(code: Int, body: String, context: String): AiException = when (code) {
        401, 403 -> AiException.AuthError("$context auth error: $code $body")
        429 -> AiException.RateLimit("$context rate limit: $code $body")
        in 500..599 -> AiException.ApiError("$context server error: $code $body")
        else -> AiException.ApiError("$context failed: HTTP $code $body")
    }
}
