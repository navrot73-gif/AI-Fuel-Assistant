package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.predictive.usecase.PredictTripFuelCostUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PredictTripFuelCostUseCaseTest {

    private val useCase = PredictTripFuelCostUseCase()

    @Test
    fun invoke_validInputs_calculatesLitersAndCost() {
        val consumption = ConsumptionPrediction(
            predictedConsumption = 8.0,
            confidence = PredictionConfidence.HIGH,
            sampleCount = 10,
            basedOn = "Sample"
        )

        val result = useCase(
            distanceKm = 100.0,
            consumptionPrediction = consumption,
            expectedPricePerLiter = 60.0
        )

        assertNotNull(result.predictedLiters)
        assertNotNull(result.predictedCost)
        assertEquals(8.0, result.predictedLiters!!, 0.01)
        assertEquals(480.0, result.predictedCost!!, 0.01)
        assertEquals(PredictionConfidence.HIGH, result.confidence)
    }

    @Test
    fun invoke_missingDistance_returnsUnknown() {
        val consumption = ConsumptionPrediction(
            predictedConsumption = 8.0,
            confidence = PredictionConfidence.HIGH,
            sampleCount = 10,
            basedOn = "Sample"
        )

        val result = useCase(
            distanceKm = null,
            consumptionPrediction = consumption,
            expectedPricePerLiter = 60.0
        )

        assertNull(result.predictedCost)
        assertEquals(PredictionConfidence.UNKNOWN, result.confidence)
    }

    @Test
    fun invoke_missingPrice_returnsLitersWithoutCost() {
        val consumption = ConsumptionPrediction(
            predictedConsumption = 8.0,
            confidence = PredictionConfidence.MEDIUM,
            sampleCount = 5,
            basedOn = "Sample"
        )

        val result = useCase(
            distanceKm = 50.0,
            consumptionPrediction = consumption,
            expectedPricePerLiter = null
        )

        assertNotNull(result.predictedLiters)
        assertEquals(4.0, result.predictedLiters!!, 0.01)
        assertNull(result.predictedCost)
    }
}
