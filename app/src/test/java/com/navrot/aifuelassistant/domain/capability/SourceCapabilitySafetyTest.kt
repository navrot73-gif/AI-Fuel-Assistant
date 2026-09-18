package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.datasource.BenzonavtFuelDataSourceAdapter
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceRequest
import com.navrot.aifuelassistant.domain.ingestion.IngestionObservation
import com.navrot.aifuelassistant.domain.intelligence.FuelIntelligenceResolver
import com.navrot.aifuelassistant.domain.recommendation.BestStationUseCase
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceCapabilitySafetyTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: BenzonavtFuelDataSourceAdapter

    @Before
    fun setUp() {
        SourceCapabilityRegistry.resetToDefaults()
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/city-prices").toString()
        adapter = BenzonavtFuelDataSourceAdapter(
            httpClient = OkHttpClient(),
            baseUrl = baseUrl
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // Requirement 10: Aggregate Benzonavt observation remains aggregate
    @Test
    fun `test 10 - Benzonavt raw observation retains aggregate ID`() = runBlocking {
        val sampleJson = """
            {
              "city": "chelyabinsk",
              "updatedAt": "2026-03-01T12:00:00Z",
              "prices": [
                {
                  "code": "AI-95",
                  "median": 54.50,
                  "available": true
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sampleJson))

        val result = adapter.fetch(FuelSourceRequest(targetCity = "chelyabinsk"))
        assertEquals(1, result.rawObservations.size)

        val rawObs = result.rawObservations.first()
        assertEquals("benzonavt:city:chelyabinsk:AI-95", rawObs.externalStationId)
        assertEquals("benzonavt:chelyabinsk:AI-95", rawObs.rawReference)
        assertEquals(FuelDataSource.BENZONAVT, rawObs.sourceId)
    }

    // Requirement 11: Aggregate observation cannot become physical station observation
    @Test(expected = IllegalArgumentException::class)
    fun `test 11 - Benzonavt IngestionObservation toFuelSourceObservation throws IllegalArgumentException`() {
        val obs = IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "benzonavt:city:chelyabinsk:AI-95",
            fuelType = "AI-95"
        )
        obs.toFuelSourceObservation(stationId = 42)
    }

    // Requirements 12 & 13: stationsMatched and observationsCreated remain 0
    @Test
    fun `test 12 and 13 - stationsMatched and observationsCreated remain 0 for Benzonavt`() = runBlocking {
        val sampleJson = """
            {
              "city": "chelyabinsk",
              "updatedAt": "2026-03-01T12:00:00Z",
              "prices": [
                { "code": "AI-92", "median": 49.50, "available": true },
                { "code": "AI-95", "median": 54.50, "available": true }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sampleJson))

        val result = adapter.fetch(FuelSourceRequest(targetCity = "chelyabinsk"))
        assertEquals(0, result.metrics.stationsMatched)
        assertEquals(0, result.metrics.observationsCreated)
        assertEquals(2, result.metrics.recordsParsed)
        assertEquals(2, result.rawObservations.size)
        assertTrue(result.observations.isEmpty())
    }

    // Requirement 14 & 15: Canonical AI-92 and AI-95 preserved
    @Test
    fun `test 14 and 15 - canonical AI-92 and AI-95 preserved in raw observations`() = runBlocking {
        val sampleJson = """
            {
              "city": "chelyabinsk",
              "updatedAt": "2026-03-01T12:00:00Z",
              "prices": [
                { "code": "АИ-92", "median": 49.50, "available": true },
                { "code": "ron95", "median": 54.50, "available": true }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sampleJson))

        val result = adapter.fetch(FuelSourceRequest(targetCity = "chelyabinsk"))
        val fuelTypes = result.rawObservations.map { it.canonicalFuelType }
        assertTrue(fuelTypes.contains("AI-92"))
        assertTrue(fuelTypes.contains("AI-95"))
    }

    // Requirements 16, 17, 18, 19, 20: UNKNOWN availability, null price, freshness, provenance, rawReference
    @Test
    fun `test 16 to 20 - UNKNOWN availability null price freshness provenance rawReference preserved`() = runBlocking {
        val sampleJson = """
            {
              "city": "chelyabinsk",
              "updatedAt": "2026-03-01T12:00:00Z",
              "prices": [
                {
                  "code": "AI-95",
                  "median": null,
                  "available": null
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sampleJson))

        val result = adapter.fetch(FuelSourceRequest(targetCity = "chelyabinsk"))
        val obs = result.rawObservations.first()

        assertEquals(FuelAvailabilityStatus.UNKNOWN, obs.availability) // Req 16
        assertNull(obs.price) // Req 17
        assertNotNull(obs.observedAt) // Req 18
        assertEquals(FuelDataSource.BENZONAVT, obs.sourceId) // Req 19
        assertEquals("benzonavt:chelyabinsk:AI-95", obs.rawReference) // Req 20
    }

    // Safety test: FuelIntelligenceResolver rejects Benzonavt from StationFuelSnapshot
    @Test
    fun `test safety - FuelIntelligenceResolver excludes Benzonavt observations from StationFuelSnapshot`() {
        val benzonavtObs = com.navrot.aifuelassistant.domain.intelligence.FuelSourceObservation(
            stationId = 1,
            fuelType = "AI-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0,
            observedAt = System.currentTimeMillis(),
            source = FuelDataSource.BENZONAVT
        )

        val snapshot = FuelIntelligenceResolver.resolve(
            observations = listOf(benzonavtObs),
            stationId = 1,
            fuelType = "AI-95"
        )

        // Benzonavt is CITY_LEVEL and excluded -> yields UNKNOWN snapshot
        assertEquals(FuelAvailabilityStatus.UNKNOWN, snapshot.availability)
        assertEquals(0, snapshot.sourceCount)
        assertNull(snapshot.price)
    }

    // Safety test: BestStationUseCase ignores Benzonavt from station recommendations
    @Test
    fun `test safety - BestStationUseCase excludes Benzonavt from station scoring`() {
        val useCase = BestStationUseCase()
        val station = com.navrot.aifuelassistant.data.model.GasStation(
            id = 1,
            name = "Test Station",
            brand = "TestBrand",
            address = "TestAddress",
            latitude = 55.16,
            longitude = 61.40,
            fuelTypes = listOf(
                com.navrot.aifuelassistant.data.model.FuelPrice(
                    type = "AI-95",
                    price = 55.0,
                    available = true,
                    source = FuelDataSource.BENZONAVT,
                    updatedAt = System.currentTimeMillis()
                )
            ),
            queueTime = 0,
            reliability = 100,
            dataSources = setOf(FuelDataSource.BENZONAVT)
        )

        val result = useCase.execute(
            stations = listOf(station),
            fuelType = "AI-95"
        )

        // Station with only BENZONAVT fuel source cannot reach HIGH confidence
        assertTrue(result.best == null || result.best!!.confidence != com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence.HIGH)
    }
}
