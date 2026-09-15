package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalFuelFeatureExtractorTest {

    @Test
    fun extractFeatures_withEmptyEvents_returnsDefaults() {
        val features = PersonalFuelFeatureExtractor.extractFeatures(
            vehicleId = 1L,
            fuelType = "АИ-95",
            events = emptyList()
        )

        assertEquals(1L, features.vehicleId)
        assertEquals("АИ-95", features.fuelType)
        assertNull(features.recentConsumption)
        assertNull(features.averageConsumption)
        assertNull(features.medianConsumption)
        assertNull(features.recentPrice)
        assertNull(features.averagePrice)
        assertNull(features.refuelIntervalKm)
    }

    @Test
    fun extractFeatures_withConsecutiveFullTankEvents_calculatesConsumptions() {
        val now = System.currentTimeMillis()
        val events = listOf(
            PersonalFuelEvent(
                id = 1,
                vehicleId = 1L,
                timestamp = now - 86400000 * 10,
                fuelType = "АИ-95",
                liters = 40.0,
                pricePerLiter = 60.0,
                odometerKm = 1000.0,
                fullTank = true
            ),
            PersonalFuelEvent(
                id = 2,
                vehicleId = 1L,
                timestamp = now - 86400000 * 5,
                fuelType = "АИ-95",
                liters = 40.0,
                pricePerLiter = 62.0,
                odometerKm = 1500.0, // (40 / 500) * 100 = 8.0 L/100km
                fullTank = true
            ),
            PersonalFuelEvent(
                id = 3,
                vehicleId = 1L,
                timestamp = now,
                fuelType = "АИ-95",
                liters = 45.0,
                pricePerLiter = 64.0,
                odometerKm = 2000.0, // (45 / 500) * 100 = 9.0 L/100km
                fullTank = true
            )
        )

        val features = PersonalFuelFeatureExtractor.extractFeatures(
            vehicleId = 1L,
            fuelType = "АИ-95",
            events = events,
            currentOdometerKm = 2100.0,
            routeDistanceKm = 35.0
        )

        assertEquals(1L, features.vehicleId)
        assertNotNull(features.recentConsumption)
        assertEquals(9.0, features.recentConsumption!!, 0.01)
        assertEquals(8.5, features.averageConsumption!!, 0.01)
        assertEquals(8.5, features.medianConsumption!!, 0.01)
        assertEquals(64.0, features.recentPrice!!, 0.01)
        assertEquals(62.0, features.averagePrice!!, 0.01)
        assertEquals(500.0, features.refuelIntervalKm!!, 0.01)
        assertEquals(100.0, features.distanceSinceRefuel!!, 0.01)
        assertEquals(35.0, features.routeDistance!!, 0.01)
        assertNotNull(features.season)
    }
}
