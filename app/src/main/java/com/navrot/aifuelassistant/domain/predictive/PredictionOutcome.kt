package com.navrot.aifuelassistant.domain.predictive

import kotlin.math.abs

/**
 * Logged outcome comparing a past prediction with actual recorded value.
 */
data class PredictionOutcome(
    val predictionId: String,
    val vehicleId: Long,
    val predictedValue: Double,
    val actualValue: Double,
    val absoluteError: Double = abs(actualValue - predictedValue),
    val relativeError: Double = if (actualValue != 0.0) abs(actualValue - predictedValue) / actualValue else 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
