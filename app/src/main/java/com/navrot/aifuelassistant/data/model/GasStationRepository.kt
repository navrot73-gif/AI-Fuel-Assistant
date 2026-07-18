
package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.model.GasStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository для работы с данными АЗС.
 * Сейчас использует локальные данные (GasStationData).
 * В будущем можно заменить на API (2ГИС, Яндекс и т.д.).
 */
object GasStationRepository {

    // Кэш для избежания повторной фильтрации большого списка
    private val _cachedStations = MutableStateFlow<List<GasStation>>(emptyList())
    val cachedStations: StateFlow<List<GasStation>> = _cachedStations.asStateFlow()

    private var lastFilterKey: String? = null
    private var lastFilterResult: List<GasStation>? = null

    /** Получить все АЗС */
    fun getAllStations(): List<GasStation> {
        if (_cachedStations.value.isEmpty()) {
            _cachedStations.value = GasStationData.stations
        }
        return _cachedStations.value
    }

    /** Получить АЗС по ID */
    fun getStationById(id: Long): GasStation? = GasStationData.stations.find { it.id == id }

    /** Получить АЗС рядом с координатами (в радиусе km) */
    fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double = 10.0): List<GasStation> {
        val cacheKey = "nearby_${lat}_${lon}_${radiusKm}"
        if (lastFilterKey == cacheKey) {
            return lastFilterResult ?: emptyList()
        }
        val result = GasStationData.getStationsNearLocation(lat, lon, radiusKm)
        lastFilterKey = cacheKey
        lastFilterResult = result
        return result
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
        val cacheKey = "price_asc_${fuelType}_${lat}_${lon}_${radiusKm}"
        if (lastFilterKey == cacheKey) {
            return lastFilterResult ?: emptyList()
        }
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            getAllStations()
        }
        val result = stations
            .filter { it.fuelTypes.any { f -> f.type == fuelType && f.available } }
            .sortedBy { it.fuelTypes.find { f -> f.type == fuelType }?.price ?: Double.MAX_VALUE }
        lastFilterKey = cacheKey
        lastFilterResult = result
        return result
    }

    /** Сортировка по цене: сначала дорогие */
    fun getStationsSortedByPriceDesc(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> {
        val cacheKey = "price_desc_${fuelType}_${lat}_${lon}_${radiusKm}"
        if (lastFilterKey == cacheKey) {
            return lastFilterResult ?: emptyList()
        }
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            getAllStations()
        }
        val result = stations
            .filter { it.fuelTypes.any { f -> f.type == fuelType && f.available } }
            .sortedByDescending { it.fuelTypes.find { f -> f.type == fuelType }?.price ?: 0.0 }
        lastFilterKey = cacheKey
        lastFilterResult = result
        return result
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
        val cacheKey = "best_${fuelType}_${lat}_${lon}_${radiusKm}"
        if (lastFilterKey == cacheKey) {
            return lastFilterResult ?: emptyList()
        }
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            getAllStations()
        }
        val result = stations
            .filter { it.fuelTypes.any { f -> f.type == fuelType && f.available } }
            .sortedBy { station ->
                val price = station.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE
                val queuePenalty = station.queueTime * 0.5
                val reliabilityBonus = (100 - station.reliability) * 0.2
                price + queuePenalty - reliabilityBonus
            }
        lastFilterKey = cacheKey
        lastFilterResult = result
        return result
    }

    /** Поиск по названию/адресу/бренду */
    fun searchStations(query: String): List<GasStation> {
        if (query.length < 2) return emptyList()
        val cacheKey = "search_${query}"
        if (lastFilterKey == cacheKey) {
            return lastFilterResult ?: emptyList()
        }
        val result = GasStationData.stations.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.address.contains(query, ignoreCase = true) ||
                    it.brand.contains(query, ignoreCase = true)
        }
        lastFilterKey = cacheKey
        lastFilterResult = result
        return result
    }

    /** Сортировка по очереди (меньше = лучше) */
    fun getStationsByQueue(
        fuelType: String,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0
    ): List<GasStation> {
        val cacheKey = "queue_${fuelType}_${lat}_${lon}_${radiusKm}"
        if (lastFilterKey == cacheKey) {
            return lastFilterResult ?: emptyList()
        }
        val stations = if (lat != null && lon != null) {
            getNearbyStations(lat, lon, radiusKm)
        } else {
            getAllStations()
        }
        val result = stations
            .filter { it.fuelTypes.any { f -> f.type == fuelType && f.available } }
            .sortedBy { it.queueTime }
        lastFilterKey = cacheKey
        lastFilterResult = result
        return result
    }

    /** Очистить кэш при обновлении данных */
    fun clearCache() {
        lastFilterKey = null
        lastFilterResult = null
        _cachedStations.value = emptyList()
    }
}