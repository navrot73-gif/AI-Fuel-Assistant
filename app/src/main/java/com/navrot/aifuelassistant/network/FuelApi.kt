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
 * Вариант маршрута OSRM.
 */
data class RouteItemResponse(
    val distance_m: Double,
    val duration_s: Double,
    val points: List<List<Double>> // [[lat, lon], [lat, lon], ...]
)

/**
 * Модель ответа Worker маршрутизации OSRM с поддержкой нескольких маршрутов.
 */
data class RouteResponse(
    val routes: List<RouteItemResponse>
) {
    // Вспомогательные свойства для обратной совместимости
    val distance_m: Double get() = routes.firstOrNull()?.distance_m ?: 0.0
    val duration_s: Double get() = routes.firstOrNull()?.duration_s ?: 0.0
    val points: List<List<Double>> get() = routes.firstOrNull()?.points ?: emptyList()
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
            val routesList = ArrayList<RouteItemResponse>()

            if (json.has("routes") && !json.isNull("routes")) {
                val routesArray = json.getJSONArray("routes")
                val limit = minOf(routesArray.length(), 3)
                for (r in 0 until limit) {
                    val routeObj = routesArray.getJSONObject(r)
                    val distM = routeObj.optDouble("distance_m", 0.0)
                    val durS = routeObj.optDouble("duration_s", 0.0)
                    val ptsArray = routeObj.optJSONArray("points")
                    val ptsList = ArrayList<List<Double>>()
                    if (ptsArray != null) {
                        for (i in 0 until ptsArray.length()) {
                            val pt = ptsArray.getJSONArray(i)
                            ptsList.add(listOf(pt.getDouble(0), pt.getDouble(1)))
                        }
                    }
                    routesList.add(RouteItemResponse(distM, durS, ptsList))
                }
            }

            if (routesList.isEmpty() && json.has("points")) {
                val distM = json.optDouble("distance_m", 0.0)
                val durS = json.optDouble("duration_s", 0.0)
                val ptsArray = json.getJSONArray("points")
                val ptsList = ArrayList<List<Double>>(ptsArray.length())
                for (i in 0 until ptsArray.length()) {
                    val pt = ptsArray.getJSONArray(i)
                    ptsList.add(listOf(pt.getDouble(0), pt.getDouble(1)))
                }
                routesList.add(RouteItemResponse(distM, durS, ptsList))
            }

            RouteResponse(routes = routesList)
        }
    }
}
