package com.navrot.aifuelassistant.domain.personal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalConsumptionCalculatorTest {

    @Test
    fun `calculateFullTankConsumption returns valid consumption when consecutive full tanks and valid odometers`() {
        val prev = PersonalFuelEvent(
            vehicleId = 1L,
            timestamp = 1000L,
            fuelType = "AI-95",
            liters = 40.0,
            odometerKm = 10000.0,
            fullTank = true
        )
        val curr = PersonalFuelEvent(
            vehicleId = 1L,
            timestamp = 2000L,
            fuelType = "AI-95",
            liters = 40.0,
            odometerKm = 10500.0, // 500 km
            fullTank = true
        )

        val result = PersonalConsumptionCalculator.calculateFullTankConsumption(prev, curr)
        // 40 / 500 * 100 = 8.0
        assertEquals(8.0, result!!, 0.001)
    }

    @Test
    fun `calculateFullTankConsumption returns null when fullTank is false`() {
        val prev = PersonalFuelEvent(
            vehicleId = 1L,
            timestamp = 1000L,
            fuelType = "AI-95",
            liters = 40.0,
            odometerKm = 10000.0,
            fullTank = true
        )
        val curr = PersonalFuelEvent(
            vehicleId = 1L,
            timestamp = 2000L,
            fuelType = "AI-95",
            liters = 40.0,
            odometerKm = 10500.0,
            fullTank = false
        )

        val result = PersonalConsumptionCalculator.calculateFullTankConsumption(prev, curr)
        assertNull(result)
    }

    @Test
    fun `calculateFullTankConsumption returns null when zero or negative distance`() {
        val prev = PersonalFuelEvent(
            vehicleId = 1L,
            timestamp = 1000L,
            fuelType = "AI-95",
            liters = 40.0,
            odometerKm = 10000.0,
            fullTank = true
        )
        val curr = PersonalFuelEvent(
            vehicleId = 1L,
            timestamp = 2000L,
            fuelType = "AI-95",
            liters = 40.0,
            odometerKm = 10000.0, // 0 km
            fullTank = true
        )

        val result = PersonalConsumptionCalculator.calculateFullTankConsumption(prev, curr)
        assertNull(result)
    }
}
