package com.navrot.aifuelassistant.domain.predictive

import javax.inject.Inject

/**
 * Explicit configuration policy for predictive statistical learning and self-learning updates.
 */
data class LearningPolicy(
    val learningRate: Double = DEFAULT_LEARNING_RATE,
    val minimumSamples: Int = DEFAULT_MINIMUM_SAMPLES,
    val maximumAdjustment: Double = DEFAULT_MAXIMUM_ADJUSTMENT,
    val outlierMultiplier: Double = DEFAULT_OUTLIER_MULTIPLIER,
    val highConfidenceMinSamples: Int = DEFAULT_HIGH_CONFIDENCE_SAMPLES,
    val mediumConfidenceMinSamples: Int = DEFAULT_MEDIUM_CONFIDENCE_SAMPLES,
    val lowConfidenceMinSamples: Int = DEFAULT_LOW_CONFIDENCE_SAMPLES
) {
    @Inject
    constructor() : this(
        DEFAULT_LEARNING_RATE,
        DEFAULT_MINIMUM_SAMPLES,
        DEFAULT_MAXIMUM_ADJUSTMENT,
        DEFAULT_OUTLIER_MULTIPLIER,
        DEFAULT_HIGH_CONFIDENCE_SAMPLES,
        DEFAULT_MEDIUM_CONFIDENCE_SAMPLES,
        DEFAULT_LOW_CONFIDENCE_SAMPLES
    )

    companion object {
        const val DEFAULT_LEARNING_RATE = 0.1
        const val DEFAULT_MINIMUM_SAMPLES = 3
        const val DEFAULT_MAXIMUM_ADJUSTMENT = 2.0
        const val DEFAULT_OUTLIER_MULTIPLIER = 1.8
        const val DEFAULT_HIGH_CONFIDENCE_SAMPLES = 10
        const val DEFAULT_MEDIUM_CONFIDENCE_SAMPLES = 5
        const val DEFAULT_LOW_CONFIDENCE_SAMPLES = 3

        val DEFAULT = LearningPolicy()
    }
}
