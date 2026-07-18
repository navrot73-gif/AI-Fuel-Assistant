
package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.model.GasStation

/**
 * Repository для работы с данными АЗС.
 * Сейчас использует локальные данные (GasStationData).
 * В будущем можно заменить на API (2ГИС, Яндекс и т.д.).
 */
object GasStationRepository {

    /** Получить все АЗС */
    fun getAllStations(): List<GasStation> = GasStationData.stations

    /** Получить АЗС по ID */
    fun getStationById(id: Long): GasStation? = GasStationData.stations.find { it.id == id }

    /** Получить АЗС рядом с координатами (в радиусе km) */
    fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double = 10.0): List<GasStation> {
        return GasStationData.getStationsNearLocation(lat, lon, radiusKm)
    }

    /** Получить АЗС по городу */
    fun getStationsByCity(city: String): List<GasStation> {
        return GasStationData.getStationsByCity(city)
    }

    /** Сортировка по цене: сначала дешёвые */
    fun getStationsSortedByPriceAsc(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> {
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            GasStationData.stations
        }
        return stations
            .filter { it.fuelTypes.any { f -> f.type == fuelType && f.available } }
            .sortedBy { it.fuelTypes.find { f -> f.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    /** Сортировка по цене: сначала дорогие */
    fun getStationsSortedByPriceDesc(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> {
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            GasStationData.stations
        }
        return stations
            .filter { it.fuelTypes.any { f -> f.type == fuelType && f.available } }
            .sortedByDescending { it.fuelTypes.find { f -> f.type == fuelType }?.price ?: 0.0 }
    }

    /** Самая дешевая АЗС для типа топлива */
    fun getCheapestStation(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): GasStation? = getStationsSortedByPriceAsc(fuelType, lat, lon, radiusKm).firstOrNull()

    /** Лучшие АЗС (цена + очередь + рейтинг) */
    fun getBestStations(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> {
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            GasStationData.stations
        }
        return stations
            .filter { it.fuelTypes.any { f -> f.type == fuelType && f.available } }
            .sortedBy { station ->
                val price = station.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE
                val queuePenalty = station.queueTime * 0.5
                val reliabilityBonus = (100 - station.reliability) * 0.2
                price + queuePenalty - reliabilityBonus
            }
    }

    /** Поиск по названию/адресу/бренду */
    fun searchStations(query: String): List<GasStation> {
        if (query.length < 2) return emptyList()
        return GasStationData.stations.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.address.contains(query, ignoreCase = true) ||
                    it.brand.contains(query, ignoreCase = true)
        }
    }

    /** Сортировка по очереди (меньше = лучше) */
    fun getStationsByQueue(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> {
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            GasStationData.stations
        }
        return stations
            .filter { it.fuelTypes.any { f -> f.type == fuelType && f.available } }
            .sortedBy { it.queueTime }
    }
}