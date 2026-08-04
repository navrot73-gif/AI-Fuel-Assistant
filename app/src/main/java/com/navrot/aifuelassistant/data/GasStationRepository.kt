package com.navrot.aifuelassistant.data

import android.content.Context
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий АЗС, загружающий данные из assets/stations.json.
 *
 * Управляется Hilt (синглтон), потокобезопасен через [Mutex].
 */
@Singleton
class GasStationRepository @Inject constructor(
    private val context: Context
) {

    private val loadMutex = Mutex()
    private var cachedStations: List<GasStation>? = null

    private suspend fun ensureLoaded(): List<GasStation> = loadMutex.withLock {
        cachedStations ?: loadFromAssets().also { cachedStations = it }
    }

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

    private fun getStationsNearLocation(lat: Double, lon: Double, radiusKm: Double, stations: List<GasStation>): List<GasStation> {
        return stations.filter { station ->
            val distance = GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
            distance <= radiusKm
        }.sortedBy { station ->
            GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
        }
    }

    private fun loadFromAssets(): List<GasStation> {
        val jsonString = context.assets.open("stations.json").bufferedReader().use { it.readText() }
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