package com.navrot.aifuelassistant.data

import android.content.Context
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GasStationRepository {

    private lateinit var context: Context
    private var cachedStations: List<GasStation>? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    private fun ensureLoaded(): List<GasStation> {
        return cachedStations ?: loadFromAssets().also { cachedStations = it }
    }

    suspend fun getAllStations(): List<GasStation> = withContext(Dispatchers.IO) {
        ensureLoaded()
    }

    fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double): List<GasStation> {
        val stations = ensureLoaded()
        return getStationsNearLocation(lat, lon, radiusKm, stations)
    }

    fun getStationsByCity(city: String): List<GasStation> {
        val stations = ensureLoaded()
        return stations.filter { it.address.contains(city, ignoreCase = true) }
    }

    fun searchStations(query: String): List<GasStation> {
        val stations = ensureLoaded()
        val q = query.lowercase()
        return stations.filter {
            it.name.lowercase().contains(q) ||
                    it.brand.lowercase().contains(q) ||
                    it.address.lowercase().contains(q)
        }
    }

    fun getBestStations(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        return nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s ->
                val fuel = s.fuelTypes.find { it.type == fuelType }!!
                val queuePenalty = s.queueTime * 0.5
                val reliabilityBonus = (100 - s.reliability) * 0.2
                fuel.price + queuePenalty - reliabilityBonus
            }
    }

    fun getCheapestStation(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): GasStation? {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        return nearby
            .filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .minByOrNull { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    fun getStationsSortedByPriceAsc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        return nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    fun getStationsSortedByPriceDesc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        return nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedByDescending { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    fun getStationsByQueue(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        return nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { it.queueTime }
    }

    fun getStationsNearLocation(lat: Double, lon: Double, radiusKm: Double, stations: List<GasStation>): List<GasStation> {
        return stations.filter { station ->
            val distance = calculateDistance(lat, lon, station.latitude, station.longitude)
            distance <= radiusKm
        }.sortedBy { station ->
            calculateDistance(lat, lon, station.latitude, station.longitude)
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

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}