package com.navrot.aifuelassistant.ai.router

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class DeepSeekProvider : AiProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun ask(prompt: String): String {
        val apiKey = AiConfig.deepSeekApiKey
        if (apiKey.isBlank()) {
            throw IllegalStateException("DeepSeek API key не задан")
        }

        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", prompt)
        )

        val bodyJson = JSONObject()
            .put("model", "deepseek-chat")
            .put("messages", messages)
            .put("temperature", 0.7)

        val body = bodyJson.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            continuation.resumeWithException(
                                IOException("DeepSeek error: ${it.code} ${it.message}")
                            )
                            return
                        }
                        val responseBody = it.body?.string()
                            ?: throw IOException("Пустой ответ DeepSeek")

                        val json = JSONObject(responseBody)
                        val answer = json
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")

                        continuation.resume(answer)
                    }
                }
            })
        }
    }
}