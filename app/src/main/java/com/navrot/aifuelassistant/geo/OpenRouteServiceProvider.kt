package com.navrot.aifuelassistant.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Построение маршрутов через OpenRouteService (openrouteservice.org).
 *
 * Бесплатный API-ключ получается на https://openrouteservice.org/dev/#/signup,
 * даёт квоту, достаточную для регионального приложения на MVP-этапе.
 * Ключ хранится в local.properties (ORS_API_KEY) и прокидывается через
 * BuildConfig — по аналогии с остальными ключами в проекте.
 */
class OpenRouteServiceProvider(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val profile: String = "driving-car"
) : RoutingProvider {

    override suspend fun route(from: GeoPoint, to: GeoPoint): RouteResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) {
            "ORS_API_KEY не задан. Добавьте его в local.properties (см. local.properties.example)."
        }

        val url = "https://api.openrouteservice.org/v2/directions/$profile".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("start", "${from.longitude},${from.latitude}")
            .addQueryParameter("end", "${to.longitude},${to.latitude}")
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GeoException.NetworkError("OpenRouteService вернул код ${response.code}")
            }
            val body = response.body?.string()
                ?: throw GeoException.InvalidResponse("Пустой ответ от OpenRouteService")

            val root = JSONObject(body)
            val features = root.optJSONArray("features")
                ?: throw GeoException.InvalidResponse("Ответ без 'features': $body")
            if (features.length() == 0) {
                throw GeoException.NoResults("Маршрут не найден")
            }

            val feature = features.getJSONObject(0)
            val summary = feature.getJSONObject("properties").getJSONObject("summary")
            val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")

            val points = (0 until coordinates.length()).map { i ->
                val pair = coordinates.getJSONArray(i)
                GeoPoint(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
            }

            RouteResult(
                distanceMeters = summary.getDouble("distance"),
                durationSeconds = summary.getDouble("duration"),
                points = points
            )
        }
    }
}

interface RoutingProvider {
    suspend fun route(from: GeoPoint, to: GeoPoint): RouteResult
}
