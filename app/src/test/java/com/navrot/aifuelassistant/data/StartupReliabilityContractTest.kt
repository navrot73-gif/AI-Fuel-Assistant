package com.navrot.aifuelassistant.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.data.datasource.OverpassFuelProvider
import com.navrot.aifuelassistant.data.datasource.RussiabaseProvider
import com.navrot.aifuelassistant.data.datasource.StationCache
import com.navrot.aifuelassistant.data.datasource.StationFilterAndSorter
import com.navrot.aifuelassistant.data.datasource.StationLoader
import com.navrot.aifuelassistant.data.datasource.StationPriceApplier
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StartupReliabilityContractTest {

    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val sampleStation = GasStation(
        id = 1,
        name = "Test Station",
        brand = "TestBrand",
        address = "Test Address",
        latitude = 55.16,
        longitude = 61.43,
        fuelTypes = listOf(FuelPrice("АИ-95", 55.0, true)),
        queueTime = 0,
        reliability = 100
    )

    private lateinit var mockStationLoader: StationLoader
    private lateinit var mockStationCache: StationCache
    private lateinit var mockStationPriceApplier: StationPriceApplier
    private lateinit var mockStationFilterAndSorter: StationFilterAndSorter
    private lateinit var mockUserPrices: UserPriceRepository
    private lateinit var mockBenzonavtProvider: BenzonavtProvider
    private lateinit var mockOverpassFuelProvider: OverpassFuelProvider
    private lateinit var mockRussiabaseProvider: RussiabaseProvider
    private lateinit var mockGetBestStationsUseCase: GetBestStationsUseCase

    private lateinit var repository: GasStationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MapDiagnosticsTracker.resetStartupTimings()

        mockStationLoader = mock()
        mockStationCache = mock()
        mockStationPriceApplier = mock()
        mockStationFilterAndSorter = mock()
        mockUserPrices = mock()
        mockBenzonavtProvider = mock()
        mockOverpassFuelProvider = mock()
        mockRussiabaseProvider = mock()
        mockGetBestStationsUseCase = mock()

        runTest {
            whenever(mockStationLoader.loadFromCache()).doReturn(listOf(sampleStation))
            whenever(mockStationLoader.loadFromAssets()).doReturn(listOf(sampleStation))
            whenever(mockStationPriceApplier.applyUserPrices(any())).doReturn(listOf(sampleStation))
            whenever(mockStationPriceApplier.applyAllPrices(any())).doReturn(listOf(sampleStation))
            whenever(mockStationFilterAndSorter.getStationsNearLocation(any(), any(), any(), any()))
                .doReturn(listOf(sampleStation))
            whenever(mockOverpassFuelProvider.fetchStations(any(), any(), any())).doReturn(emptyList())
            whenever(mockRussiabaseProvider.fetchObservations(any(), any(), any(), any())).doReturn(emptyList())
        }

        repository = GasStationRepository(
            stationLoader = mockStationLoader,
            stationCache = mockStationCache,
            stationPriceApplier = mockStationPriceApplier,
            stationFilterAndSorter = mockStationFilterAndSorter,
            userPrices = mockUserPrices,
            benzonavtProvider = mockBenzonavtProvider,
            overpassFuelProvider = mockOverpassFuelProvider,
            russiabaseProvider = mockRussiabaseProvider,
            getBestStationsUseCase = mockGetBestStationsUseCase,
            appScope = testScope
        )
    }

    @Test
    fun `verify emit1 and emit2 timing budget recorded in diagnostics tracker`() = runTest {
        val result = repository.getAllStations()
        assertTrue(result.isNotEmpty())
        assertTrue("emit1Ms should be <= 100ms", MapDiagnosticsTracker.emit1Ms <= 100)
        assertTrue("emit2Ms should be <= 3000ms", MapDiagnosticsTracker.emit2Ms <= 3000)
    }

    @Test
    fun `verify getNearbyStationsFlow emits initial local cached station immediately`() = runTest {
        val list = repository.getNearbyStationsFlow(55.16, 61.43, 50.0).first()
        assertEquals(1, list.size)
        assertEquals("Test Station", list.first().name)
    }
}
