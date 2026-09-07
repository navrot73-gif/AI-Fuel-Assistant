package com.navrot.aifuelassistant.features.dashboard.delegate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.datasource.RussiabaseHtmlParser
import com.navrot.aifuelassistant.data.datasource.RussiabaseMatcher
import com.navrot.aifuelassistant.data.datasource.StationJsonParserImpl
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.geo.GeoUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GoldenAcceptanceTest {

    private lateinit var context: Context
    private val mockAiRouter = mock<AiRouter>()
    private val mockRouteStateManager = mock<RouteStateManager>()
    private val mockGasStationRepository = mock<GasStationRepositoryInterface>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("chat_history", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("chat_history_encrypted", Context.MODE_PRIVATE).edit().clear().commit()

        runBlocking {
            whenever(mockAiRouter.ask(any(), any(), any(), any(), any())).thenAnswer {
                throw IllegalStateException("LLM call forbidden for local intent query")
            }
        }
    }

    @Test
    fun goldenAcceptanceTest_nearestBrandLocalSelectionAndRedPins() = runTest {
        val jsonString = context.assets.open("stations.json").bufferedReader().use { it.readText() }
        val baseStations = StationJsonParserImpl().parseJson(jsonString)

        val russiabaseFixture = """
            Газпромнефть №201 / Челябинск, Свердловский тракт, 12В / Аи-92 Доступно, Аи-95 Отсутствует, ДТ Доступно
            Газпромнефть №260 / Челябинск, Курчатова, 2/1 / АЗС закрыта, Топлива нет
        """.trimIndent()

        val observations = RussiabaseHtmlParser.parseHtml(russiabaseFixture, "ai95")
        val mergedStations = RussiabaseMatcher.applyObservations(baseStations, observations)

        val userLat = 55.1608
        val userLon = 61.3989

        whenever(mockGasStationRepository.getNearbyStations(userLat, userLon, 60.0)).thenReturn(mergedStations)
        whenever(mockGasStationRepository.getAllStations()).thenReturn(mergedStations)

        val recommendationDelegate = mock<StationRecommendationDelegate>()
        whenever(recommendationDelegate.stations).thenReturn(MutableStateFlow(mergedStations))

        val delegate = AiChatDelegate(
            aiRouter = mockAiRouter,
            routeStateManager = mockRouteStateManager,
            gasStationRepository = mockGasStationRepository,
            applicationContext = context
        )
        delegate.updateUserLocation(userLat, userLon)

        // --- Query 1: Газпромнефть ---
        delegate.setUserQuestion("Построй маршрут до ближайшей заправочной станции Газпромнефть")
        delegate.askUserQuestion(this, recommendationDelegate)
        advanceUntilIdle()

        val answer1 = delegate.userAnswer.value
        assertNotNull("AI answer must not be null", answer1)
        assertTrue("Answer MUST contain 'Свердловский'", answer1!!.contains("Свердловский"))
        assertFalse("Answer MUST NOT contain 'Магнитогорск'", answer1.contains("Магнитогорск"))
        assertFalse("Answer MUST NOT contain 'Москва'", answer1.contains("Москва"))
        assertFalse("Answer MUST NOT contain 'Курчатова'", answer1.contains("Курчатова"))

        val targetStationId = delegate.pendingRouteStationId.value
        assertNotNull("Pending route station ID must be set", targetStationId)

        val targetStation = mergedStations.find { it.id == targetStationId }
        assertNotNull("Target station must exist in merged stations list", targetStation)
        assertTrue("Target station address must be Sverdlovsky Trakt", targetStation!!.address.contains("Свердловский тракт"))

        val distKm = GeoUtils.calculateDistance(userLat, userLon, targetStation.latitude, targetStation.longitude)
        assertTrue("Distance to target station must be < 5km", distKm < 5.0)

        val ai95Status = PriceReliabilityCalculator.calculateFuelAvailability(targetStation, "АИ-95")
        assertEquals("Target station AI-95 status must be NO_FUEL (🔴)", FuelAvailabilityStatus.NO_FUEL, ai95Status)

        val containsWarning = answer1.contains("топлива нет") || answer1.contains("нет топлива")
        assertTrue("Answer MUST contain warning about no fuel ('топлива нет')", containsWarning)
        assertEquals("ai_path must be 'local'", "local", MapDiagnosticsTracker.aiPath)

        // --- Query 2: Лукойл ---
        delegate.setUserQuestion("Построй маршрут до ближайшей заправочной станции Лукойл")
        delegate.askUserQuestion(this, recommendationDelegate)
        advanceUntilIdle()

        val answer2 = delegate.userAnswer.value
        assertNotNull("AI answer for Lukoil must not be null", answer2)
        assertTrue("Answer MUST contain 'Мира'", answer2!!.contains("Мира"))

        val lukoilStationId = delegate.pendingRouteStationId.value
        assertNotNull("Pending route station ID for Lukoil must be set", lukoilStationId)

        val lukoilStation = mergedStations.find { it.id == lukoilStationId }
        assertNotNull("Lukoil station must exist", lukoilStation)

        val lukoilDistKm = GeoUtils.calculateDistance(userLat, userLon, lukoilStation!!.latitude, lukoilStation.longitude)
        assertTrue("Distance to Lukoil station must be < 3km", lukoilDistKm < 3.0)
    }
}
