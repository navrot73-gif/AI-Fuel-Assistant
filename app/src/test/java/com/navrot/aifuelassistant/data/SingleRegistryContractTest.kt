package com.navrot.aifuelassistant.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.data.datasource.FuelObservation
import com.navrot.aifuelassistant.data.datasource.RussiabaseHtmlParser
import com.navrot.aifuelassistant.data.datasource.RussiabaseMatcher
import com.navrot.aifuelassistant.data.datasource.StationCacheImpl
import com.navrot.aifuelassistant.data.datasource.StationJsonParserImpl
import com.navrot.aifuelassistant.data.datasource.StationLoaderImpl
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.domain.usecase.StationQueryFacade
import com.navrot.aifuelassistant.network.FuelApi
import com.navrot.aifuelassistant.ui.map.delegate.MapRouteDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SingleRegistryContractTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        MapDiagnosticsTracker.resetStartupTimings()
    }

    @Test
    fun `T1 - Benzonavt unmatched row is dropped and AI brand-nearest does NOT contain Salavata`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val registryStations = loader.loadFromAssets()
        MapDiagnosticsTracker.registrySize = registryStations.size

        // Unmatched Benzonavt observation for non-existent station
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

        // 1. Unmatched observation is dropped: registry station count remains unchanged
        assertEquals(registryStations.size, overlaidStations.size)
        // 2. No station in overlaidStations has address containing "Салавата Юлаева, 28"
        val hasPhantom = overlaidStations.any { it.address.contains("Салавата Юлаева, 28") }
        assertFalse("Unmatched Benzonavt row for Salavata Yulaeva 28 must be dropped", hasPhantom)
        // 3. benzonavtUnmatched counter is incremented
        assertTrue("benzonavtUnmatched should be >= 1", MapDiagnosticsTracker.benzonavtUnmatched >= 1)

        // 4. AI brand-nearest query for "Газпромнефть" returns a real station, NOT containing "Салавата"
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
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun `T5 - cold start offline registry_size is at least 100`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )
        val stations = loader.loadFromAssets()
        MapDiagnosticsTracker.registrySize = stations.size

        assertTrue("Cold start offline registry_size must be >= 100", stations.size >= 100)
        assertTrue("Diagnostics tracker registrySize must be >= 100", MapDiagnosticsTracker.registrySize >= 100)
    }
}
