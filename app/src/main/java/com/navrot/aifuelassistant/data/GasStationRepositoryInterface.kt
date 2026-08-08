/**
 * Интерфейс репозитория АЗС.
 *
 * Выделен для тестируемости: мокать интерфейс проще и стабильнее,
 * чем конкретный класс с приватными методами и Android-зависимостями.
 */
package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.model.GasStation

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
    suspend fun refresh(): List<GasStation>
    suspend fun reportUserPrice(stationId: Int, fuelType: String, price: Double): List<GasStation>
    suspend fun clearUserPrice(stationId: Int, fuelType: String): List<GasStation>
}
