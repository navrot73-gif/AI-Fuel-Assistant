package com.navrot.aifuelassistant.domain.predictive

import kotlin.math.abs

object PersonalConsumptionModel {

    data class FilterResult(
        val validConsumptions: List<Double>,
        val outliers: List<Double>
    )

    data class BaselineCalculation(
        val baselineConsumption: Double?,
        val validCount: Int,
        val outlierCount: Int,
        val confidence: PredictionConfidence
    )

    /**
     * Filters consumption outliers using median and relative deviation / IQR threshold.
     * Raw event records are not deleted; only marked valid vs outlier for model calculation.
     */
    fun filterOutliers(
        consumptions: List<Double>,
        policy: LearningPolicy = LearningPolicy.DEFAULT
    ): FilterResult {
        if (consumptions.size < 4) {
            return FilterResult(validConsumptions = consumptions, outliers = emptyList())
        }

        val sorted = consumptions.sorted()
        val median = calculateMedian(sorted)

        val valid = mutableListOf<Double>()
        val outliers = mutableListOf<Double>()

        for (value in consumptions) {
            val relativeDev = if (median > 0) abs(value - median) / median else 0.0
            // If relative deviation exceeds multiplier (e.g. > 50% shift from median) or extreme spike
            if (relativeDev > (policy.outlierMultiplier - 1.0).coerceAtLeast(0.4) || value <= 0.0) {
                outliers.add(value)
            } else {
                valid.add(value)
            }
        }

        // Safety fallback: if all values were flagged as outliers, return original list
        if (valid.isEmpty() && consumptions.isNotEmpty()) {
            return FilterResult(validConsumptions = consumptions, outliers = emptyList())
        }

        return FilterResult(validConsumptions = valid, outliers = outliers)
    }

    /**
     * Computes weighted moving average where recent entries carry higher weights.
     */
    fun calculateWeightedMovingAverage(validConsumptions: List<Double>): Double? {
        if (validConsumptions.isEmpty()) return null
        if (validConsumptions.size == 1) return validConsumptions.first()

        var totalWeight = 0.0
        var weightedSum = 0.0

        val n = validConsumptions.size
        for (i in 0 until n) {
            // Weight increases linearly from 1.0 for oldest to 1.0 + i*0.5 for newest
            val weight = 1.0 + (i * 0.5)
            weightedSum += validConsumptions[i] * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) weightedSum / totalWeight else null
    }

    /**
     * Computes median value of a numerical list.
     */
    fun calculateMedian(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    /**
     * Calculates deterministic consumption baseline using weighted average, median, and outlier filtering.
     */
    fun calculateBaseline(
        rawConsumptions: List<Double>,
        policy: LearningPolicy = LearningPolicy.DEFAULT
    ): BaselineCalculation {
        if (rawConsumptions.size < policy.minimumSamples) {
            return BaselineCalculation(
                baselineConsumption = null,
                validCount = rawConsumptions.size,
                outlierCount = 0,
                confidence = PredictionConfidence.UNKNOWN
            )
        }

        val filterResult = filterOutliers(rawConsumptions, policy)
        val valid = filterResult.validConsumptions

        if (valid.size < policy.minimumSamples) {
            return BaselineCalculation(
                baselineConsumption = null,
                validCount = valid.size,
                outlierCount = filterResult.outliers.size,
                confidence = PredictionConfidence.UNKNOWN
            )
        }

        val weightedAvg = calculateWeightedMovingAverage(valid)
        val confidence = evaluateConfidence(valid.size, policy)

        return BaselineCalculation(
            baselineConsumption = weightedAvg,
            validCount = valid.size,
            outlierCount = filterResult.outliers.size,
            confidence = confidence
        )
    }

    fun evaluateConfidence(
        validSampleCount: Int,
        policy: LearningPolicy = LearningPolicy.DEFAULT
    ): PredictionConfidence = when {
        validSampleCount >= policy.highConfidenceMinSamples -> PredictionConfidence.HIGH
        validSampleCount >= policy.mediumConfidenceMinSamples -> PredictionConfidence.MEDIUM
        validSampleCount >= policy.lowConfidenceMinSamples -> PredictionConfidence.LOW
        else -> PredictionConfidence.UNKNOWN
    }
}
