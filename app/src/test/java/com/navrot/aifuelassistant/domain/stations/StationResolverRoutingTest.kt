package com.navrot.aifuelassistant.domain.stations

import android.content.Context
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.features.dashboard.delegate.AiChatDelegate
import com.navrot.aifuelassistant.features.dashboard.delegate.StationRecommendationDelegate
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.network.FuelApi
import com.navrot.aifuelassistant.network.RouteOptionData
import com.navrot.aifuelassistant.network.RouteResponse
import com.navrot.aifuelassistant.ui.map.delegate.MapRouteDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StationResolverRoutingTest {

    private val mockRepository = mock<GasStationRepositoryInterface>()
    private val mockFuelApi = mock<FuelApi>()
    private val mockContext = mock<Context>()
    private val mockPrefs = mock<android.content.SharedPreferences>()

    private lateinit var resolver: StationResolver
    private lateinit var routeStateManager: RouteStateManager
    private lateinit var mapRouteDelegate: MapRouteDelegate

    private val userLat = 55.1644
    private val userLon = 61.4368

    private val sverdlovskyStation = GasStation(
        id = 12,
        name = "Татнефть Свердловский тракт",
        brand = "Татнефть",
        address = "Челябинск, Свердловский тракт, 40/1",
        latitude = 55.1847,
        longitude = 61.4098,
        fuelTypes = listOf(
            FuelPrice("АИ-95", 0.0, available = false, source = FuelDataSource.BENZONAVT)
        ),
        queueTime = 0,
        reliability = 80,
        dataSources = setOf(FuelDataSource.BENZONAVT)
    )

    private val kurchatovaStation = GasStation(
        id = 10,
        name = "Газпромнефть",
        brand = "Газпромнефть",
        address = "ул. Курчатова, 2/1",
        latitude = 55.1500,
        longitude = 61.4200,
        fuelTypes = listOf(
            FuelPrice("АИ-95", 62.0, available = true)
        ),
        queueTime = 0,
        reliability = 90
    )

    @Before
    fun setup() = runTest {
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockPrefs)
        whenever(mockPrefs.getString(any(), any())).thenReturn("[]")

        whenever(mockRepository.getNearbyStations(any(), any(), any()))
            .thenReturn(listOf(sverdlovskyStation, kurchatovaStation))
        whenever(mockRepository.getAllStations())
            .thenReturn(listOf(sverdlovskyStation, kurchatovaStation))

        resolver = StationResolver(mockRepository)
        routeStateManager = RouteStateManager()
        mapRouteDelegate = MapRouteDelegate(mockFuelApi, routeStateManager)
    }

    @Test
    fun `resolveQuery for Gazpromneft Sverdlovsky Trakt finds station with dist under 5km and nearestByBrand finds it`() = runTest {
        val gpnSverdlovsky = GasStation(
            id = 201,
            name = "Газпромнефть №201",
            brand = "Газпромнефть",
            address = "Челябинск, Свердловский тракт, 12В",
            latitude = 55.1800,
            longitude = 61.4150,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 61.05, available = true),
                FuelPrice("АИ-95", 66.54, available = false)
            ),
            queueTime = 0,
            reliability = 90
        )

        whenever(mockRepository.getNearbyStations(any(), any(), any()))
            .thenReturn(listOf(gpnSverdlovsky, kurchatovaStation, sverdlovskyStation))
        whenever(mockRepository.getAllStations())
            .thenReturn(listOf(gpnSverdlovsky, kurchatovaStation, sverdlovskyStation))

        val resolved = resolver.resolveQuery("Газпромнефть Свердловский тракт", userLat, userLon)
        assertNotNull("Resolved station should not be null", resolved)
        assertTrue("Address should contain Свердловский: ${resolved?.address}", resolved?.address?.contains("Свердловский") == true)

        val distKm = com.navrot.aifuelassistant.geo.GeoUtils.calculateDistance(
            userLat, userLon, resolved!!.latitude, resolved.longitude
        )
        assertTrue("Distance should be under 5km, got $distKm", distKm < 5.0)

        val nearestGpn = resolver.nearestByBrand("Газпромнефть", userLat, userLon)
        assertNotNull("Nearest GPN should not be null", nearestGpn)
        assertEquals("Газпромнефть", nearestGpn?.brand)
    }

    @Test
    fun `resolveQuery for Sverdlovsky Trakt finds station and mock OSRM builds route to it`() = runTest {
        val resolved = resolver.resolveQuery("Свердловский тракт", userLat, userLon)
        assertNotNull(resolved)
        assertEquals(12, resolved?.id)

        val mockOptionData = RouteOptionData(
            points = listOf(listOf(userLat, userLon), listOf(resolved!!.latitude, resolved.longitude)),
            distanceMeters = 3200.0,
            durationSeconds = 300.0
        )
        val mockRouteResponse = mock<RouteResponse>()
        whenever(mockRouteResponse.getRouteOptions()).thenReturn(listOf(mockOptionData))
        whenever(mockFuelApi.getRoute(eq(userLon), eq(userLat), eq(resolved.longitude), eq(resolved.latitude), eq(false)))
            .thenReturn(Result.success(mockRouteResponse))

        mapRouteDelegate.buildRouteTo(this, resolved, userLat to userLon) { fail("Error callback called: $it") }
        advanceUntilIdle()

        val routeState = mapRouteDelegate.route.value
        assertNotNull(routeState)
        assertEquals("3.2 км", routeState?.distanceText)
        assertEquals("5 мин", routeState?.durationText)
        assertFalse(routeState!!.isStraightLine)
    }

    @Test
    fun `route to NO_FUEL station builds route and adds warning in AI chat response`() = runTest {
        val mockAiRouter = mock<com.navrot.aifuelassistant.ai.router.AiRouter>()
        val aiChatDelegate = AiChatDelegate(
            aiRouter = mockAiRouter,
            routeStateManager = routeStateManager,
            gasStationRepository = mockRepository,
            stationResolver = resolver,
            applicationContext = mockContext
        )

        val now = System.currentTimeMillis()
        val noFuelStation = sverdlovskyStation.copy(
            dataSources = setOf(FuelDataSource.RUSSIABASE),
            fuelTypes = listOf(FuelPrice("АИ-95", 60.0, available = false, updatedAt = now))
        )
        whenever(mockRepository.getNearbyStations(any(), any(), any()))
            .thenReturn(listOf(noFuelStation, kurchatovaStation))
        whenever(mockRepository.getAllStations())
            .thenReturn(listOf(noFuelStation, kurchatovaStation))

        whenever(mockAiRouter.ask(any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("LLM offline"))

        val mockRecommendationDelegate = mock<StationRecommendationDelegate>()
        whenever(mockRecommendationDelegate.stations)
            .thenReturn(MutableStateFlow(listOf(noFuelStation, kurchatovaStation)))

        aiChatDelegate.updateUserLocation(userLat, userLon)
        aiChatDelegate.setUserQuestion("Маршрут на Свердловский тракт")
        aiChatDelegate.askUserQuestion(this, mockRecommendationDelegate)
        advanceUntilIdle()

        assertEquals(12, aiChatDelegate.pendingRouteStationId.value)
        val answer = aiChatDelegate.userAnswer.value
        assertNotNull("userAnswer should not be null", answer)
        assertTrue("Expected warning in answer: $answer", answer!!.contains("⚠️ по данным Russiabase топлива нет"))
    }
}
