package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.isKnownClosed
import com.navrot.aifuelassistant.data.model.matchesBrand
import com.navrot.aifuelassistant.geo.GeoUtils
import javax.inject.Inject

class StationFilterAndSorterImpl @Inject constructor() : StationFilterAndSorter {

    override fun getStationsNearLocation(
        lat: Double,
        lon: Double,
        radiusKm: Double,
        stations: List<GasStation>
    ): List<GasStation> {
        return stations.filter { station ->
            val distance = GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
            distance <= radiusKm
        }.sortedBy { station ->
            GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
        }
    }

    override fun filterByCity(stations: List<GasStation>, city: String): List<GasStation> {
        return stations.filter { it.address.contains(city, ignoreCase = true) }
    }

    override fun search(stations: List<GasStation>, query: String): List<GasStation> {
        val q = query.lowercase()
        return stations.filter {
            it.name.lowercase().contains(q) ||
                    it.brand.lowercase().contains(q) ||
                    it.address.lowercase().contains(q)
        }
    }

    override fun getCheapestStation(stations: List<GasStation>, fuelType: String): GasStation? {
        return stations
            .filter { s -> s.fuelTypes.any { it.type == fuelType } }
            .minByOrNull { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    override fun sortPriceAscending(stations: List<GasStation>, fuelType: String): List<GasStation> {
        return stations.filter { s -> s.fuelTypes.any { it.type == fuelType } }
            .sortedBy { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    override fun sortPriceDescending(stations: List<GasStation>, fuelType: String): List<GasStation> {
        return stations.filter { s -> s.fuelTypes.any { it.type == fuelType } }
            .sortedByDescending { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    override fun sortByQueue(stations: List<GasStation>, fuelType: String): List<GasStation> {
        return stations.filter { s -> s.fuelTypes.any { it.type == fuelType } }
            .sortedBy { it.queueTime }
    }

    override fun filterOpen(stations: List<GasStation>): List<GasStation> {
        return stations.filter { !it.isKnownClosed() }
    }

    override fun filterByBrands(stations: List<GasStation>, brands: Set<String>): List<GasStation> {
        if (brands.isEmpty()) return stations
        return stations.filter { station ->
            brands.any { b -> station.matchesBrand(b) }
        }
    }
}
