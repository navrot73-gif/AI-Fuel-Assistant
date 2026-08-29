package com.navrot.aifuelassistant.features.dashboard.delegate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.features.dashboard.ChatMessage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

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
}
