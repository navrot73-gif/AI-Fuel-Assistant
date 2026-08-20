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
 * Элемент одного варианта маршрута.
 */
data class RouteOptionData(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val points: List<List<Double>> // [[lat, lon], [lat, lon], ...]
)

/**
 * Модель ответа Worker маршрутизации OSRM.
 * Поддерживает как одиночный ответ (distance_m, duration_s, points),
 * так и список вариантов (routes).
 */
data class RouteResponse(
    val distance_m: Double,
    val duration_s: Double,
    val points: List<List<Double>>, // [[lat, lon], [lat, lon], ...]
    val routes: List<RouteOptionData> = emptyList()
) {
    /**
     * Возвращает до 3 вариантов маршрута.
     */
    fun getRouteOptions(): List<RouteOptionData> {
        return if (routes.isNotEmpty()) {
            routes.take(3)
        } else if (points.isNotEmpty()) {
            listOf(RouteOptionData(distance_m, duration_s, points))
        } else {
            emptyList()
        }
    }
}

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
            .addQueryParameter("alternatives", "true")
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
            val parsedRoutes = mutableListOf<RouteOptionData>()

            if (json.has("routes") && !json.isNull("routes")) {
                val routesArray = json.getJSONArray("routes")
                val count = minOf(routesArray.length(), 3)
                for (i in 0 until count) {
                    val routeObj = routesArray.getJSONObject(i)
                    val dist = if (routeObj.has("distance_m")) routeObj.getDouble("distance_m") else routeObj.optDouble("distance", 0.0)
                    val dur = if (routeObj.has("duration_s")) routeObj.getDouble("duration_s") else routeObj.optDouble("duration", 0.0)
                    val pointsArray = routeObj.getJSONArray("points")
                    val pointsList = ArrayList<List<Double>>(pointsArray.length())
                    for (j in 0 until pointsArray.length()) {
                        val pt = pointsArray.getJSONArray(j)
                        pointsList.add(listOf(pt.getDouble(0), pt.getDouble(1)))
                    }
                    parsedRoutes.add(RouteOptionData(dist, dur, pointsList))
                }
            }

            val mainDistance = if (parsedRoutes.isNotEmpty()) parsedRoutes[0].distanceMeters
                else if (json.has("distance_m")) json.getDouble("distance_m") else 0.0
            val mainDuration = if (parsedRoutes.isNotEmpty()) parsedRoutes[0].durationSeconds
                else if (json.has("duration_s")) json.getDouble("duration_s") else 0.0
            val mainPoints = if (parsedRoutes.isNotEmpty()) parsedRoutes[0].points
                else if (json.has("points")) {
                    val pointsArray = json.getJSONArray("points")
                    val list = ArrayList<List<Double>>(pointsArray.length())
                    for (i in 0 until pointsArray.length()) {
                        val pt = pointsArray.getJSONArray(i)
                        list.add(listOf(pt.getDouble(0), pt.getDouble(1)))
                    }
                    list
                } else emptyList()

            val finalRoutes = if (parsedRoutes.isNotEmpty()) {
                parsedRoutes
            } else if (mainPoints.isNotEmpty()) {
                listOf(RouteOptionData(mainDistance, mainDuration, mainPoints))
            } else {
                emptyList()
            }

            RouteResponse(
                distance_m = mainDistance,
                duration_s = mainDuration,
                points = mainPoints,
                routes = finalRoutes
            )
        }
    }
}
