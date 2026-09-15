package com.navrot.aifuelassistant.domain.predictive

/**
 * Result of trip fuel cost prediction.
 */
data class TripCostPrediction(
    val distanceKm: Double,
    val predictedLiters: Double?,
    val predictedCost: Double?, // Cost in currency (₽), null if missing consumption or price
    val expectedPricePerLiter: Double?,
    val confidence: PredictionConfidence,
    val explanation: String
)
