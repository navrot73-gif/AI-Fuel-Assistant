package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.datasource.BenzonavtFuelDataSourceAdapter
import com.navrot.aifuelassistant.data.datasource.BenzonavtParser
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.intelligence.FuelIntelligenceResolver
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BenzonavtFuelDataSourceAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun createAdapter(): BenzonavtFuelDataSourceAdapter {
        return BenzonavtFuelDataSourceAdapter(
            httpClient = okHttpClient,
            baseUrl = mockWebServer.url("/city-prices").toString()
        )
    }

    // Safety Regression Test: City-level record MUST NOT produce station-level snapshot
    @Test
    fun testCityLevelRecordCannotProduceStationFuelSnapshot() = runBlocking {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-95", "median": 69.5 }
              ],
              "updatedAt": "2026-09-17T10:41:00.614Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertEquals(0, result.observations.size)
        assertEquals(0, result.metrics.stationsMatched)
        assertEquals(0, result.metrics.observationsCreated)

        val snapshot = FuelIntelligenceResolver.resolve(
            observations = result.observations,
            stationId = 1,
            fuelType = "AI-95"
        )

        assertEquals(FuelAvailabilityStatus.UNKNOWN, snapshot.availability)
        assertNull(snapshot.price)
        assertEquals(0, snapshot.sourceCount)
    }

    // A. City-level record MUST NOT produce station observations
    @Test
    fun testCityLevelRecordDoesNotProduceStationObservation() = runBlocking {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-95", "median": 69.5 }
              ],
              "updatedAt": "2026-09-17T10:41:00.614Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertTrue("City-level source MUST NOT generate station-level downstream observations", result.observations.isEmpty())
        assertEquals(1, result.rawObservations.size)
        assertEquals("benzonavt:city:chelyabinsk:AI-95", result.rawObservations[0].externalStationId)
    }

    // B & C & D. One city price MUST NOT produce multiple station observations, matchesBrand MUST NOT be used, stationsMatched == 0
    @Test
    fun testStationMatchingIsZeroForCityLevelSource() = runBlocking {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-95", "median": 69.5, "sources": [{"name": "benzonavt", "price": 69.5}] }
              ],
              "updatedAt": "2026-09-17T10:41:00.614Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertEquals("stationsMatched MUST be 0 when station identity is unavailable", 0, result.metrics.stationsMatched)
        assertEquals("stationsUnmatched MUST reflect aggregate observations", 1, result.metrics.stationsUnmatched)
        assertEquals("observationsCreated MUST be 0", 0, result.metrics.observationsCreated)
        assertTrue("observations MUST be empty for city-level data", result.observations.isEmpty())
    }

    // E. Aggregate/unmatched observation remains traceable
    @Test
    fun testAggregateObservationIsTraceableInRawObservations() = runBlocking {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-95", "median": 69.5 }
              ],
              "updatedAt": "2026-09-17T10:41:00.614Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertEquals(1, result.rawObservations.size)
        val rawObs = result.rawObservations[0]
        assertEquals("benzonavt:city:chelyabinsk:AI-95", rawObs.externalStationId)
        assertEquals("AI-95", rawObs.canonicalFuelType)
        assertEquals(69.5, rawObs.price)
    }

    // F. AI-92 / AI-95 normalization PASS
    @Test
    fun testFuelNormalization() {
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("АИ-92"))
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("92"))
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("ron92"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("АИ-95"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("95"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("gasoline95"))
        assertNull(FuelSourceRequest.normalizeFuelType("DIESEL"))
        assertNull(FuelSourceRequest.normalizeFuelType("AI-98"))
    }

    // G. UNKNOWN availability PASS
    @Test
    fun testMissingAvailabilityIsUnknown() = runBlocking {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-95", "median": 69.5 }
              ],
              "updatedAt": "2026-09-17T10:41:00.614Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertEquals(1, result.rawObservations.size)
        val obs = result.rawObservations[0]
        assertEquals(69.5, obs.price)
        assertEquals("Missing availability field MUST remain UNKNOWN", FuelAvailabilityStatus.UNKNOWN, obs.availability)
    }

    // H. Missing price == null
    @Test
    fun testMissingPriceIsNull() = runBlocking {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-95", "median": 0.0, "available": false }
              ],
              "updatedAt": "2026-09-17T10:41:00.614Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertEquals(1, result.rawObservations.size)
        val obs = result.rawObservations[0]
        assertNull("Missing/zero price MUST be null", obs.price)
        assertEquals(FuelAvailabilityStatus.UNAVAILABLE, obs.availability)
    }

    // I. Freshness / provenance preserved
    @Test
    fun testFreshnessAndProvenancePreserved() = runBlocking {
        val isoStr = "2026-09-17T10:41:00.614Z"
        val expectedEpoch = BenzonavtParser.parseIsoTimestamp(isoStr)
        assertNotNull(expectedEpoch)

        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-95", "median": 69.5 }
              ],
              "updatedAt": "$isoStr"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        val obs = result.rawObservations[0]
        assertEquals(expectedEpoch, obs.observedAt)
        assertEquals("benzonavt:chelyabinsk:AI-95", obs.rawReference)
    }

    // Parser valid response
    @Test
    fun testParserValidResponse() {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                {
                  "code": "АИ-95",
                  "median": 69.5,
                  "min": 69.2,
                  "max": 80.17,
                  "sources": [
                    {"name": "benzonavt", "price": 69.2}
                  ]
                },
                {
                  "code": "АИ-92",
                  "median": 63.8,
                  "min": 63.0,
                  "max": 65.0
                }
              ],
              "count": 2,
              "sourcesUsed": ["benzonavt"],
              "updatedAt": "2026-09-17T10:41:00.614Z"
            }
        """.trimIndent()

        val dto = BenzonavtParser.parseJson(json)
        assertEquals("chelyabinsk", dto.city)
        assertEquals(2, dto.prices?.size)
        assertEquals("АИ-95", dto.prices?.get(0)?.code)
        assertEquals(69.5, dto.prices?.get(0)?.median)
        assertEquals("2026-09-17T10:41:00.614Z", dto.updatedAt)
    }

    // Parser empty response
    @Test(expected = IllegalArgumentException::class)
    fun testParserEmptyResponse() {
        BenzonavtParser.parseJson("")
    }

    // Parser malformed response
    @Test(expected = Exception::class)
    fun testParserMalformedResponse() {
        BenzonavtParser.parseJson("{ invalid json }")
    }

    // HTTP failure (4xx, 5xx)
    @Test
    fun testHttpFailureHandling() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertNotNull(result)
        assertEquals(FuelDataSource.BENZONAVT, result.sourceId)
        assertEquals(FuelSourceStatus.FAILED, result.status)
        assertTrue(result.errorMessage?.contains("500") == true)
    }

    // Network timeout
    @Test
    fun testNetworkTimeoutHandling() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk", timeoutMs = 100L)
        val result = adapter.fetch(request)

        assertEquals(FuelSourceStatus.FAILED, result.status)
        assertTrue(result.errorMessage?.contains("timeout", ignoreCase = true) == true)
    }

    // Unsupported fuel type rejection
    @Test
    fun testUnsupportedFuelTypeRejection() = runBlocking {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-95", "median": 69.5 },
                { "code": "ДТ", "median": 80.5 },
                { "code": "АИ-98", "median": 99.9 }
              ],
              "updatedAt": "2026-09-17T10:41:00.614Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk", fuelTypes = listOf("AI-92", "AI-95"))
        val result = adapter.fetch(request)

        assertTrue(result.rawObservations.all { it.canonicalFuelType == "AI-95" || it.canonicalFuelType == "AI-92" })
        assertEquals(1, result.rawObservations.size)
        assertEquals("AI-95", result.rawObservations[0].canonicalFuelType)
        assertTrue(result.metrics.invalidRecords >= 2)
    }
}
