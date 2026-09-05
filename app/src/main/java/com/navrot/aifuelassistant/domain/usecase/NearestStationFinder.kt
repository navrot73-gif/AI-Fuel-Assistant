package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.matchesBrand
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.geo.GeoUtils

data class NearestBrandResult(
    val nearestStation: GasStation,
    val isNearestFuelAvailable: Boolean,
    val alternativeWithFuel: GasStation?
)

data class StationCandidate(
    val id: Int,
    val brand: String,
    val name: String,
    val distanceKm: Double,
    val status: FuelAvailabilityStatus
)

object NearestStationFinder {

    /**
     * Returns top candidates for brand query sorted by distance.
     */
    fun getTopCandidates(
        stations: List<GasStation>,
        brand: String,
        userLat: Double,
        userLon: Double,
        fuelType: String? = null,
        limit: Int = 3
    ): List<StationCandidate> {
        return stations.filter {
            it.matchesBrand(brand) ||
            it.brand.contains(brand, ignoreCase = true) ||
            it.name.contains(brand, ignoreCase = true)
        }.map { st ->
            val dist = GeoUtils.calculateDistance(userLat, userLon, st.latitude, st.longitude)
            val status = PriceReliabilityCalculator.calculateFuelAvailability(st, fuelType)
            StationCandidate(st.id, st.brand, st.name, dist, status)
        }.sortedBy { it.distanceKm }.take(limit)
    }

    /**
     * Finds the nearest station matching the specified brand across the full merged list of stations (including OSM-only).
     * If the nearest station does not have available fuel (status NO_FUEL or UNKNOWN, or price 0),
     * an alternative station with available fuel is provided.
     */
    fun findNearestStationByBrand(
        stations: List<GasStation>,
        brand: String,
        userLat: Double,
        userLon: Double,
        fuelType: String? = null
    ): NearestBrandResult? {
        val matchingBrandStations = stations.filter {
            it.matchesBrand(brand) ||
            it.brand.contains(brand, ignoreCase = true) ||
            it.name.contains(brand, ignoreCase = true)
        }
        if (matchingBrandStations.isEmpty()) return null

        val nearest = matchingBrandStations.minByOrNull {
            GeoUtils.calculateDistance(userLat, userLon, it.latitude, it.longitude)
        } ?: return null

        val availability = PriceReliabilityCalculator.calculateFuelAvailability(nearest, fuelType)
        val fuelPrice = fuelType?.let { ft -> nearest.fuelTypes.find { it.type == ft }?.price ?: 0.0 } ?: 0.0
        val hasFuel = availability == FuelAvailabilityStatus.AVAILABLE && fuelPrice > 0.0

        val alternative = if (!hasFuel) {
            stations.filter { st ->
                val status = PriceReliabilityCalculator.calculateFuelAvailability(st, fuelType)
                val price = fuelType?.let { ft -> st.fuelTypes.find { it.type == ft }?.price ?: 0.0 } ?: (st.fuelTypes.firstOrNull()?.price ?: 0.0)
                status == FuelAvailabilityStatus.AVAILABLE && price > 0.0 && st.id != nearest.id
            }.minByOrNull {
                GeoUtils.calculateDistance(userLat, userLon, it.latitude, it.longitude)
            }
        } else null

        return NearestBrandResult(
            nearestStation = nearest,
            isNearestFuelAvailable = hasFuel,
            alternativeWithFuel = alternative
        )
    }
}
