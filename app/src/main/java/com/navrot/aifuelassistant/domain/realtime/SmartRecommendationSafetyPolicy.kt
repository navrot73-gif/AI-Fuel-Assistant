package com.navrot.aifuelassistant.domain.realtime

import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

object SmartRecommendationSafetyPolicy {

    /**
     * Determines if a candidate station is ineligible for recommendation due to safety criteria.
     * Rule: Explicitly UNAVAILABLE or NO_FUEL stations must NEVER be recommended.
     * UNKNOWN availability stations are NOT automatically excluded.
     */
    fun isExcludedFromRecommendation(dataQuality: FuelDataQuality): Boolean {
        return dataQuality.availability == FuelAvailabilityStatus.UNAVAILABLE ||
                dataQuality.availability == FuelAvailabilityStatus.NO_FUEL
    }

    /**
     * Adjusts confidence based on real-time data quality warnings, freshness, and conflicts.
     */
    fun adjustConfidenceForSafety(
        baseConfidence: RecommendationConfidence,
        dataQuality: FuelDataQuality
    ): RecommendationConfidence {
        if (dataQuality.availability == FuelAvailabilityStatus.UNKNOWN && dataQuality.price == null) {
            return RecommendationConfidence.UNKNOWN
        }

        if (dataQuality.conflict || dataQuality.qualityLevel == FuelDataQualityLevel.LOW) {
            return RecommendationConfidence.LOW
        }

        if (dataQuality.qualityLevel == FuelDataQualityLevel.HIGH && baseConfidence == RecommendationConfidence.HIGH) {
            return RecommendationConfidence.HIGH
        }

        if (dataQuality.qualityLevel == FuelDataQualityLevel.MEDIUM || baseConfidence == RecommendationConfidence.MEDIUM) {
            return RecommendationConfidence.MEDIUM
        }

        return RecommendationConfidence.LOW
    }

    /**
     * Generates safety warning text for UI if recommendation relies on incomplete or aging data.
     */
    fun generateSafetyWarnings(dataQuality: FuelDataQuality): List<String> {
        val warnings = mutableListOf<String>()

        if (dataQuality.conflict) {
            warnings.add("Источники расходятся — данные требуют проверки")
        } else if (dataQuality.availability == FuelAvailabilityStatus.UNKNOWN) {
            warnings.add("Наличие топлива не подтверждено")
        }

        if (dataQuality.price == null) {
            warnings.add("Цена топлива не указана")
        }

        if (dataQuality.warnings.contains("Данные устарели (>6 ч)")) {
            val ageStr = dataQuality.ageMinutes?.let { " (${it / 60} ч назад)" } ?: ""
            warnings.add("Рекомендация основана на устаревших данных$ageStr")
        } else if (dataQuality.warnings.contains("Данные частично устарели (1–6 ч)")) {
            val ageStr = dataQuality.ageMinutes?.let { " (${it / 60} ч назад)" } ?: ""
            warnings.add("Данные обновлялись более 1 ч назад$ageStr")
        }

        return warnings
    }
}
