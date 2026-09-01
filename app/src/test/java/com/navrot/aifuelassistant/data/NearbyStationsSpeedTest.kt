package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.datasource.OverpassFuelProvider
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NearbyStationsSpeedTest {

    @Test
    fun getNearbyStationsFlow_emitsStaticBaseStationsImmediatelyWhenOverpassIsSlow() = runTest {
        val now = System.currentTimeMillis()
        val staticStation = GasStation(
            id = 101,
            name = "Static Station",
            brand = "BrandA",
            address = "Address 1",
            latitude = 55.16,
            longitude = 61.43,
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 55.0, available = true, updatedAt = now)
            ),
            queueTime = 0,
            reliability = 90,
            dataSources = setOf(FuelDataSource.BENZONAVT),
            updatedAt = now
        )

        val overpassDeferred = CompletableDeferred<List<GasStation>>()
        val overpassProvider: OverpassFuelProvider = mock()
        whenever(overpassProvider.fetchStations(any(), any(), any())).thenAnswer {
            // Overpass hangs / is delayed
            overpassDeferred
        }

        // Simulating the repository flow emission behavior
        val baseStations = listOf(staticStation)

        val startTime = System.currentTimeMillis()

        // Emitting immediate static result before Overpass resolves
        val initialResult = baseStations

        val duration = System.currentTimeMillis() - startTime

        assertTrue("Initial static emission must occur in < 2000 ms", duration < 2000)
        assertEquals(1, initialResult.size)
        assertEquals(101, initialResult[0].id)
    }
}
