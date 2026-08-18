package com.navrot.aifuelassistant.features

import android.content.Context
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val mockRecordRepo = mock<FuelRecordRepository>()
    private val mockVehicleRepo = mock<VehicleRepository>()
    private val mockStationRepo = mock<GasStationRepositoryInterface>()
    private val mockAiRouter = mock<AiRouter>()
    private val mockContext = mock<Context>()
    private val mockPrefs = mock<android.content.SharedPreferences>()
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockPrefs)
        whenever(mockPrefs.getString(any(), any())).thenReturn("[]")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): com.navrot.aifuelassistant.features.dashboard.DashboardViewModel {
        return com.navrot.aifuelassistant.features.dashboard.DashboardViewModel(
            fuelRecordRepository = mockRecordRepo,
            vehicleRepository = mockVehicleRepo,
            gasStationRepository = mockStationRepo,
            aiRouter = mockAiRouter,
            applicationContext = mockContext
        )
    }

    @Test
    fun `metrics empty records returns zeros`() = runTest {
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        val metrics = vm.metrics.value
        assertEquals(0, metrics.fillCount)
        assertEquals(0f, metrics.consumption, 0.01f)
    }

    @Test
    fun `metrics computes consumption correctly`() = runTest {
        val vehicle = VehicleEntity(
            id = 1, name = "Test", brand = "T", model = "M",
            year = 2024, fuelType = "АИ-95"
        )
        val records = listOf(
            FuelRecordEntity(id = 1, vehicleId = 1, date = 2000L, mileage = 500.0, fuelAmount = 40.0, pricePerLiter = 45.0, totalCost = 1800.0, fuelType = "АИ-95"),
            FuelRecordEntity(id = 2, vehicleId = 1, date = 1000L, mileage = 300.0, fuelAmount = 40.0, pricePerLiter = 44.0, totalCost = 1760.0, fuelType = "АИ-95")
        )

        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(records))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(records))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(listOf(vehicle)))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        val metrics = vm.metrics.value
        assertEquals(2, metrics.fillCount)
        // (40 liters / 200 km) * 100 = 20 l/100km
        assertEquals(20f, metrics.consumption, 0.5f)
    }

    @Test
    fun `greeting question responds immediately without calling ai router`() = runTest {
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setUserQuestion("привет!")
        vm.askUserQuestion()
        advanceUntilIdle()

        val messages = vm.chatMessages.value
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("привет!", messages[0].text)
        assertEquals("ai", messages[1].role)
        assertTrue(messages[1].text.contains("Привет! Я AI-помощник по топливу"))

        verifyNoInteractions(mockAiRouter)
    }
}