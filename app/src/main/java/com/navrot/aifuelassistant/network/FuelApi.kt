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
    suspend fun getRoute(
        fromLon: Double,
        fromLat: Double,
        toLon: Double,
        toLat: Double,
        alternatives: Boolean = true
    ): Result<RouteResponse>
}

/**
 * Реализация FuelApi через OkHttpClient с таймаутом 15 секунд.
 */
@Singleton
class FuelApiImpl internal constructor(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = PRIMARY_OSRM_URL,
    private val secondaryBaseUrl: String = SECONDARY_OSRM_URL
) : FuelApi {

    @Inject
    constructor(httpClient: OkHttpClient) : this(
        httpClient,
        PRIMARY_OSRM_URL,
        SECONDARY_OSRM_URL
    )

    companion object {
        private const val PRIMARY_OSRM_URL = "https://router.project-osrm.org/route/v1/driving/"
        private const val SECONDARY_OSRM_URL = "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
    }

    private val routeClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRequestUrl(
        endpointUrl: String,
        fromLon: Double,
        fromLat: Double,
        toLon: Double,
        toLat: Double,
        alternatives: Boolean
    ): String {
        return if (endpointUrl.contains("workers.dev") || endpointUrl.endsWith("/route")) {
            endpointUrl.toHttpUrl().newBuilder()
                .addQueryParameter("from", "$fromLon,$fromLat")
                .addQueryParameter("to", "$toLon,$toLat")
                .addQueryParameter("alternatives", alternatives.toString())
                .build().toString()
        } else {
            val base = if (endpointUrl.endsWith("/")) endpointUrl else "$endpointUrl/"
            "${base}$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson&alternatives=$alternatives"
        }
    }

    private fun fetchRouteFromUrl(url: String): RouteResponse {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "AIFuelAssistant/1.0")
            .header("Accept", "application/json")
            .build()

        return routeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} from routing endpoint")
            }
            val body = response.body?.string()
                ?: throw Exception("Empty routing response body")
            parseRouteResponse(body)
        }
    }

    fun parseRouteResponse(body: String): RouteResponse {
        val json = JSONObject(body)
        val parsedRoutes = mutableListOf<RouteOptionData>()

        if (json.has("routes") && !json.isNull("routes")) {
            val routesArray = json.getJSONArray("routes")
            val count = minOf(routesArray.length(), 3)
            for (i in 0 until count) {
                val routeObj = routesArray.getJSONObject(i)
                val dist = if (routeObj.has("distance_m")) routeObj.getDouble("distance_m") else routeObj.optDouble("distance", 0.0)
                val dur = if (routeObj.has("duration_s")) routeObj.getDouble("duration_s") else routeObj.optDouble("duration", 0.0)

                val pointsList = ArrayList<List<Double>>()
                if (routeObj.has("points") && !routeObj.isNull("points")) {
                    val pointsArray = routeObj.getJSONArray("points")
                    for (j in 0 until pointsArray.length()) {
                        val pt = pointsArray.getJSONArray(j)
                        pointsList.add(listOf(pt.getDouble(0), pt.getDouble(1)))
                    }
                } else if (routeObj.has("geometry") && !routeObj.isNull("geometry")) {
                    val geomObj = routeObj.getJSONObject("geometry")
                    if (geomObj.has("coordinates")) {
                        val coordsArray = geomObj.getJSONArray("coordinates")
                        for (j in 0 until coordsArray.length()) {
                            val pt = coordsArray.getJSONArray(j)
                            // OSRM GeoJSON geometry coordinates are [lon, lat] -> convert to [lat, lon]
                            pointsList.add(listOf(pt.getDouble(1), pt.getDouble(0)))
                        }
                    }
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

        return RouteResponse(
            distance_m = mainDistance,
            duration_s = mainDuration,
            points = mainPoints,
            routes = finalRoutes
        )
    }

    override suspend fun getRoute(
        fromLon: Double,
        fromLat: Double,
        toLon: Double,
        toLat: Double,
        alternatives: Boolean
    ): Result<RouteResponse> = withContext(Dispatchers.IO) {
        val mirror1Url = buildRequestUrl(baseUrl, fromLon, fromLat, toLon, toLat, alternatives)
        val mirror2Url = buildRequestUrl(secondaryBaseUrl, fromLon, fromLat, toLon, toLat, alternatives)

        // Attempt 1: Mirror 1
        var lastException: Exception? = null
        try {
            return@withContext Result.success(fetchRouteFromUrl(mirror1Url))
        } catch (e: Exception) {
            lastException = e
            timber.log.Timber.tag("FuelApiImpl").w(e, "Mirror 1 request failed, retrying Mirror 1 once...")
        }

        // Retry 1: Mirror 1
        try {
            return@withContext Result.success(fetchRouteFromUrl(mirror1Url))
        } catch (e: Exception) {
            lastException = e
            timber.log.Timber.tag("FuelApiImpl").w(e, "Mirror 1 retry failed, switching to Mirror 2...")
        }

        // Attempt 1: Mirror 2
        try {
            return@withContext Result.success(fetchRouteFromUrl(mirror2Url))
        } catch (e: Exception) {
            lastException = e
            timber.log.Timber.tag("FuelApiImpl").w(e, "Mirror 2 request failed as well")
        }

        Result.failure(lastException ?: Exception("Routing failed on all mirrors"))
    }
}
