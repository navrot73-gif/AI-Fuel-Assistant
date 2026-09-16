package com.navrot.aifuelassistant.domain.smart

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.intelligence.FuelFreshness
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

data class SmartStationRecommendation(
    val station: GasStation,
    val stationId: Int,
    val stationName: String,
    val address: String,
    val fuelType: String,

    val availability: FuelAvailabilityStatus,
    val availabilityFreshness: FuelFreshness,
    val price: Double?,
    val priceFreshness: FuelFreshness,

    val distanceKm: Double?,
    val estimatedTravelMinutes: Int?,

    val queueTimeMinutes: Int?,

    val objectiveScore: Double,
    val personalScore: Double,

    val estimatedFuelCost: Double?,
    val estimatedTripCost: Double?,
    val estimatedTotalCost: Double?,

    val confidence: RecommendationConfidence,

    val reasons: List<SmartRecommendationReason>,

    val personalVisitCount: Int?,

    val recommended: Boolean,

    val dataQuality: com.navrot.aifuelassistant.domain.realtime.FuelDataQuality? = null,
    val safetyWarnings: List<String> = emptyList()
)
