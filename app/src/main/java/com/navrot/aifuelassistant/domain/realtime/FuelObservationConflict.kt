package com.navrot.aifuelassistant.domain.realtime

import com.navrot.aifuelassistant.domain.intelligence.FuelSourceObservation

enum class ConflictType {
    AVAILABILITY_CONFLICT,
    PRICE_CONFLICT,
    FRESHNESS_CONFLICT,
    SOURCE_CONFLICT
}

data class FuelObservationConflict(
    val stationId: Int,
    val fuelType: String,
    val observations: List<FuelSourceObservation>,
    val detectedAt: Long = System.currentTimeMillis(),
    val conflictType: ConflictType
)
