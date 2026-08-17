package com.navrot.aifuelassistant.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Модель ответа Worker маршрутизации OSRM.
 */
data class RouteResponse(
    val distance_m: Double,
    val duration_s: Double,
    val points: List<List<Double>> // [[lat, lon], [lat, lon], ...]
)

/**
 * Интерфейс API для сервисов AI Fuel Assistant.
 */
interface FuelApi {
    suspend fun getRoute(fromLon: Double, fromLat: Double, toLon: Double, toLat: Double): RouteResponse
}

/**
 * Реализация FuelApi через OkHttpClient с таймаутом 3 секунды.
 */
@Singleton
class FuelApiImpl @Inject constructor(
    private val httpClient: OkHttpClient
) : FuelApi {

    companion object {
        private const val BASE_URL = "https://ai-fuel-proxy.navrot73.workers.dev/route"
    }

    private val routeClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun getRoute(
        fromLon: Double,
        fromLat: Double,
        toLon: Double,
        toLat: Double
    ): RouteResponse = withContext(Dispatchers.IO) {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("from", "$fromLon,$fromLat")
            .addQueryParameter("to", "$toLon,$toLat")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "AIFuelAssistant/1.0")
            .header("Accept", "application/json")
            .build()

        routeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} от Worker route")
            }
            val body = response.body?.string()
                ?: throw Exception("Пустой ответ от Worker route")

            val json = JSONObject(body)
            val distanceM = json.getDouble("distance_m")
            val durationS = json.getDouble("duration_s")
            val pointsArray = json.getJSONArray("points")
            val pointsList = ArrayList<List<Double>>(pointsArray.length())
            for (i in 0 until pointsArray.length()) {
                val pt = pointsArray.getJSONArray(i)
                pointsList.add(listOf(pt.getDouble(0), pt.getDouble(1)))
            }
            RouteResponse(
                distance_m = distanceM,
                duration_s = durationS,
                points = pointsList
            )
        }
    }
}
