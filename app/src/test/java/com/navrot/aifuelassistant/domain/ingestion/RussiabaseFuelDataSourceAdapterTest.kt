package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.datasource.FuelObservation
import com.navrot.aifuelassistant.data.datasource.RussiabaseFuelDataSourceAdapter
import com.navrot.aifuelassistant.data.datasource.RussiabaseProvider
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.intelligence.FuelIntelligenceResolver
import com.navrot.aifuelassistant.domain.intelligence.FuelSourceObservation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RussiabaseFuelDataSourceAdapterTest {

    private class FakeRussiabaseProvider(
        var observationsToReturn: List<FuelObservation> = emptyList(),
        var shouldThrow: Boolean = false
    ) : RussiabaseProvider {
        override suspend fun fetchObservations(
            citySlug: String,
            fuels: List<String>,
            lat: Double?,
            lon: Double?
        ): List<FuelObservation> {
            if (shouldThrow) throw RuntimeException("Simulated Russiabase Provider error")
            return observationsToReturn
        }
    }

    private val sampleStation = GasStation(
        id = 101,
        name = "Газпромнефть №201",
        brand = "Газпромнефть",
        address = "Свердловский тракт, 12В",
        latitude = 55.2243443,
        longitude = 61.3747471,
        fuelTypes = listOf(
            FuelPrice(type = "AI-95", price = 55.0, available = true, source = FuelDataSource.RUSSIABASE)
        ),
        queueTime = 0,
        reliability = 100,
        dataSources = setOf(FuelDataSource.RUSSIABASE),
        ref = "201"
    )

    @Test
    fun testSuccessfulFetchAndMapping() = runBlocking {
        val fakeProvider = FakeRussiabaseProvider(
            observationsToReturn = listOf(
                FuelObservation(
                    brand = "Газпромнефть №201",
                    address = "Свердловский тракт 12В",
                    fuelType = "АИ-95",
                    price = 56.5,
                    available = true,
                    statusText = "В наличии"
                )
            )
        )

        val adapter = RussiabaseFuelDataSourceAdapter(fakeProvider)
        val request = FuelSourceRequest(targetCity = "chelyabinsk", fuelTypes = listOf("AI-95"))
        val result = adapter.fetchWithStationMapping(request, listOf(sampleStation))

        assertEquals(FuelDataSource.RUSSIABASE, result.sourceId)
        assertEquals(FuelSourceStatus.HEALTHY, result.status)
        assertEquals(1, result.observations.size)
        assertEquals(1, result.rawObservations.size)

        val obs = result.observations[0]
        assertEquals(101, obs.stationId)
        assertEquals("AI-95", obs.fuelType)
        assertEquals(56.5, obs.price)
        assertEquals(FuelAvailabilityStatus.AVAILABLE, obs.availability)
        assertEquals(FuelDataSource.RUSSIABASE, obs.source)

        assertEquals(1, result.metrics.recordsReceived)
        assertEquals(1, result.metrics.recordsParsed)
        assertEquals(1, result.metrics.stationsMatched)
        assertEquals(0, result.metrics.stationsUnmatched)
    }

    @Test
    fun testCanonicalFuelTypeNormalization() {
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("АИ-92"))
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("92"))
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("ron92"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("АИ-95"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("95"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("gasoline95"))
        assertNull(FuelSourceRequest.normalizeFuelType("DIESEL"))
        assertNull(FuelSourceRequest.normalizeFuelType("AI-98"))
    }

    @Test
    fun testProviderErrorHandling() = runBlocking {
        val fakeProvider = FakeRussiabaseProvider(shouldThrow = true)
        val adapter = RussiabaseFuelDataSourceAdapter(fakeProvider)
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetchWithStationMapping(request, listOf(sampleStation))

        assertEquals(FuelSourceStatus.FAILED, result.status)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Simulated Russiabase Provider error"))
    }

    @Test
    fun testStationFuelSnapshotSafetyBoundary() = runBlocking {
        // Station-level observation from Russiabase
        val obs = FuelSourceObservation(
            stationId = 101,
            fuelType = "AI-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 56.5,
            observedAt = System.currentTimeMillis(),
            source = FuelDataSource.RUSSIABASE
        )

        val snapshot = FuelIntelligenceResolver.resolve(
            observations = listOf(obs),
            stationId = 101,
            fuelType = "AI-95"
        )

        assertEquals(FuelAvailabilityStatus.AVAILABLE, snapshot.availability)
        assertEquals(56.5, snapshot.price)
        assertEquals(1, snapshot.sourceCount)

        // Benzonavt observation (CITY_LEVEL / ineligible)
        val cityObs = FuelSourceObservation(
            stationId = 101,
            fuelType = "AI-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 69.5,
            observedAt = System.currentTimeMillis(),
            source = FuelDataSource.BENZONAVT
        )

        val citySnapshot = FuelIntelligenceResolver.resolve(
            observations = listOf(cityObs),
            stationId = 101,
            fuelType = "AI-95"
        )

        assertEquals(FuelAvailabilityStatus.UNKNOWN, citySnapshot.availability)
        assertNull(citySnapshot.price)
        assertEquals(0, citySnapshot.sourceCount)
    }
}
