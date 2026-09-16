package com.navrot.aifuelassistant.domain.realtime

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.intelligence.FuelFreshness
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

data class FuelDataQuality(
    val stationId: Int,
    val fuelType: String,
    val availability: FuelAvailabilityStatus,
    val price: Double?,
    val lastUpdated: Long?,
    val ageMinutes: Long?,
    val freshness: FuelFreshness,
    val sourceCount: Int,
    val reliableSourceCount: Int,
    val agreement: Boolean,
    val conflict: Boolean,
    val conflicts: List<FuelObservationConflict> = emptyList(),
    val sourceReliability: Double,
    val completeness: Double,
    val qualityLevel: FuelDataQualityLevel,
    val confidence: RecommendationConfidence,
    val primarySource: FuelDataSource?,
    val supportingSources: List<FuelDataSource>,
    val warnings: List<String> = emptyList()
)
