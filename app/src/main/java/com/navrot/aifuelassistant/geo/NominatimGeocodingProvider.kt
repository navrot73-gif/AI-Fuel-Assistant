package com.navrot.aifuelassistant.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Геокодинг через публичный Nominatim (nominatim.openstreetmap.org).
 *
 * Важно: это бесплатный публичный сервис с лимитом ~1 запрос/сек и
 * обязательным указанием User-Agent приложения (требование политики OSM,
 * см. https://operations.osmfoundation.org/policies/nominatim/).
 * Не использовать для массовых/пакетных запросов без своего инстанса.
 *
 * Результаты по умолчанию ограничены Челябинской областью через viewbox,
 * чтобы повысить релевантность и снизить число ложных совпадений с
 * одноимёнными объектами в других регионах.
 */
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class NominatimGeocodingProvider(
    private val userAgent: String = "AIFuelAssistant/1.0 (Android; contact: navrot73-gif)",
    private val httpClient: OkHttpClient = OkHttpClient(),
    // Приблизительный bounding box Челябинской области (lon_min, lat_min, lon_max, lat_max)
    private val regionViewbox: String = "57.0,52.9,63.5,56.4"
) : GeocodingProvider {

    private data class CacheEntry(
        val result: GeocodingResult,
        val timestamp: Long
    )

    private val reverseCache = ConcurrentHashMap<String, CacheEntry>()

    companion object {
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 mins
    }

    override suspend fun geocode(query: String): GeocodingResult = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Query must not be blank" }

        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("viewbox", regionViewbox)
            .addQueryParameter("bounded", "1")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GeoException.NetworkError("Nominatim вернул код ${response.code}")
            }
            val body = response.body?.string()
                ?: throw GeoException.InvalidResponse("Пустой ответ от Nominatim")

            val results = JSONArray(body)
            if (results.length() == 0) {
                throw GeoException.NoResults("Ничего не найдено по запросу: $query")
            }

            val first = results.getJSONObject(0)
            GeocodingResult(
                point = GeoPoint(
                    latitude = first.getString("lat").toDouble(),
                    longitude = first.getString("lon").toDouble()
                ),
                displayName = first.optString("display_name", query)
            )
        }
    }

    override suspend fun reverseGeocode(lat: Double, lon: Double): GeocodingResult = withContext(Dispatchers.IO) {
        // Округляем до ~1км (2 знака после запятой) для кэш-ключа
        val roundedLat = (lat * 100).roundToInt() / 100.0
        val roundedLon = (lon * 100).roundToInt() / 100.0
        val cacheKey = "$roundedLat,$roundedLon"

        val now = System.currentTimeMillis()
        reverseCache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL_MS) {
                return@withContext entry.result
            }
        }

        val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lon.toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("zoom", "10") // уровень города
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GeoException.NetworkError("Nominatim reverse вернул код ${response.code}")
            }
            val body = response.body?.string()
                ?: throw GeoException.InvalidResponse("Пустой ответ от Nominatim reverse")

            val json = JSONObject(body)
            val addressObj = json.optJSONObject("address")
            val cityName = addressObj?.optString("city")
                ?.takeIf { it.isNotBlank() }
                ?: addressObj?.optString("town")?.takeIf { it.isNotBlank() }
                ?: addressObj?.optString("village")?.takeIf { it.isNotBlank() }
                ?: addressObj?.optString("county")?.takeIf { it.isNotBlank() }
                ?: json.optString("display_name", "Неизвестная локация")

            val result = GeocodingResult(
                point = GeoPoint(
                    latitude = json.getString("lat").toDouble(),
                    longitude = json.getString("lon").toDouble()
                ),
                displayName = cityName
            )
            reverseCache[cacheKey] = CacheEntry(result, now)
            result
        }
    }
}

interface GeocodingProvider {
    suspend fun geocode(query: String): GeocodingResult
    suspend fun reverseGeocode(lat: Double, lon: Double): GeocodingResult
}