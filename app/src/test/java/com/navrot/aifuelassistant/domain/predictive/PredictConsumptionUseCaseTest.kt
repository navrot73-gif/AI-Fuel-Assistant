package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import com.navrot.aifuelassistant.domain.predictive.usecase.PredictConsumptionUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictConsumptionUseCaseTest {

    private val useCase = PredictConsumptionUseCase()

    @Test
    fun invoke_zeroHistory_returnsUnavailablePrediction() {
        val prediction = useCase(vehicleId = 1L, fuelType = "АИ-95", events = emptyList())

        assertNull(prediction.predictedConsumption)
        assertEquals(PredictionConfidence.UNKNOWN, prediction.confidence)
        assertEquals("Прогноз пока недоступен. Добавьте ещё несколько заправок.", prediction.basedOn)
    }

    @Test
    fun invoke_insufficientHistory_returnsUnavailablePrediction() {
        val events = listOf(
            PersonalFuelEvent(vehicleId = 1L, timestamp = 1000, fuelType = "АИ-95", liters = 40.0, odometerKm = 1000.0, fullTank = true),
            PersonalFuelEvent(vehicleId = 1L, timestamp = 2000, fuelType = "АИ-95", liters = 40.0, odometerKm = 1500.0, fullTank = true)
        )

        val prediction = useCase(vehicleId = 1L, fuelType = "АИ-95", events = events)

        assertNull(prediction.predictedConsumption)
        assertEquals(PredictionConfidence.UNKNOWN, prediction.confidence)
        assertEquals("Прогноз пока недоступен. Добавьте ещё несколько заправок.", prediction.basedOn)
    }

    @Test
    fun invoke_sufficientHistory_returnsValidPrediction() {
        val now = System.currentTimeMillis()
        val events = listOf(
            PersonalFuelEvent(vehicleId = 1L, timestamp = now - 40000, fuelType = "АИ-95", liters = 40.0, odometerKm = 1000.0, fullTank = true),
            PersonalFuelEvent(vehicleId = 1L, timestamp = now - 30000, fuelType = "АИ-95", liters = 40.0, odometerKm = 1500.0, fullTank = true),
            PersonalFuelEvent(vehicleId = 1L, timestamp = now - 20000, fuelType = "АИ-95", liters = 40.0, odometerKm = 2000.0, fullTank = true),
            PersonalFuelEvent(vehicleId = 1L, timestamp = now - 10000, fuelType = "АИ-95", liters = 40.0, odometerKm = 2500.0, fullTank = true)
        )

        val prediction = useCase(vehicleId = 1L, fuelType = "АИ-95", events = events)

        assertNotNull(prediction.predictedConsumption)
        assertEquals(8.0, prediction.predictedConsumption!!, 0.05)
        assertEquals(PredictionConfidence.LOW, prediction.confidence)
        assertTrue(prediction.basedOn.contains("Прогноз основан на"))
    }
}
