package com.navrot.aifuelassistant.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.navrot.aifuelassistant.data.datasource.OverpassFuelProviderImpl
import com.navrot.aifuelassistant.data.datasource.RussiabaseProviderImpl
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.network.FuelApiImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Интеграционные приёмочные тесты для проверки поведения приложения на устройстве/эмуляторе:
 *
 * Сценарий A: Авиарежим (offline / cold start) → карта показывает >= 100 пинов из реестра, firstEmitMs < 1000.
 * Сценарий B: Сеть доступна, но внешние флаги выключены → activeSources = "registry",
 *            в логах и сетевых вызовах нет обращений к внешним источникам.
 * Сценарий C: Построение маршрута к станции из реестра — OSRM endpoint принимает точные координаты
 *            станции из реестра без их переписывания или искажения.
 */
@RunWith(AndroidJUnit4::class)
class DeviceScenarioTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        MapDiagnosticsTracker.resetStartupTimings()
    }

    /**
     * Сценарий A: Включён авиарежим (отсутствует сеть).
     * Cold start приложения должен выдавать мгновенную первую эмиссию (< 1000мс)
     * и отображать не менее 100 станций из локального реестра (assets/cache).
     */
    @Test
    fun scenarioA_airplaneMode_coldStart_showsAtLeast100Pins_firstEmitLessThan1000ms() = runBlocking {
        // Создаём HTTP клиент, который имитирует отсутствие сети (авиарежим)
        val offlineHttpClient = OkHttpClient.Builder()
            .addInterceptor {
                throw IOException("Airplane mode enabled - network unavailable")
            }
            .build()

        val userPrices = UserPriceRepository(context)
        val getBestStationsUseCase = GetBestStationsUseCase()
        val benzonavtProvider = BenzonavtProvider(offlineHttpClient)
        val overpassProvider = OverpassFuelProviderImpl(offlineHttpClient)
        val russiabaseProvider = RussiabaseProviderImpl(offlineHttpClient, context)
        val userPrefs = UserPreferencesRepository(context)

        val repository = GasStationRepository(
            context = context,
            httpClient = offlineHttpClient,
            userPrices = userPrices,
            getBestStationsUseCase = getBestStationsUseCase,
            benzonavtProvider = benzonavtProvider,
            appScope = CoroutineScope(Dispatchers.IO),
            overpassFuelProvider = overpassProvider,
            russiabaseProvider = russiabaseProvider,
            userPreferencesRepository = userPrefs
        )

        MapDiagnosticsTracker.resetStartupTimings()

        val stations = repository.getNearbyStationsFlow(55.1608, 61.3989, 50.0).first()

        assertTrue(
            "В режиме офлайн/авиарежима карта должна содержать >= 100 пинов из реестра, фактически: ${stations.size}",
            stations.size >= 100
        )

        val firstEmitMs = MapDiagnosticsTracker.firstEmitMs
        assertTrue(
            "При cold start в офлайне первая эмиссия должна занимать < 1000мс, фактически: ${firstEmitMs}мс",
            firstEmitMs < 1000L
        )
    }

    /**
     * Сценарий B: Сеть есть, но все флаги внешних источников выключены.
     * Проверяем, что activeSources = "registry" и сетевые запросы к внешним провайдерам не выполняются.
     */
    @Test
    fun scenarioB_networkAvailable_allFlagsDisabled_activeSourcesIsRegistry_noExternalCalls() = runBlocking {
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

        val userPrices = UserPriceRepository(context)
        val getBestStationsUseCase = GetBestStationsUseCase()
        val benzonavtProvider = BenzonavtProvider(interceptingClient)
        val overpassProvider = OverpassFuelProviderImpl(interceptingClient)
        val russiabaseProvider = RussiabaseProviderImpl(interceptingClient, context)

        val repository = GasStationRepository(
            context = context,
            httpClient = interceptingClient,
            userPrices = userPrices,
            getBestStationsUseCase = getBestStationsUseCase,
            benzonavtProvider = benzonavtProvider,
            appScope = CoroutineScope(Dispatchers.IO),
            overpassFuelProvider = overpassProvider,
            russiabaseProvider = russiabaseProvider,
            userPreferencesRepository = userPrefs
        )

        val stations = repository.triggerEnrichment(55.1608, 61.3989)
        assertTrue("Реестр станций должен быть заполнен", stations.isNotEmpty())

        val activeSources = MapDiagnosticsTracker.activeSources
        assertEquals("При выключенных флагах activeSources должен быть 'registry'", "registry", activeSources)

        val externalCalls = requestedUrls.filter { url ->
            url.contains("benzonavt") || url.contains("russiabase") || url.contains("overpass") || url.contains("openstreetmap")
        }
        assertTrue(
            "При выключенных флагах не должно быть сетевых запросов к внешним источникам. Фактически вызовы: $externalCalls",
            externalCalls.isEmpty()
        )
    }

    /**
     * Сценарий C: Построение маршрута к станции из реестра.
     * Проверяем, что запрос маршрутизации (OSRM) принимает ровно те координаты, которые зафиксированы в реестре.
     */
    @Test
    fun scenarioC_routeToRegistryStation_endpointReceivesExactCoordinates() = runBlocking {
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

        val userPrices = UserPriceRepository(context)
        val getBestStationsUseCase = GetBestStationsUseCase()
        val benzonavtProvider = BenzonavtProvider(interceptingClient)

        val repository = GasStationRepository(
            context = context,
            httpClient = interceptingClient,
            userPrices = userPrices,
            getBestStationsUseCase = getBestStationsUseCase,
            benzonavtProvider = benzonavtProvider,
            appScope = CoroutineScope(Dispatchers.IO)
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

        assertTrue("Запрос маршрута должен успешно завершиться", result.isSuccess)
        assertTrue("Запрос к OSRM должен быть отправлен", capturedUrls.isNotEmpty())

        val requestUrl = capturedUrls.first()
        val expectedCoordSubString = "$fromLon,$fromLat;${targetStation.longitude},${targetStation.latitude}"

        assertTrue(
            "URL маршрута $requestUrl должен содержать точные координаты из реестра: $expectedCoordSubString",
            requestUrl.contains(expectedCoordSubString)
        )
    }
}
