package com.navrot.aifuelassistant.domain.predictive

/**
 * Confidence level for statistical predictions.
 *
 * Dependent on sample count, data freshness, variance, and completeness.
 * Never uses fake numeric scores (like confidence = 100).
 */
enum class PredictionConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN;

    fun toUserLabel(): String = when (this) {
        HIGH -> "высокая"
        MEDIUM -> "средняя"
        LOW -> "низкая"
        UNKNOWN -> "недостаточно данных"
    }
}
