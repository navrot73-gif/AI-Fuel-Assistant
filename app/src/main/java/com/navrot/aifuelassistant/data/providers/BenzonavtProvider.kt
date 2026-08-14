package com.navrot.aifuelassistant.data.providers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сводка цен по топливному коду из агрегатора (через Cloudflare Worker прокси).
 */
data class FuelPriceInfo(
    val median: Double,
    val min: Double,
    val max: Double,
    val sourceCount: Int,
    val updatedAt: String
)

/**
 * Источник реальных цен на топливо по городу (public endpoint прокси).
 *
 * GET {baseUrl}?city={city} → {"prices": [...], "sourcesUsed": [...], "updatedAt": "..."}
 *
 * Встроенный in-memory кеш на 30 минут. При ошибке сети возвращает пустую Map —
 * вызывающий код обязан сохранить fallback на станции из stations.json.
 */
@Singleton
class BenzonavtProvider @Inject constructor(
    private val httpClient: OkHttpClient
) {

    private class CacheEntry(
        val prices: Map<String, FuelPriceInfo>,
        val expiresAt: Long
    )

    companion object {
        private const val TAG = "BenzonavtProvider"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private const val BASE_URL = "https://ai-fuel-proxy.navrot73.workers.dev/city-prices"
    }

    @Volatile
    private var currentCity = "chelyabinsk"

    private val cache = mutableMapOf<String, CacheEntry>()
    private val mutex = Mutex()

    fun setCity(city: String) {
        val slug = city.trim().lowercase()
        if (slug.isNotBlank()) currentCity = slug
    }

    fun currentCity(): String = currentCity

    /**
     * Загружает цены для города с in-memory кешем на 30 минут.
     * При любой ошибке возвращает пустую Map (fallback остаётся в репозитории).
     */
    suspend fun fetchCityPrices(city: String = currentCity): Map<String, FuelPriceInfo> =
        withContext(Dispatchers.IO) {
            val slug = city.trim().lowercase()
            val now = System.currentTimeMillis()

            cache[slug]?.takeIf { it.expiresAt > now }?.let {
                return@withContext it.prices
            }

            val fetched = loadFromNetwork(slug)
            cache[slug] = CacheEntry(fetched, now + CACHE_TTL_MS)
            fetched
        }

    private fun loadFromNetwork(city: String): Map<String, FuelPriceInfo> {
        try {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("city", city)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AIFuelAssistant/1.0")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} для города $city")
                    return emptyMap()
                }
                val body = response.body?.string()
                    ?: return emptyMap()

                val json = JSONObject(body)
                val pricesArray = json.optJSONArray("prices")
                    ?: return emptyMap()
                val updatedAt = json.optString("updatedAt", "")

                val result = HashMap<String, FuelPriceInfo>(pricesArray.length())
                for (i in 0 until pricesArray.length()) {
                    val item = pricesArray.getJSONObject(i)
                    val code = item.optString("code")
                    if (code.isBlank()) continue
                    val sources = item.optJSONArray("sources")
                    result[code] = FuelPriceInfo(
                        median = item.optDouble("median", 0.0),
                        min = item.optDouble("min", 0.0),
                        max = item.optDouble("max", 0.0),
                        sourceCount = sources?.length() ?: 0,
                        updatedAt = updatedAt
                    )
                }
                Log.i(TAG, "loaded ${result.size} fuel types for $city")
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось загрузить цены: ${e.message}")
            return emptyMap()
        }
    }
}