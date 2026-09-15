package com.navrot.aifuelassistant.domain.predictive

/**
 * Result of fuel consumption prediction.
 */
data class ConsumptionPrediction(
    val predictedConsumption: Double?, // L/100km, null if insufficient data
    val confidence: PredictionConfidence,
    val sampleCount: Int,
    val basedOn: String, // Explainable detail (e.g. "По 18 заправкам, средний расход 8.3 л/100 км, исключен 1 выброс")
    val baselineConsumption: Double? = null,
    val deltaFromBaseline: Double? = null
)
