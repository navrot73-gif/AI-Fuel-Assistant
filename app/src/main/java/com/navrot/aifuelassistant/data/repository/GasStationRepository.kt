package com.navrot.aifuelassistant.data.repository

import com.navrot.aifuelassistant.data.GasStationData
import com.navrot.aifuelassistant.data.model.GasStation

/**
 * Repository для работы с данными АЗС.
 * Сейчас использует локальные данные (GasStationData).
 * В будущем можно заменить на API (2ГИС, Яндекс и т.д.).
 */
object GasStationRepository {
    fun getAllStations(): List<GasStation> = GasStationData.stations

    fun getStationById(id: Long): GasStation? = GasStationData.stations.find { it.id == id }

    fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double = 10.0): List<GasStation> =
        GasStationData.getStationsNearLocation(lat, lon, radiusKm)

    fun getStationsByCity(city: String): List<GasStation> =
        GasStationData.getStationsByCity(city)

    fun getStationsSortedByPriceAsc(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> = stationsFor(fuelType, lat, lon, radiusKm)
        .sortedBy { it.fuelTypes.find { f -> f.type == fuelType }?.price ?: Double.MAX_VALUE }

    fun getStationsSortedByPriceDesc(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> = stationsFor(fuelType, lat, lon, radiusKm)
        .sortedByDescending { it.fuelTypes.find { f -> f.type == fuelType }?.price ?: 0.0 }

    fun getCheapestStation(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): GasStation? = getStationsSortedByPriceAsc(fuelType, lat, lon, radiusKm).firstOrNull()

    fun getBestStations(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> = stationsFor(fuelType, lat, lon, radiusKm)
        .sortedBy { station ->
            val price = station.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE
            val queuePenalty = station.queueTime * 0.5
            val reliabilityBonus = (100 - station.reliability) * 0.2
            price + queuePenalty - reliabilityBonus
        }

    fun searchStations(query: String): List<GasStation> {
        if (query.length < 2) return emptyList()
        return GasStationData.stations.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.address.contains(query, ignoreCase = true) ||
                it.brand.contains(query, ignoreCase = true)
        }
    }

    fun getStationsByQueue(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> = stationsFor(fuelType, lat, lon, radiusKm)
        .sortedBy { it.queueTime }

    private fun stationsFor(
        fuelType: String,
        lat: Double?,
        lon: Double?,
        radiusKm: Double
    ): List<GasStation> {
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            GasStationData.stations
        }
        return stations.filter { station ->
            station.fuelTypes.any { fuel -> fuel.type == fuelType && fuel.available }
        }
    }
}
