package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.isKnownClosed
import com.navrot.aifuelassistant.data.model.matchesBrand
import com.navrot.aifuelassistant.geo.GeoUtils
import javax.inject.Inject

class StationFilterAndSorterImpl @Inject constructor() : StationFilterAndSorter {

    /**
     * Фильтрует станции в радиусе от пользователя и сортирует по расстоянию (по возрастанию).
     *
     * Оптимизация: расстояние (Haversine, 6 тригонометрических операций) считается ОДИН раз
     * на каждую станцию и кешируется в паре (station, distanceKm). Раньше считалось дважды —
     * отдельно в filter, отдельно в sortedBy — что давало лишние ~1200 вызовов Haversine на
     * 100+ АЗС в радиусе при каждой эмиссии карты.
     */
    override fun getStationsNearLocation(
        lat: Double,
        lon: Double,
        radiusKm: Double,
        stations: List<GasStation>
    ): List<GasStation> {
        // Один проход: фильтрация + расчёт расстояния
        val withDistances = stations.mapNotNull { station ->
            val distance = GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
            if (distance <= radiusKm) station to distance else null
        }
        // Сортировка по уже посчитанному distance — без второго вызова Haversine
        return withDistances
            .sortedBy { it.second }
            .map { it.first }
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
