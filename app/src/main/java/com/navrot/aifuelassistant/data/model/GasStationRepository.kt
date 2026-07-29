package com.navrot.aifuelassistant.data.model

import com.navrot.aifuelassistant.data.GasStationData

object GasStationRepository {

    fun getAllStations(): List<GasStation> = GasStationData.stations

    fun getStationById(id: Int): GasStation? = GasStationData.stations.find { it.id == id }

    fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double = 10.0): List<GasStation> {
        return GasStationData.getStationsNearLocation(lat, lon, radiusKm)
    }

    fun getStationsByCity(city: String): List<GasStation> {
        return GasStationData.getStationsByCity(city)
    }

    fun searchStations(query: String): List<GasStation> {
        return GasStationData.searchStations(query)
    }

    fun getBestStations(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        return GasStationData.getBestStations(fuelType, lat, lon, radiusKm)
    }

    fun getStationsSortedByPriceAsc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        return GasStationData.getStationsSortedByPriceAsc(fuelType, lat, lon, radiusKm)
    }

    fun getStationsSortedByPriceDesc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        return GasStationData.getStationsSortedByPriceDesc(fuelType, lat, lon, radiusKm)
    }

    fun getStationsByQueue(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        return GasStationData.getStationsByQueue(fuelType, lat, lon, radiusKm)
    }

    fun getCheapestStation(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): GasStation? {
        return GasStationData.getCheapestStation(fuelType, lat, lon, radiusKm)
    }

    fun getBestStation(fuelType: String): GasStation? {
        return GasStationData.getBestStation(fuelType)
    }
}