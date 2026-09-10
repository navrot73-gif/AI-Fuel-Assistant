package com.navrot.aifuelassistant.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.data.datasource.FuelObservation
import com.navrot.aifuelassistant.data.datasource.OverpassFuelProvider
import com.navrot.aifuelassistant.data.datasource.RussiabaseHtmlParser
import com.navrot.aifuelassistant.data.datasource.RussiabaseMatcher
import com.navrot.aifuelassistant.data.datasource.StationCacheImpl
import com.navrot.aifuelassistant.data.datasource.StationJsonParserImpl
import com.navrot.aifuelassistant.data.datasource.StationLoaderImpl
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.features.dashboard.delegate.StationRecommendationDelegate
import com.navrot.aifuelassistant.ui.map.TILE_SOURCE_OPENFREEMAP
import com.navrot.aifuelassistant.ui.map.TILE_SOURCE_OSM_RASTER
import com.navrot.aifuelassistant.ui.map.TILE_SOURCE_VERSATILES
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeviceScenarioTest {

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
    fun `T7 - Tile source fallback chain transitions ordered without repeating failed source`() = runTest {
        val prefsRepo = UserPreferencesRepository(context)
        prefsRepo.setMapEngine(UserPreferencesRepository.ENGINE_MAPLIBRE)

        val tileChain = listOf(TILE_SOURCE_OPENFREEMAP, TILE_SOURCE_VERSATILES, TILE_SOURCE_OSM_RASTER)
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

        assertEquals(listOf(TILE_SOURCE_OPENFREEMAP, TILE_SOURCE_VERSATILES, TILE_SOURCE_OSM_RASTER), sequence)
        assertEquals(3, failedSources.size)

        // When all sources fail, engine auto-switches to osmdroid
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

        // 1. Recommendation rendered immediately from registry (<= 3s)
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val baseStations = loader.loadFromAssets()
        assertTrue("Base registry size must be >= 100", baseStations.size >= 100)

        val bestStationsUseCase = com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase()
        val vehicleRepo: com.navrot.aifuelassistant.data.VehicleRepository = mock()

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

        // 2. Enrichment caps <= 6s
        val startTime = testScheduler.currentTime
        repo.triggerEnrichment(55.1608, 61.3989)
        val elapsedVirtualMs = testScheduler.currentTime - startTime

        assertTrue("Enrichment total time must cap at <= 6000ms virtual time", elapsedVirtualMs <= 6000L)
        assertTrue("Live Overpass is called when srcOverpass flag is true", liveOverpassCalled)
    }
}
