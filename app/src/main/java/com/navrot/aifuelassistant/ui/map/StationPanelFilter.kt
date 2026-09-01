package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator

/**
 * Filters gas stations for the "Stations Nearby" bottom sheet panel.
 * Only includes stations where selected fuel type(s) are AVAILABLE and have a price > 0.
 * Stations with NO_FUEL, UNKNOWN, or price 0 ₽ are excluded from the panel.
 */
fun filterStationsForPanel(
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>
): List<GasStation> {
    return stations.filter { station ->
        station.fuelTypes.any { fuel ->
            (selectedFuelTypes.isEmpty() || selectedFuelTypes.contains(fuel.type)) &&
                    fuel.price > 0.0 &&
                    PriceReliabilityCalculator.calculateFuelAvailability(station, fuel.type) == FuelAvailabilityStatus.AVAILABLE
        }
    }
}
