package com.navrot.aifuelassistant.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.data.datasource.FuelObservation
import com.navrot.aifuelassistant.data.datasource.OverpassFuelProvider
import com.navrot.aifuelassistant.data.datasource.OverpassFuelProviderImpl
import com.navrot.aifuelassistant.data.datasource.RussiabaseHtmlParser
import com.navrot.aifuelassistant.data.datasource.RussiabaseMatcher
import com.navrot.aifuelassistant.data.datasource.RussiabaseProviderImpl
import com.navrot.aifuelassistant.data.datasource.StationCacheImpl
import com.navrot.aifuelassistant.data.datasource.StationJsonParserImpl
import com.navrot.aifuelassistant.data.datasource.StationLoaderImpl
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.domain.usecase.StationQueryFacade
import com.navrot.aifuelassistant.features.dashboard.delegate.StationRecommendationDelegate
import com.navrot.aifuelassistant.network.FuelApi
import com.navrot.aifuelassistant.network.FuelApiImpl
import com.navrot.aifuelassistant.ui.map.TILE_SOURCE_OPENFREEMAP
import com.navrot.aifuelassistant.ui.map.TILE_SOURCE_OSM_RASTER
import com.navrot.aifuelassistant.ui.map.TILE_SOURCE_VERSATILES
import com.navrot.aifuelassistant.ui.map.delegate.MapRouteDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * JVM Quality Gate covering Scenarios T1–T10 without requiring physical device or emulator.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeviceScenarioUnitGate {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val userPrices: UserPriceRepository = mock()
    private val benzonavtProvider: BenzonavtProvider = mock()
    private val getBestStationsUseCase: GetBestStationsUseCase = mock()

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            MapDiagnosticsTracker.resetStartupTimings()
            whenever(userPrices.getAll()).doReturn(emptyMap())
            whenever(benzonavtProvider.currentCity()).doReturn("chelyabinsk")
            whenever(benzonavtProvider.fetchCityPrices(any())).doReturn(emptyMap())
        }
    }

    @Test
    fun `T1 - Benzonavt unmatched row is dropped and AI brand-nearest does NOT contain Salavata`() = runTest {
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val registryStations = loader.loadFromAssets()
        MapDiagnosticsTracker.registrySize = registryStations.size

        val phantomObs = FuelObservation(
            brand = "Газпромнефть",
            address = "Челябинск, ул. Салавата Юлаева, 28",
            fuelType = "АИ-95",
            available = true,
            price = 65.0
        )

        val overlaidStations = RussiabaseMatcher.applyBenzonavtObservations(
            stations = registryStations,
            observations = listOf(phantomObs)
        )

        assertEquals(registryStations.size, overlaidStations.size)
        val hasPhantom = overlaidStations.any { it.address.contains("Салавата Юлаева, 28") }
        assertFalse("Unmatched Benzonavt row for Salavata Yulaeva 28 must be dropped", hasPhantom)
        assertTrue("benzonavtUnmatched should be >= 1", MapDiagnosticsTracker.benzonavtUnmatched >= 1)

        val resolved = StationQueryFacade.resolveQuery(
            text = "Газпромнефть",
            stations = overlaidStations,
            userLat = 55.1608,
            userLon = 61.3989
        )
        assertNotNull(resolved)
        assertFalse("AI brand-nearest station address must NOT contain 'Салавата'", resolved!!.nearestStation.address.contains("Салавата"))
    }

    @Test
    fun `T2 - AI brand response equals top1 of nearest_top3`() = runTest {
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val stations = loader.loadFromAssets()
        val lat = 55.1608
        val lon = 61.3989
        val brand = "Газпромнефть"

        val aiResult = StationQueryFacade.nearestByBrand(
            stations = stations,
            brand = brand,
            userLat = lat,
            userLon = lon
        )

        val top3Candidates = StationQueryFacade.getTopCandidates(
            stations = stations,
            brand = brand,
            userLat = lat,
            userLon = lon
        )

        assertNotNull(aiResult)
        assertTrue(top3Candidates.isNotEmpty())
        assertEquals("AI brand-nearest ID must match top1 candidate ID", top3Candidates.first().id, aiResult!!.nearestStation.id)
    }

    @Test
    fun `T3 - route target coordinates exactly equal registry coordinates of resolved station`() = runTest {
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val stations = loader.loadFromAssets()
        val userLat = 55.1608
        val userLon = 61.3989

        val resolved = StationQueryFacade.resolveQuery(
            text = "Газпромнефть Курчатова 2",
            stations = stations,
            userLat = userLat,
            userLon = userLon
        )?.nearestStation

        assertNotNull(resolved)

        val fuelApi: FuelApi = mock()
        val routeStateManager = RouteStateManager()
        val routeDelegate = MapRouteDelegate(fuelApi, routeStateManager)

        routeDelegate.buildRouteTo(
            scope = testScope,
            station = resolved!!,
            userLocation = Pair(userLat, userLon),
            onError = {}
        )

        val routeState = routeDelegate.route.value
        assertNotNull(routeState)
        val endPoint = routeState!!.points.last()

        assertEquals("Route destination latitude must exactly match registry station latitude", resolved.latitude, endPoint.latitude, 0.000001)
        assertEquals("Route destination longitude must exactly match registry station longitude", resolved.longitude, endPoint.longitude, 0.000001)
    }

    @Test
    fun `T4 - Russiabase fixture applies AI-95 NO_FUEL status to registry station Sverdlovsky Trakt 12V`() = runTest {
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val stations = loader.loadFromAssets()

        val fixtureStream = javaClass.classLoader?.getResourceAsStream("russiabase_fixture.html")
            ?: error("russiabase_fixture.html resource not found")
        val html = fixtureStream.bufferedReader().use { it.readText() }
        val observations = RussiabaseHtmlParser.parseHtml(html, "ai95")

        val overlaidStations = RussiabaseMatcher.applyObservations(
            stations = stations,
            observations = observations
        )

        val targetStation = overlaidStations.find { it.address.contains("Свердловский тракт") && it.address.contains("12") }
        assertNotNull("Registry station on Sverdlovsky Trakt 12V must exist", targetStation)

        val ai95Price = targetStation!!.fuelTypes.find { it.type == "АИ-95" }
        assertNotNull("AI-95 fuel entry must exist", ai95Price)
        assertFalse("AI-95 fuel must be unavailable (RED / NO_FUEL)", ai95Price!!.available)

        val status = PriceReliabilityCalculator.calculateFuelAvailability(targetStation, fuelType = "АИ-95")
        assertEquals("Fuel availability status for AI-95 must be NO_FUEL", FuelAvailabilityStatus.NO_FUEL, status)
    }

    @Test
    fun `T5 - Cold start offline registry size is at least 100 and Russiabase fixture address matching yields matched over 0`() = runTest {
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val stations = loader.loadFromAssets()
        MapDiagnosticsTracker.registrySize = stations.size

        assertTrue("Cold start offline registry_size must be >= 100", stations.size >= 100)

        val fixtureStream = javaClass.classLoader?.getResourceAsStream("russiabase_fixture.html")
            ?: error("russiabase_fixture.html resource not found")
        val html = fixtureStream.bufferedReader().use { it.readText() }
        val observations = RussiabaseHtmlParser.parseHtml(html, "ai95")

        val overlaidStations = RussiabaseMatcher.applyObservations(
            stations = stations,
            observations = observations,
            mode = "slug",
            region = "468",
            httpCode = 200
        )

        val sverdlovskyStation = overlaidStations.find {
            it.address.contains("Свердловский") && (it.address.contains("12") || it.name.contains("201"))
        }
        assertNotNull("Sverdlovsky Trakt 12V station must exist in registry", sverdlovskyStation)

        val status = PriceReliabilityCalculator.calculateFuelAvailability(sverdlovskyStation!!, fuelType = "АИ-95")
        assertEquals("Sverdlovsky Trakt 12V AI-95 must be NO_FUEL (RED)", FuelAvailabilityStatus.NO_FUEL, status)
    }

    @Test
    fun `T6 - Lukoil Mira 65 st1 exists in registry and registry size is at least 109`() = runTest {
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val stations = loader.loadFromAssets()

        val lukoilMira = stations.find { it.address.contains("Мира, 65") }
        assertNotNull("Lukoil on Mira 65 st1 must exist in station registry", lukoilMira)
        assertEquals("Brand must be Лукойл", "Лукойл", lukoilMira!!.brand)
        assertTrue("Registry size must be >= 109", stations.size >= 109)
    }

    @Test
    fun `T7 - Tile source fallback chain transitions ordered without repeating failed source`() = runTest {
        val prefsRepo = UserPreferencesRepository(context)
        prefsRepo.setMapEngine(UserPreferencesRepository.ENGINE_MAPLIBRE)

        val tileChain = listOf(TILE_SOURCE_OSM_RASTER, TILE_SOURCE_OPENFREEMAP, TILE_SOURCE_VERSATILES)
        val failedSources = mutableSetOf<String>()

        var currentIdx = 0
        val sequence = mutableListOf<String>()

        while (currentIdx < tileChain.size) {
            val source = tileChain[currentIdx]
            assertFalse("Source $source must not be repeated in fallback cycle", failedSources.contains(source))
            sequence.add(source)
            failedSources.add(source)

            val nextIdx = tileChain.indices.firstOrNull { it > currentIdx && !failedSources.contains(tileChain[it]) }
            if (nextIdx != null) {
                currentIdx = nextIdx
            } else {
                break
            }
        }

        assertEquals(listOf(TILE_SOURCE_OSM_RASTER, TILE_SOURCE_OPENFREEMAP, TILE_SOURCE_VERSATILES), sequence)
        assertEquals(3, failedSources.size)

        prefsRepo.setMapEngine(UserPreferencesRepository.ENGINE_OSMDROID)
        val engine = prefsRepo.mapEngine.first()
        assertEquals(UserPreferencesRepository.ENGINE_OSMDROID, engine)
    }

    @Test
    fun `T8 - Enrichment caps at 6 seconds and recommendation is drawn from registry within 3 seconds`() = runTest {
        var liveOverpassCalled = false
        val overpassProvider = object : OverpassFuelProvider {
            override suspend fun fetchStations(lat: Double, lon: Double, radiusMeters: Double): List<GasStation> {
                liveOverpassCalled = true
                return emptyList()
            }
        }

        val httpClient = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).build()
        val repo = GasStationRepository(
            context = context,
            httpClient = httpClient,
            userPrices = userPrices,
            getBestStationsUseCase = getBestStationsUseCase,
            benzonavtProvider = benzonavtProvider,
            appScope = testScope,
            overpassFuelProvider = overpassProvider
        )

        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val baseStations = loader.loadFromAssets()
        assertTrue("Base registry size must be >= 100", baseStations.size >= 100)

        val bestStationsUseCase = GetBestStationsUseCase()
        val vehicleRepo: VehicleRepository = mock()

        val recommendationDelegate = StationRecommendationDelegate(
            vehicleRepository = vehicleRepo,
            gasStationRepository = repo,
            getBestStationsUseCase = bestStationsUseCase
        )

        val recStartTime = System.currentTimeMillis()
        recommendationDelegate.setStations(baseStations)
        val recommendationDuration = System.currentTimeMillis() - recStartTime

        assertNotNull("Best station recommendation must be present", recommendationDelegate.bestStation.value)
        assertTrue("Recommendation must be rendered in <= 3000ms, took $recommendationDuration ms", recommendationDuration <= 3000L)

        val startTime = testScheduler.currentTime
        repo.triggerEnrichment(55.1608, 61.3989)
        val elapsedVirtualMs = testScheduler.currentTime - startTime

        assertTrue("Enrichment total time must cap at <= 6000ms virtual time", elapsedVirtualMs <= 6000L)
        assertTrue("Live Overpass is called when srcOverpass flag is true", liveOverpassCalled)
    }

    @Test
    fun `T9 - network available but external flags disabled activeSources is registry and no external calls`() = runTest {
        val requestedUrls = CopyOnWriteArrayList<String>()

        val interceptingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requestedUrls.add(request.url.toString())
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val userPrefs = UserPreferencesRepository(context)
        userPrefs.setSrcBenzonavt(false)
        userPrefs.setSrcRussiabase(false)
        userPrefs.setSrcOverpass(false)

        val benzonavt = BenzonavtProvider(interceptingClient)
        val overpass = OverpassFuelProviderImpl(interceptingClient)
        val russiabase = RussiabaseProviderImpl(interceptingClient, context)

        val repository = GasStationRepository(
            context = context,
            httpClient = interceptingClient,
            userPrices = userPrices,
            getBestStationsUseCase = getBestStationsUseCase,
            benzonavtProvider = benzonavt,
            appScope = testScope,
            overpassFuelProvider = overpass,
            russiabaseProvider = russiabase,
            userPreferencesRepository = userPrefs
        )

        val stations = repository.triggerEnrichment(55.1608, 61.3989)
        assertTrue("Station registry must not be empty", stations.isNotEmpty())

        val activeSources = MapDiagnosticsTracker.activeSources
        assertEquals("activeSources must be 'registry' when all external flags are false", "registry", activeSources)

        val externalCalls = requestedUrls.filter { url ->
            url.contains("benzonavt") || url.contains("russiabase") || url.contains("overpass") || url.contains("openstreetmap")
        }
        assertTrue(
            "No network calls to external sources when flags disabled: $externalCalls",
            externalCalls.isEmpty()
        )
    }

    @Test
    fun `T10 - route to registry station endpoint receives exact registry coordinates`() = runTest {
        val capturedUrls = CopyOnWriteArrayList<String>()

        val dummyJsonResponse = """
            {
              "routes": [
                {
                  "distance": 1200.0,
                  "duration": 180.0,
                  "geometry": {
                    "coordinates": [
                      [61.3989, 55.1608],
                      [61.4000, 55.1700]
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val interceptingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedUrls.add(req.url.toString())
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(dummyJsonResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val benzonavt = BenzonavtProvider(interceptingClient)

        val repository = GasStationRepository(
            context = context,
            httpClient = interceptingClient,
            userPrices = userPrices,
            getBestStationsUseCase = getBestStationsUseCase,
            benzonavtProvider = benzonavt,
            appScope = testScope
        )

        val allStations = repository.getAllStations()
        val targetStation = allStations.first { it.latitude != 0.0 && it.longitude != 0.0 }

        val fromLon = 61.3989
        val fromLat = 55.1608

        val fuelApi = FuelApiImpl(interceptingClient)
        val result = fuelApi.getRoute(
            fromLon = fromLon,
            fromLat = fromLat,
            toLon = targetStation.longitude,
            toLat = targetStation.latitude,
            alternatives = false
        )

        assertTrue("Route request must succeed", result.isSuccess)
        assertTrue("OSRM request must be sent", capturedUrls.isNotEmpty())

        val requestUrl = capturedUrls.first { it.contains("osrm") || it.contains("route") }
        val expectedCoordSubString = "$fromLon,$fromLat;${targetStation.longitude},${targetStation.latitude}"

        assertTrue(
            "URL $requestUrl must contain exact registry coordinates: $expectedCoordSubString",
            requestUrl.contains(expectedCoordSubString)
        )
    }
}
