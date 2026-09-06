package com.navrot.aifuelassistant.features.dashboard.delegate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.stations.StationResolver
import com.navrot.aifuelassistant.features.dashboard.ChatMessage
import org.junit.Assert.*
import org.junit.Before
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AiChatDelegateTest {

    private lateinit var context: Context
    private val mockAiRouter = mock<AiRouter>()
    private val mockRouteStateManager = mock<RouteStateManager>()
    private val mockGasStationRepository = mock<GasStationRepositoryInterface>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear shared preferences before each test
        context.getSharedPreferences("chat_history", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("chat_history_encrypted", Context.MODE_PRIVATE).edit().clear().commit()
        kotlinx.coroutines.runBlocking {
            org.mockito.kotlin.whenever(mockGasStationRepository.getNearbyStations(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(emptyList())
            org.mockito.kotlin.whenever(mockGasStationRepository.getAllStations()).thenReturn(emptyList())
            org.mockito.kotlin.whenever(mockGasStationRepository.searchStations(org.mockito.kotlin.any())).thenReturn(emptyList())
        }
    }

    private fun createDelegate(): AiChatDelegate {
        return AiChatDelegate(
            aiRouter = mockAiRouter,
            routeStateManager = mockRouteStateManager,
            gasStationRepository = mockGasStationRepository,
            stationResolver = StationResolver(mockGasStationRepository),
            applicationContext = context
        )
    }

    @Test
    fun `legacy chat history is migrated on init`() {
        val legacyPrefs = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
        val legacyJson = """[{"role":"user","text":"Привет","ts":1000}]"""
        legacyPrefs.edit().putString("messages", legacyJson).commit()

        val delegate = createDelegate()

        assertEquals(1, delegate.chatMessages.value.size)
        assertEquals("user", delegate.chatMessages.value[0].role)
        assertEquals("Привет", delegate.chatMessages.value[0].text)
    }

    @Test
    fun `addChatMessage and clearChatHistory persist correctly`() {
        val delegate = createDelegate()
        assertEquals(0, delegate.chatMessages.value.size)

        delegate.addChatMessage(ChatMessage("user", "Как погода?", 2000))
        assertEquals(1, delegate.chatMessages.value.size)

        // Re-create delegate to verify persistence
        val delegate2 = createDelegate()
        assertEquals(1, delegate2.chatMessages.value.size)
        assertEquals("Как погода?", delegate2.chatMessages.value[0].text)

        delegate2.clearChatHistory()
        assertEquals(0, delegate2.chatMessages.value.size)

        val delegate3 = createDelegate()
        assertEquals(0, delegate3.chatMessages.value.size)
    }

    @Test
    fun `askUserQuestion with ROUTE tag sets pendingRouteStationId for station card display`() = kotlinx.coroutines.test.runTest {
        val mockContext = mock<Context>()
        val mockPrefs = mock<android.content.SharedPreferences>()
        org.mockito.kotlin.whenever(mockContext.getSharedPreferences(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(mockPrefs)
        org.mockito.kotlin.whenever(mockPrefs.getString(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn("[]")

        val delegate = AiChatDelegate(
            aiRouter = mockAiRouter,
            routeStateManager = mockRouteStateManager,
            gasStationRepository = mockGasStationRepository,
            stationResolver = StationResolver(mockGasStationRepository),
            applicationContext = mockContext
        )

        val mockRecommendationDelegate = mock<StationRecommendationDelegate>()
        val testStation = GasStation(
            id = 7,
            name = "Газпромнефть",
            brand = "Газпромнефть",
            address = "ул. Победы",
            latitude = 55.1,
            longitude = 61.4,
            fuelTypes = emptyList(),
            queueTime = 0,
            reliability = 95
        )
        org.mockito.kotlin.whenever(mockRecommendationDelegate.stations).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(listOf(testStation)))
        org.mockito.kotlin.whenever(mockAiRouter.ask(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn("Маршрут построен [ROUTE:7]")

        delegate.setUserQuestion("Построй маршрут до ближайшей Газпромнефть")
        delegate.askUserQuestion(this, mockRecommendationDelegate)
        testScheduler.advanceUntilIdle()

        assertEquals(7, delegate.pendingRouteStationId.value)
        assertEquals(
            com.navrot.aifuelassistant.features.dashboard.DashboardViewModel.PendingRouteMode.ROUTE,
            delegate.pendingRouteMode.value
        )
    }

    @Test
    fun `detectIntent routes to nearest brand station even if NO_FUEL`() = kotlinx.coroutines.test.runTest {
        val mockContext = mock<Context>()
        val mockPrefs = mock<android.content.SharedPreferences>()
        org.mockito.kotlin.whenever(mockContext.getSharedPreferences(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(mockPrefs)
        org.mockito.kotlin.whenever(mockPrefs.getString(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn("[]")

        val delegate = AiChatDelegate(
            aiRouter = mockAiRouter,
            routeStateManager = mockRouteStateManager,
            gasStationRepository = mockGasStationRepository,
            stationResolver = StationResolver(mockGasStationRepository),
            applicationContext = mockContext
        )

        val mockRecommendationDelegate = mock<StationRecommendationDelegate>()
        val now = System.currentTimeMillis()
        val nearestNoFuel = GasStation(
            id = 1,
            name = "Лукойл Ближняя",
            brand = "Лукойл",
            address = "ул. Ближняя 1",
            latitude = 55.01,
            longitude = 61.01,
            fuelTypes = listOf(FuelPrice("АИ-95", 50.0, available = false, updatedAt = now)),
            queueTime = 0,
            reliability = 90
        )
        val farAvailable = GasStation(
            id = 2,
            name = "Лукойл Дальняя",
            brand = "Лукойл",
            address = "ул. Дальняя 10",
            latitude = 55.10,
            longitude = 61.10,
            fuelTypes = listOf(FuelPrice("АИ-95", 52.0, available = true, updatedAt = now)),
            queueTime = 0,
            reliability = 90
        )
        org.mockito.kotlin.whenever(mockRecommendationDelegate.stations).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(listOf(nearestNoFuel, farAvailable)))
        org.mockito.kotlin.whenever(mockAiRouter.ask(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn("Ближайшая Лукойл: ул. Ближняя 1, ⚠️ по меткам нет топлива. Альтернатива с топливом: Лукойл на ул. Дальняя 10 (52.0 ₽).")

        delegate.updateUserLocation(55.0, 61.0)
        delegate.setUserQuestion("где ближайшая Лукойл")
        delegate.askUserQuestion(this, mockRecommendationDelegate)
        testScheduler.advanceUntilIdle()

        assertEquals(1, delegate.pendingRouteStationId.value)
    }

    @Test
    fun `buildUserContext includes OSM-only stations with negative ids and excludes raw id tags from answer prompt guidance`() = kotlinx.coroutines.test.runTest {
        val delegate = createDelegate()

        val mockRecommendationDelegate = mock<StationRecommendationDelegate>()
        val staticStation = GasStation(
            id = 101,
            name = "Газпромнефть Статическая",
            brand = "Газпромнефть",
            address = "ул. Курчатова, 2/1",
            latitude = 55.16,
            longitude = 61.43,
            fuelTypes = listOf(FuelPrice("АИ-95", 62.0, available = true)),
            queueTime = 0,
            reliability = 90
        )
        val osmOnlyStation = GasStation(
            id = -5001,
            name = "Газпромнефть OSM",
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12в",
            latitude = 55.15001,
            longitude = 61.40001,
            fuelTypes = listOf(FuelPrice("АИ-95", 61.5, available = true)),
            queueTime = 0,
            reliability = 90
        )

        org.mockito.kotlin.whenever(mockGasStationRepository.getNearbyStations(55.15, 61.40, 50.0))
            .thenReturn(listOf(osmOnlyStation, staticStation))
        org.mockito.kotlin.whenever(mockRecommendationDelegate.stations)
            .thenReturn(kotlinx.coroutines.flow.MutableStateFlow(listOf(osmOnlyStation, staticStation)))

        var capturedPrompt = ""
        org.mockito.kotlin.whenever(mockAiRouter.ask(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenAnswer { invocation ->
                capturedPrompt = invocation.getArgument(0)
                "Газпромнефть, Свердловский тракт, 12в — 61.5₽, 🟢 есть топливо [ROUTE:-5001]"
            }

        delegate.updateUserLocation(55.15, 61.40)
        delegate.setUserQuestion("Расскажи про олефиновые добавки")
        delegate.askUserQuestion(this, mockRecommendationDelegate)
        testScheduler.advanceUntilIdle()

        assertTrue(capturedPrompt.contains("Свердловский тракт, 12в"))
        assertTrue(capturedPrompt.contains("[ROUTE:-5001]"))
        assertFalse(capturedPrompt.contains("[-5001] Газпромнефть"))
    }

    @Test
    fun `intent nearest Gazpromneft with LLM mock returning Kurchatova results in Sverdlovsky local override and ai_path local`() = kotlinx.coroutines.test.runTest {
        val delegate = createDelegate()
        val mockRecommendationDelegate = mock<StationRecommendationDelegate>()

        val sverdlovskyStation = GasStation(
            id = 12,
            name = "Газпромнефть Свердловский",
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12в",
            latitude = 55.1847,
            longitude = 61.4098,
            fuelTypes = listOf(FuelPrice("АИ-95", 61.5, available = true)),
            queueTime = 0,
            reliability = 90
        )
        val kurchatovaStation = GasStation(
            id = 10,
            name = "Газпромнефть Курчатова",
            brand = "Газпромнефть",
            address = "ул. Курчатова, 2/1",
            latitude = 55.1500,
            longitude = 61.4200,
            fuelTypes = listOf(FuelPrice("АИ-95", 62.0, available = true)),
            queueTime = 0,
            reliability = 90
        )

        val stationsList = listOf(sverdlovskyStation, kurchatovaStation)
        org.mockito.kotlin.whenever(mockGasStationRepository.getNearbyStations(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(stationsList)
        org.mockito.kotlin.whenever(mockRecommendationDelegate.stations)
            .thenReturn(kotlinx.coroutines.flow.MutableStateFlow(stationsList))

        // Mock LLM to return Kurchatova station
        org.mockito.kotlin.whenever(mockAiRouter.ask(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn("Газпромнефть, ул. Курчатова, 2/1 — 62₽, 🟢 есть топливо [ROUTE:10]")

        delegate.updateUserLocation(55.18, 61.40)
        delegate.setUserQuestion("ближайшая Газпромнефть")
        delegate.askUserQuestion(this, mockRecommendationDelegate)
        testScheduler.advanceUntilIdle()

        assertEquals("local", com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker.aiPath)
        assertEquals(12, delegate.pendingRouteStationId.value)
        assertTrue(delegate.userAnswer.value!!.contains("Свердловский"))
    }

    @Test
    fun `route query for Sverdlovsky Trakt finds station by address about 3km away`() = kotlinx.coroutines.test.runTest {
        val delegate = createDelegate()
        val mockRecommendationDelegate = mock<StationRecommendationDelegate>()

        val sverdlovskyStation = GasStation(
            id = 12,
            name = "Татнефть Свердловский",
            brand = "Татнефть",
            address = "Свердловский тракт, 40/1",
            latitude = 55.1847,
            longitude = 61.4098,
            fuelTypes = listOf(FuelPrice("АИ-95", 60.0, available = true)),
            queueTime = 0,
            reliability = 90
        )
        val stationsList = listOf(sverdlovskyStation)
        org.mockito.kotlin.whenever(mockGasStationRepository.getNearbyStations(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(stationsList)
        org.mockito.kotlin.whenever(mockRecommendationDelegate.stations)
            .thenReturn(kotlinx.coroutines.flow.MutableStateFlow(stationsList))

        delegate.updateUserLocation(55.1644, 61.4368)
        delegate.setUserQuestion("маршрут до АЗС на Свердловском тракте")
        delegate.askUserQuestion(this, mockRecommendationDelegate)
        testScheduler.advanceUntilIdle()

        assertEquals("local", com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker.aiPath)
        assertEquals(12, delegate.pendingRouteStationId.value)
        assertTrue(delegate.userAnswer.value!!.contains("Свердловский"))
    }

    @Test
    fun `nearestByBrand UNKNOWN status beats AVAILABLE status when closer`() = kotlinx.coroutines.test.runTest {
        val resolver = StationResolver(mockGasStationRepository)
        val userLat = 55.1644
        val userLon = 61.4368

        val unknownCloseStation = GasStation(
            id = 101,
            name = "Газпромнефть Ближняя",
            brand = "Газпромнефть",
            address = "ул. Ближняя, 5",
            latitude = 55.1800,
            longitude = 61.4000,
            fuelTypes = listOf(FuelPrice("АИ-95", 60.0, available = false, updatedAt = 0L)),
            queueTime = 0,
            reliability = 50
        )

        val availableFarStation = GasStation(
            id = 102,
            name = "Газпромнефть Дальняя",
            brand = "Газпромнефть",
            address = "ул. Дальняя, 100",
            latitude = 55.2500,
            longitude = 61.5000,
            fuelTypes = listOf(FuelPrice("АИ-95", 62.0, available = true, updatedAt = System.currentTimeMillis())),
            queueTime = 0,
            reliability = 90
        )

        val stations = listOf(unknownCloseStation, availableFarStation)
        org.mockito.kotlin.whenever(mockGasStationRepository.getNearbyStations(userLat, userLon, 50.0))
            .thenReturn(stations)

        val result = resolver.nearestByBrand("газпромнефть", userLat, userLon, fallbackStations = stations)

        assertNotNull(result)
        assertEquals(101, result?.id)
        val dist = com.navrot.aifuelassistant.geo.GeoUtils.calculateDistance(userLat, userLon, result!!.latitude, result.longitude)
        assertTrue("Distance should be ~3.35 km", dist in 2.5..4.0)
    }

    @Test
    fun `askUserQuestion for route query falls back to local intent when LLM throws error`() = kotlinx.coroutines.test.runTest {
        val mockContext = mock<Context>()
        val mockPrefs = mock<android.content.SharedPreferences>()
        org.mockito.kotlin.whenever(mockContext.getSharedPreferences(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(mockPrefs)
        org.mockito.kotlin.whenever(mockPrefs.getString(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn("[]")

        val delegate = AiChatDelegate(
            aiRouter = mockAiRouter,
            routeStateManager = mockRouteStateManager,
            gasStationRepository = mockGasStationRepository,
            stationResolver = StationResolver(mockGasStationRepository),
            applicationContext = mockContext
        )

        val mockRecommendationDelegate = mock<StationRecommendationDelegate>()
        val station = GasStation(
            id = 55,
            name = "Газпромнефть",
            brand = "Газпромнефть",
            address = "ул. Свердловский тракт, 5",
            latitude = 55.18,
            longitude = 61.38,
            fuelTypes = listOf(FuelPrice("АИ-95", 61.0, available = true)),
            queueTime = 0,
            reliability = 90
        )
        org.mockito.kotlin.whenever(mockRecommendationDelegate.stations).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(listOf(station)))
        org.mockito.kotlin.whenever(mockAiRouter.ask(org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.anyOrNull(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenThrow(RuntimeException("LLM offline"))

        delegate.updateUserLocation(55.18, 61.38)
        delegate.setUserQuestion("Построй маршрут на Газпромнефть")
        delegate.askUserQuestion(this, mockRecommendationDelegate)
        testScheduler.advanceUntilIdle()

        assertEquals("local", com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker.aiPath)
        assertNull(delegate.error.value)
        assertNotNull(delegate.userAnswer.value)
        assertTrue(delegate.userAnswer.value!!.contains("Газпромнефть"))
        assertEquals(55, delegate.pendingRouteStationId.value)
        assertEquals(com.navrot.aifuelassistant.features.dashboard.DashboardViewModel.PendingRouteMode.ROUTE, delegate.pendingRouteMode.value)
    }
}
