package com.navrot.aifuelassistant.domain.intelligence

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

data class FuelSourceObservation(
    val stationId: Int,
    val fuelType: String,
    val availability: FuelAvailabilityStatus = FuelAvailabilityStatus.UNKNOWN,
    val price: Double? = null,
    val observedAt: Long? = null,
    val source: FuelDataSource = FuelDataSource.DEMO,
    val confidence: RecommendationConfidence = RecommendationConfidence.UNKNOWN,
    val referenceId: String? = null
)
