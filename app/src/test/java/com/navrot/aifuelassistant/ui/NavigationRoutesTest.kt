package com.navrot.aifuelassistant.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRoutesTest {

    @Test
    fun `test all NavRoute subclasses are annotated with Serializable`() {
        val navRouteSubclasses = listOf(
            NavRoute.MainTabs::class.java,
            NavRoute.Map::class.java,
            NavRoute.MapBuildRoute::class.java,
            NavRoute.MapShowStations::class.java,
            NavRoute.Dashboard::class.java,
            NavRoute.Garage::class.java,
            NavRoute.GarageList::class.java,
            NavRoute.GarageDetail::class.java,
            NavRoute.AddVehicle::class.java,
            NavRoute.FuelRecordList::class.java,
            NavRoute.AddFuelRecord::class.java,
            NavRoute.Reports::class.java,
            NavRoute.Vehicles::class.java,
            NavRoute.Settings::class.java,
            NavRoute.FuelForm::class.java,
            NavRoute.VehicleDetail::class.java,
            NavRoute.StationDetail::class.java
        )

        for (clazz in navRouteSubclasses) {
            val serializableAnnotation = clazz.annotations.find {
                it.annotationClass.java.name == "kotlinx.serialization.Serializable"
            }
            assertNotNull("Class ${clazz.simpleName} should be annotated with @Serializable", serializableAnnotation)
        }
    }

    @Test
    fun `smoke test FuelForm serialization and deserialization`() {
        val originalRoute = NavRoute.FuelForm(vehicleId = 1L, stationId = 5)
        val jsonString = Json.encodeToString(originalRoute)
        assertTrue(jsonString.contains("\"vehicleId\":1"))
        assertTrue(jsonString.contains("\"stationId\":5"))

        val deserializedRoute = Json.decodeFromString<NavRoute.FuelForm>(jsonString)
        assertEquals(1L, deserializedRoute.vehicleId)
        assertEquals(5, deserializedRoute.stationId)
        assertEquals(originalRoute, deserializedRoute)
    }

    @Test
    fun `smoke test StationDetail serialization and deserialization`() {
        val originalRoute = NavRoute.StationDetail(stationId = 10, fuelType = "АИ-95")
        val jsonString = Json.encodeToString(originalRoute)

        val deserializedRoute = Json.decodeFromString<NavRoute.StationDetail>(jsonString)
        assertEquals(10, deserializedRoute.stationId)
        assertEquals("АИ-95", deserializedRoute.fuelType)
        assertEquals(originalRoute, deserializedRoute)
    }

    @Test
    fun `smoke test VehicleDetail serialization and deserialization`() {
        val originalRoute = NavRoute.VehicleDetail(vehicleId = 42L)
        val jsonString = Json.encodeToString(originalRoute)

        val deserializedRoute = Json.decodeFromString<NavRoute.VehicleDetail>(jsonString)
        assertEquals(42L, deserializedRoute.vehicleId)
        assertEquals(originalRoute, deserializedRoute)
    }
}
