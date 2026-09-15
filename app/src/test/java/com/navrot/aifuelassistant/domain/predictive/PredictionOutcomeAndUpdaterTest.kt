package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.predictive.usecase.PersonalModelUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionOutcomeAndUpdaterTest {

    private val policy = LearningPolicy(
        learningRate = 0.1,
        minimumSamples = 3,
        maximumAdjustment = 2.0,
        outlierMultiplier = 1.8
    )
    private val updater = PersonalModelUpdater(policy)

    @Test
    fun outcome_exactMatch_zeroError() {
        val features = PersonalFuelFeatures(
            vehicleId = 1L,
            fuelType = "АИ-95",
            timestamp = 1000L,
            dayOfWeek = 1,
            hour = 12,
            distanceSinceRefuel = 100.0,
            recentConsumption = 8.0,
            averageConsumption = 8.0,
            medianConsumption = 8.0,
            recentPrice = 60.0,
            averagePrice = 60.0,
            refuelIntervalKm = 500.0,
            refuelIntervalDays = 7.0,
            stationId = 10
        )

        val result = updater.evaluateAndUpdate(
            vehicleId = 1L,
            features = features,
            predictedConsumption = 8.0,
            actualConsumption = 8.0,
            sampleCount = 5
        )

        assertEquals(0.0, result.outcome.absoluteError, 0.001)
        assertEquals(0.0, result.outcome.relativeError, 0.001)
        assertEquals(0.0, result.updatedMetadata.biasAdjustment, 0.001)
    }

    @Test
    fun outcome_actualGreaterThanPredicted_updatesBiasIncrementally() {
        val features = PersonalFuelFeatures(
            vehicleId = 1L,
            fuelType = "АИ-95",
            timestamp = 1000L,
            dayOfWeek = 1,
            hour = 12,
            distanceSinceRefuel = 100.0,
            recentConsumption = 8.0,
            averageConsumption = 8.0,
            medianConsumption = 8.0,
            recentPrice = 60.0,
            averagePrice = 60.0,
            refuelIntervalKm = 500.0,
            refuelIntervalDays = 7.0,
            stationId = 10
        )

        val result = updater.evaluateAndUpdate(
            vehicleId = 1L,
            features = features,
            predictedConsumption = 8.0,
            actualConsumption = 9.0, // error = +1.0
            sampleCount = 5
        )

        assertEquals(1.0, result.outcome.absoluteError, 0.01)
        // newBias = oldBias + 0.1 * 1.0 = +0.1
        assertEquals(0.1, result.updatedMetadata.biasAdjustment, 0.01)
    }

    @Test
    fun outcome_extremeAnomaly_skipsBiasUpdate() {
        val features = PersonalFuelFeatures(
            vehicleId = 1L,
            fuelType = "АИ-95",
            timestamp = 1000L,
            dayOfWeek = 1,
            hour = 12,
            distanceSinceRefuel = 100.0,
            recentConsumption = 8.0,
            averageConsumption = 8.0,
            medianConsumption = 8.0,
            recentPrice = 60.0,
            averagePrice = 60.0,
            refuelIntervalKm = 500.0,
            refuelIntervalDays = 7.0,
            stationId = 10
        )

        val result = updater.evaluateAndUpdate(
            vehicleId = 1L,
            features = features,
            predictedConsumption = 8.0,
            actualConsumption = 22.0, // extreme spike > 100% error
            sampleCount = 5
        )

        // Outcome logged correctly
        assertEquals(14.0, result.outcome.absoluteError, 0.01)
        // Bias update skipped due to outlier protection
        assertEquals(0.0, result.updatedMetadata.biasAdjustment, 0.01)
    }

    @Test
    fun outcome_insufficientSamples_skipsModelUpdate() {
        val features = PersonalFuelFeatures(
            vehicleId = 1L,
            fuelType = "АИ-95",
            timestamp = 1000L,
            dayOfWeek = 1,
            hour = 12,
            distanceSinceRefuel = 100.0,
            recentConsumption = 8.0,
            averageConsumption = 8.0,
            medianConsumption = 8.0,
            recentPrice = 60.0,
            averagePrice = 60.0,
            refuelIntervalKm = 500.0,
            refuelIntervalDays = 7.0,
            stationId = 10
        )

        val result = updater.evaluateAndUpdate(
            vehicleId = 1L,
            features = features,
            predictedConsumption = 8.0,
            actualConsumption = 9.0,
            sampleCount = 1 // < minimumSamples (3)
        )

        assertEquals(0.0, result.updatedMetadata.biasAdjustment, 0.01)
    }
}
