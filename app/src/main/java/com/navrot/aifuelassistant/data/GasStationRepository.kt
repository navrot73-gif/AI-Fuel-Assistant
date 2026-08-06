package com.navrot.aifuelassistant.data

import android.content.Context
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий АЗС.
 *
 * Приоритет загрузки данных:
 *  1) удалённый stations.json (GitHub raw) — цены обновляются без релиза приложения;
 *  2) локальный кеш последнего успешного ответа;
 *  3) assets/stations.json — офлайн-фолбэк.
 *
 * Управляется Hilt (синглтон), потокобезопасен через [Mutex].
 */
@Singleton
class GasStationRepository @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/navrot73-gif/AI-Fuel-Assistant/main/app/src/main/assets/stations.json"
        private const val CACHE_FILE = "stations_cache.json"

        // Не дёргаем сеть чаще, чем раз в 10 минут
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val loadMutex = Mutex()
    private var cachedStations: List<GasStation>? = null
    private var lastRemoteCheckMs = 0L

    private suspend fun ensureLoaded(): List<GasStation> = loadMutex.withLock {
        cachedStations?.let { return@withLock it }

        val now = System.currentTimeMillis()
        val stations: List<GasStation> =
            if (now - lastRemoteCheckMs >= REFRESH_INTERVAL_MS) {
                lastRemoteCheckMs = now
                loadFromRemote() ?: loadFromCache() ?: loadFromAssets()
            } else {
                loadFromCache() ?: loadFromAssets()
            }

        cachedStations = stations
        stations
    }

    /** Принудительное обновление из сети (пригодится для pull-to-refresh). */
    suspend fun refresh(): List<GasStation> = loadMutex.withLock {
        lastRemoteCheckMs = System.currentTimeMillis()
        val stations = loadFromRemote() ?: loadFromCache() ?: loadFromAssets()
        cachedStations = stations
        stations
    }

    // ==================== ПУБЛИЧНЫЕ МЕТОДЫ (без изменений) ====================

    suspend fun getAllStations(): List<GasStation> = withContext(Dispatchers.IO) {
        ensureLoaded()
    }

    suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        getStationsNearLocation(lat, lon, radiusKm, stations)
    }

    suspend fun getStationsByCity(city: String): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        stations.filter { it.address.contains(city, ignoreCase = true) }
    }

    suspend fun searchStations(query: String): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val q = query.lowercase()
        stations.filter {
            it.name.lowercase().contains(q) ||
                    it.brand.lowercase().contains(q) ||
                    it.address.lowercase().contains(q)
        }
    }

    suspend fun getBestStations(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s ->
                val fuel = s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE
                val queuePenalty = s.queueTime * 0.5
                val reliabilityBonus = (100 - s.reliability) * 0.2
                fuel + queuePenalty - reliabilityBonus
            }
    }

    suspend fun getCheapestStation(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): GasStation? = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby
            .filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .minByOrNull { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    suspend fun getStationsSortedByPriceAsc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    suspend fun getStationsSortedByPriceDesc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedByDescending { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    suspend fun getStationsByQueue(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { it.queueTime }
    }

    // ==================== ЗАГРУЗКА ДАННЫХ ====================

    private fun getStationsNearLocation(lat: Double, lon: Double, radiusKm: Double, stations: List<GasStation>): List<GasStation> {
        return stations.filter { station ->
            val distance = GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
            distance <= radiusKm
        }.sortedBy { station ->
            GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
        }
    }

    /** 1) Сеть: GitHub raw. При успехе — сохраняем копию в кеш. */
    private suspend fun loadFromRemote(): List<GasStation>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(REMOTE_URL).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val stations = parseJson(body)
                if (stations.isNotEmpty()) {
                    cacheFile().writeText(body)
                }
                stations.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 2) Локальный кеш последнего успешного ответа из сети. */
    private suspend fun loadFromCache(): List<GasStation>? = withContext(Dispatchers.IO) {
        try {
            val file = cacheFile()
            if (!file.exists()) return@withContext null
            parseJson(file.readText()).takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /** 3) Офлайн-фолбэк из assets. */
    private fun loadFromAssets(): List<GasStation> {
        val jsonString = context.assets.open("stations.json").bufferedReader().use { it.readText() }
        return parseJson(jsonString)
    }

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE)

    private fun parseJson(jsonString: String): List<GasStation> {
        val jsonArray = JSONArray(jsonString)
        return (0 until jsonArray.length()).map { i ->
            parseStation(jsonArray.getJSONObject(i))
        }
    }

    private fun parseStation(json: JSONObject): GasStation {
        val fuelArray = json.getJSONArray("fuelTypes")
        val fuelTypes = (0 until fuelArray.length()).map { j ->
            val fuel = fuelArray.getJSONObject(j)
            FuelPrice(
                type = fuel.getString("type"),
                price = fuel.getDouble("price"),
                available = fuel.getBoolean("available")
            )
        }
        return GasStation(
            id = json.getInt("id"),
            name = json.getString("name"),
            brand = json.getString("brand"),
            address = json.getString("address"),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            fuelTypes = fuelTypes,
            queueTime = json.getInt("queueTime"),
            reliability = json.getInt("reliability")
        )
    }
}
