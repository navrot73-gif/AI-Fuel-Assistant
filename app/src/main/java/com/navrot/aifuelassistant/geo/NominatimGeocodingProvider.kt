package com.navrot.aifuelassistant.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

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
class NominatimGeocodingProvider(
    private val userAgent: String = "AIFuelAssistant/1.0 (Android; contact: navrot73-gif)",
    private val httpClient: OkHttpClient = OkHttpClient(),
    // Приблизительный bounding box Челябинской области (lon_min, lat_min, lon_max, lat_max)
    private val regionViewbox: String = "57.0,52.9,63.5,56.4"
) : GeocodingProvider {

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
}

interface GeocodingProvider {
    suspend fun geocode(query: String): GeocodingResult
}
