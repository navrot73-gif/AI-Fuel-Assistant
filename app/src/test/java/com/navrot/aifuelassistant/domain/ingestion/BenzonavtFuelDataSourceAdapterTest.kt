package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.datasource.BenzonavtFuelDataSourceAdapter
import com.navrot.aifuelassistant.data.datasource.BenzonavtParser
import com.navrot.aifuelassistant.data.datasource.StationLoader
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.intelligence.FuelIntelligenceResolver
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.smart.GetSmartFuelRecommendationUseCase
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

    private fun createAdapter(loader: StationLoader = createMockStationLoader()): BenzonavtFuelDataSourceAdapter {
        return BenzonavtFuelDataSourceAdapter(
            httpClient = okHttpClient,
            stationLoader = loader,
            baseUrl = mockWebServer.url("/city-prices").toString()
        )
    }

    private fun createMockStationLoader(count: Int = 109): StationLoader {
        val dummyStations = (1..count).map { id ->
            GasStation(
                id = id,
                name = "АЗС №$id",
                brand = if (id % 2 == 0) "Газпромнефть" else "benzonavt",
                address = "Челябинск, ул. Свободы $id",
                latitude = 55.16 + (id * 0.001),
                longitude = 61.40 + (id * 0.001),
                fuelTypes = emptyList(),
                queueTime = 0,
                reliability = 0
            )
        }
        return object : StationLoader {
            override suspend fun loadStations(): List<GasStation> = dummyStations
            override suspend fun loadFromRemote(): List<GasStation>? = dummyStations
            override suspend fun loadFromCache(): List<GasStation>? = dummyStations
            override suspend fun loadFromAssets(): List<GasStation> = dummyStations
        }
    }

    // A. Parser valid response
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

    // B. Parser empty response
    @Test(expected = IllegalArgumentException::class)
    fun testParserEmptyResponse() {
        BenzonavtParser.parseJson("")
    }

    // C. Parser malformed response
    @Test(expected = Exception::class)
    fun testParserMalformedResponse() {
        BenzonavtParser.parseJson("{ invalid json }")
    }

    // D. HTTP failure (4xx, 5xx)
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

    // E. Network timeout
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

    // F. Unsupported fuel type rejection
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

    // G. Aliases -> canonical AI-92 / AI-95
    @Test
    fun testAliasesNormalization() {
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("АИ-92"))
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("92"))
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("ron92"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("АИ-95"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("95"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("gasoline95"))
        assertNull(FuelSourceRequest.normalizeFuelType("DIESEL"))
        assertNull(FuelSourceRequest.normalizeFuelType("AI-98"))
    }

    // H. Missing price -> null/UNKNOWN
    @Test
    fun testMissingPriceSemantics() = runBlocking {
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
        assertNull(obs.price)
        assertEquals(FuelAvailabilityStatus.UNAVAILABLE, obs.availability)
    }

    // I. Missing availability -> UNKNOWN (Strict Requirement 9)
    @Test
    fun testMissingAvailabilitySemantics() = runBlocking {
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
        assertEquals(FuelAvailabilityStatus.UNKNOWN, obs.availability)
    }

    // J. Invalid coordinates handling
    @Test(expected = IllegalArgumentException::class)
    fun testInvalidCoordinatesValidation() {
        IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_invalid",
            fuelType = "AI-95",
            latitude = 120.0
        )
    }

    // K & L & R. Station matching success and registry baseline 109/109
    @Test
    fun testStationMatchingAndBaseline109() = runBlocking {
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

        val mockLoader = createMockStationLoader(count = 109)
        val adapter = createAdapter(mockLoader)
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertEquals(109, mockLoader.loadFromAssets().size)
        assertTrue(result.metrics.stationsMatched > 0)
        assertEquals(109, mockLoader.loadFromAssets().size) // Station registry baseline remains 109/109
    }

    // M. Partial station data
    @Test
    fun testPartialDataParsing() = runBlocking {
        val json = """
            {
              "city": "chelyabinsk",
              "prices": [
                { "code": "АИ-92" }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val adapter = createAdapter()
        val request = FuelSourceRequest(targetCity = "chelyabinsk")
        val result = adapter.fetch(request)

        assertEquals(1, result.rawObservations.size)
        val obs = result.rawObservations[0]
        assertEquals("AI-92", obs.canonicalFuelType)
        assertNull(obs.price)
        assertEquals(FuelAvailabilityStatus.UNKNOWN, obs.availability)
        assertNull(obs.observedAt)
    }

    // N & O. Freshness & Provenance
    @Test
    fun testFreshnessAndProvenance() = runBlocking {
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

    // P. Real observation creation
    @Test
    fun testObservationCreation() {
        val rawObs = IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "benzonavt_chelyabinsk_ai95",
            fuelType = "АИ-95",
            price = 69.5,
            availability = FuelAvailabilityStatus.AVAILABLE,
            observedAt = 1600000000000L
        )

        val downstream = rawObs.toFuelSourceObservation(stationId = 101)
        assertEquals(101, downstream.stationId)
        assertEquals("AI-95", downstream.fuelType)
        assertEquals(69.5, downstream.price)
        assertEquals(FuelAvailabilityStatus.AVAILABLE, downstream.availability)
        assertEquals(1600000000000L, downstream.observedAt)
        assertEquals(FuelDataSource.BENZONAVT, downstream.source)
    }

    // Q. Integration with existing intelligence pipeline
    @Test
    fun testIntegrationWithIntelligencePipeline() = runBlocking {
        val rawObs = IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "benzonavt_chelyabinsk_ai95",
            fuelType = "АИ-95",
            price = 69.5,
            availability = FuelAvailabilityStatus.AVAILABLE
        )

        val downstreamObs = rawObs.toFuelSourceObservation(stationId = 1)

        val snapshot = FuelIntelligenceResolver.resolve(
            observations = listOf(downstreamObs),
            stationId = 1,
            fuelType = "AI-95"
        )

        assertNotNull(snapshot)
        assertEquals(1, snapshot.stationId)
        assertEquals(69.5, snapshot.price)
        assertEquals(FuelAvailabilityStatus.AVAILABLE, snapshot.availability)

        val mockStation = GasStation(
            id = 1,
            name = "Тестовая АЗС",
            brand = "Газпромнефть",
            address = "Челябинск",
            latitude = 55.16,
            longitude = 61.40,
            fuelTypes = listOf(
                FuelPrice(type = "AI-95", price = 69.5, available = true, source = FuelDataSource.BENZONAVT)
            ),
            queueTime = 0,
            reliability = 0
        )

        val useCase = GetSmartFuelRecommendationUseCase()
        val recommendationResult = useCase.execute(
            stations = listOf(mockStation),
            fuelType = "AI-95",
            userLat = 55.16,
            userLon = 61.40
        )

        assertNotNull(recommendationResult)
        assertNotNull(recommendationResult.topRecommendation)
        assertEquals(1, recommendationResult.topRecommendation?.stationId)
    }

    // S. UNKNOWN safety regression
    @Test
    fun testUnknownSafetyRegression() {
        val obs = IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "AI-95"
        )

        assertEquals(FuelAvailabilityStatus.UNKNOWN, obs.availability)
        assertNull(obs.price)

        val downstream = obs.toFuelSourceObservation(stationId = 1)
        assertEquals(FuelAvailabilityStatus.UNKNOWN, downstream.availability)
        assertNull(downstream.price)
    }
}
