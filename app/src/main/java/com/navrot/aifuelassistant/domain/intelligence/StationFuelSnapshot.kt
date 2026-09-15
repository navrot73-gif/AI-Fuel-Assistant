package com.navrot.aifuelassistant.domain.intelligence

import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

data class StationFuelSnapshot(
    val stationId: Int,
    val fuelType: String,
    val availability: FuelAvailabilityStatus,
    val price: Double?,
    val freshness: FuelFreshness,
    val confidence: RecommendationConfidence,
    val sourceCount: Int,
    val confirmingSourceCount: Int,
    val conflictingSourceCount: Int,
    val isConflict: Boolean = false,
    val conflictReason: String? = null,
    val lastUpdatedAt: Long? = null,
    val sources: List<FuelSourceObservation> = emptyList()
)
