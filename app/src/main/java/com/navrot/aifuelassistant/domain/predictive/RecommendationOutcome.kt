package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

/**
 * Outcome evaluating recommendation prediction accuracy against actual observed user/station facts.
 */
data class RecommendationOutcome(
    val feedbackId: String,
    val recommendationId: String,
    val stationId: Long,
    val fuelType: String?,
    val action: UserAction,
    val outcome: UserOutcome,
    val predictedAvailability: FuelAvailabilityStatus?,
    val actualAvailability: FuelAvailabilityStatus?,
    val availabilityCorrect: Boolean?,
    val predictedPrice: Double?,
    val actualPrice: Double?,
    val priceError: Double?,
    val absolutePriceError: Double?,
    val predictedQueue: Int?,
    val actualQueue: Int?,
    val queueError: Int?,
    val isConfirmedFact: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
