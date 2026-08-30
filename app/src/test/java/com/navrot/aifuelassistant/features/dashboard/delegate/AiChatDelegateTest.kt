package com.navrot.aifuelassistant.features.dashboard.delegate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
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
    }

    private fun createDelegate(): AiChatDelegate {
        return AiChatDelegate(
            aiRouter = mockAiRouter,
            routeStateManager = mockRouteStateManager,
            gasStationRepository = mockGasStationRepository,
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
            applicationContext = mockContext
        )

        val mockRecommendationDelegate = mock<StationRecommendationDelegate>()
        val testStation = com.navrot.aifuelassistant.data.model.GasStation(
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
}
