package com.navrot.aifuelassistant.domain.predictive

import kotlin.math.abs

/**
 * Deterministic statistical prediction model implementing incremental self-learning updates.
 */
data class StatisticalPredictionModel(
    val biasAdjustment: Double = 0.0,
    val policy: LearningPolicy = LearningPolicy.DEFAULT
) : PredictionModel {

    override fun predict(features: PersonalFuelFeatures): Double? {
        val base = features.recentConsumption ?: features.medianConsumption ?: features.averageConsumption
        ?: return null
        return (base + biasAdjustment).coerceAtLeast(1.0)
    }

    override fun update(features: PersonalFuelFeatures, actual: Double): StatisticalPredictionModel {
        val predicted = predict(features) ?: return this
        val error = actual - predicted

        // Protect model against single extreme anomalies
        val relativeError = if (predicted > 0) abs(error) / predicted else 0.0
        if (relativeError > (policy.outlierMultiplier - 1.0).coerceAtLeast(0.4)) {
            // Anomaly detected: skip bias modification
            return this
        }

        // Bounded incremental update: newBias = oldBias + learningRate * error
        val delta = (policy.learningRate * error).coerceIn(
            -policy.maximumAdjustment,
            policy.maximumAdjustment
        )
        val newBias = (biasAdjustment + delta).coerceIn(
            -policy.maximumAdjustment * 2,
            policy.maximumAdjustment * 2
        )

        return copy(biasAdjustment = newBias)
    }

    override fun confidence(sampleCount: Int): PredictionConfidence {
        return PersonalConsumptionModel.evaluateConfidence(sampleCount, policy)
    }
}
