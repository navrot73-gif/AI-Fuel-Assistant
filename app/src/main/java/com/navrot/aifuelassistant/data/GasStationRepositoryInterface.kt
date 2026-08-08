package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.model.GasStation

/**
 * Интерфейс репозитория АЗС.
 *
 * Позволяет мокать в тестах и заменять реализацию
 * (например, на Room-кэш или backend API).
 */
interface GasStationRepositoryInterface {

    suspend fun getAllStations(): List<GasStation>

    suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double): List<GasStation>

    suspend fun getStationsByCity(city: String): List<GasStation>

    suspend fun searchStations(query: String): List<GasStation>

    suspend fun getBestStations(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation>

    suspend fun getCheapestStation(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): GasStation?

    suspend fun getStationsSortedByPriceAsc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation>

    suspend fun getStationsSortedByPriceDesc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation>

    suspend fun getStationsByQueue(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation>

    /** Сообщить пользовательскую цену. Цена немедленно применяется к кешу. */
    suspend fun reportUserPrice(stationId: Int, fuelType: String, price: Double): List<GasStation>

    /** Очистить пользовательскую цену. */
    suspend fun clearUserPrice(stationId: Int, fuelType: String): List<GasStation>

    /** Принудительное обновление из сети. */
    suspend fun refresh(): List<GasStation>
}