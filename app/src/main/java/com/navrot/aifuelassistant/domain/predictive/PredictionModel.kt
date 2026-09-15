package com.navrot.aifuelassistant.domain.predictive

/**
 * Interface for predictive fuel consumption models.
 * Enables statistical or future ML implementations without altering caller contracts.
 */
interface PredictionModel {
    fun predict(features: PersonalFuelFeatures): Double?
    fun update(features: PersonalFuelFeatures, actual: Double): PredictionModel
    fun confidence(sampleCount: Int): PredictionConfidence
}
