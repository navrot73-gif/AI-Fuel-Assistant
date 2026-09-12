package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator

/**
 * Filters gas stations for the "Stations Nearby" bottom sheet panel.
 * Includes stations where selected fuel type(s) are AVAILABLE and have a price > 0.
 * If filtering yields 0 stations, applies auto-relaxation to return all stations so panel is never empty.
 */
fun filterStationsForPanel(
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>
): List<GasStation> {
    val filtered = stations.filter { station ->
        station.fuelTypes.any { fuel ->
            (selectedFuelTypes.isEmpty() || selectedFuelTypes.contains(fuel.type)) &&
                    fuel.price > 0.0 &&
                    PriceReliabilityCalculator.calculateFuelAvailability(station, fuel.type) == FuelAvailabilityStatus.AVAILABLE
        }
    }
    if (filtered.isEmpty() && stations.isNotEmpty()) {
        return stations
    }
    return filtered
}
