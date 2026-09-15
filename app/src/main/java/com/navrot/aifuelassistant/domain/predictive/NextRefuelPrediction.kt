package com.navrot.aifuelassistant.domain.predictive

/**
 * Result of next refuel timing/distance prediction.
 */
data class NextRefuelPrediction(
    val predictedKmRemaining: Double?,
    val predictedDaysRemaining: Double?,
    val confidence: PredictionConfidence,
    val explanation: String
)
