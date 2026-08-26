package com.navrot.aifuelassistant.features

import android.content.Context
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
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
            routeStateManager = RouteStateManager(),
            getBestStationsUseCase = com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase(),
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
        assertTrue(vm.weeklyConsumption.value.isEmpty())
    }

    @Test
    fun `refuel on sunday no crash and correctly recorded`() = runTest {
        // Oct 16 2023 (Monday) -> Oct 22 2023 (Sunday)
        val monDate = 1697450400000L
        val sunDate = 1697968800000L
        val vehicle = VehicleEntity(id = 1, name = "Car", brand = "B", model = "M", year = 2020, fuelType = "АИ-95")
        val records = listOf(
            FuelRecordEntity(id = 1, vehicleId = 1, date = monDate, mileage = 1000.0, fuelAmount = 40.0, pricePerLiter = 50.0, totalCost = 2000.0, fuelType = "АИ-95"),
            FuelRecordEntity(id = 2, vehicleId = 1, date = sunDate, mileage = 1400.0, fuelAmount = 40.0, pricePerLiter = 50.0, totalCost = 2000.0, fuelType = "АИ-95")
        )

        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(records))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(records))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(listOf(vehicle)))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        val weekly = vm.weeklyConsumption.value
        assertNotNull(weekly)
        val sunEntry = weekly.find { it.first == "Вс" }
        assertNotNull(sunEntry)
        assertEquals(10.0f, sunEntry!!.second, 0.1f)
    }

    @Test
    fun `refuel on monday mapped to index 0`() = runTest {
        // Oct 15 2023 (Sunday) -> Oct 16 2023 (Monday)
        val sunDate = 1697364000000L
        val monDate = 1697450400000L
        val vehicle = VehicleEntity(id = 1, name = "Car", brand = "B", model = "M", year = 2020, fuelType = "АИ-95")
        val records = listOf(
            FuelRecordEntity(id = 1, vehicleId = 1, date = sunDate, mileage = 1000.0, fuelAmount = 40.0, pricePerLiter = 50.0, totalCost = 2000.0, fuelType = "АИ-95"),
            FuelRecordEntity(id = 2, vehicleId = 1, date = monDate, mileage = 1400.0, fuelAmount = 40.0, pricePerLiter = 50.0, totalCost = 2000.0, fuelType = "АИ-95")
        )

        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(records))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(records))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(listOf(vehicle)))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        val weekly = vm.weeklyConsumption.value
        assertNotNull(weekly)
        val monEntry = weekly.find { it.first == "Пн" }
        assertNotNull(monEntry)
        assertEquals(10.0f, monEntry!!.second, 0.1f)
    }

    @Test
    fun `two records with same odometer metrics zero no crash`() = runTest {
        val vehicle = VehicleEntity(id = 1, name = "Car", brand = "B", model = "M", year = 2020, fuelType = "АИ-95")
        val records = listOf(
            FuelRecordEntity(id = 1, vehicleId = 1, date = 1000L, mileage = 500.0, fuelAmount = 30.0, pricePerLiter = 50.0, totalCost = 1500.0, fuelType = "АИ-95"),
            FuelRecordEntity(id = 2, vehicleId = 1, date = 2000L, mileage = 500.0, fuelAmount = 40.0, pricePerLiter = 50.0, totalCost = 2000.0, fuelType = "АИ-95")
        )

        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(records))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(records))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(listOf(vehicle)))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        val metrics = vm.metrics.value
        assertEquals(2, metrics.fillCount)
        assertEquals(0f, metrics.consumption, 0.01f)
        assertEquals(0f, metrics.rubPerKm, 0.01f)
        assertTrue(vm.weeklyConsumption.value.isEmpty())
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
    fun `askUserQuestion greeting returns immediate response without AI router`() = runTest {
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setUserQuestion("Привет! Какая погода?")
        vm.askUserQuestion()
        advanceUntilIdle()

        val messages = vm.chatMessages.value
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("Привет! Какая погода?", messages[0].text)
        assertEquals("ai", messages[1].role)
        assertTrue(messages[1].text.startsWith("Привет! Я AI-помощник по топливу."))
        verifyNoInteractions(mockAiRouter)
    }

    @Test
    fun `askUserQuestion with ROUTE tag sets pendingRouteStationId and hides tag`() = runTest {
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())
        whenever(mockAiRouter.ask(any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn("Едем на заправку GetPetrol.\n[ROUTE:5]")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setUserQuestion("Построй маршрут до GetPetrol")
        vm.askUserQuestion()
        advanceUntilIdle()

        assertEquals(5, vm.pendingRouteStationId.value)
        assertEquals(com.navrot.aifuelassistant.features.dashboard.DashboardViewModel.PendingRouteMode.ROUTE, vm.pendingRouteMode.value)

        val messages = vm.chatMessages.value
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("ai", messages[1].role)
        assertEquals("Едем на заправку GetPetrol.", messages[1].text)
        assertFalse(messages[1].text.contains("[ROUTE:5]"))
    }

    @Test
    fun `askUserQuestion without ROUTE tag uses fallback detectIntent`() = runTest {
        val testStation = com.navrot.aifuelassistant.data.model.GasStation(
            id = 42,
            name = "Лукойл",
            brand = "Лукойл",
            address = "ул. Тестовая",
            latitude = 55.0,
            longitude = 37.0,
            fuelTypes = emptyList(),
            queueTime = 0,
            reliability = 100
        )
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        whenever(mockStationRepo.getAllStations()).thenReturn(listOf(testStation))
        whenever(mockAiRouter.ask(any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn("Попробуйте заправиться на Лукойле.")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setUserQuestion("Построй маршрут на Лукойл")
        vm.askUserQuestion()
        advanceUntilIdle()

        assertEquals(42, vm.pendingRouteStationId.value)
        assertEquals(com.navrot.aifuelassistant.features.dashboard.DashboardViewModel.PendingRouteMode.ROUTE, vm.pendingRouteMode.value)

        val messages = vm.chatMessages.value
        assertEquals(2, messages.size)
        assertEquals("Попробуйте заправиться на Лукойле.", messages[1].text)
    }

    @Test
    fun `askUserQuestion on AI error keeps user message and handles intent`() = runTest {
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())
        whenever(mockAiRouter.ask(any(), anyOrNull(), anyOrNull(), any(), any())).thenThrow(RuntimeException("401 Unauthorized"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setUserQuestion("Построй маршрут на АЗС")
        vm.askUserQuestion()
        advanceUntilIdle()

        val messages = vm.chatMessages.value
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("Построй маршрут на АЗС", messages[0].text)
        assertEquals("ai", messages[1].role)
        assertEquals("Что-то пошло не так. Попробуйте ещё раз.", messages[1].text)
        assertEquals("Что-то пошло не так. Попробуйте ещё раз.", vm.error.value)
    }

    @Test
    fun `askUserQuestion on Worker timeout posts AI fallback message and executes local intent`() = runTest {
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        val testStation = com.navrot.aifuelassistant.data.model.GasStation(
            id = 99,
            name = "Лукойл",
            brand = "Лукойл",
            address = "ул. Ленина",
            latitude = 55.0,
            longitude = 61.0,
            fuelTypes = emptyList(),
            queueTime = 0,
            reliability = 100
        )
        whenever(mockStationRepo.getAllStations()).thenReturn(listOf(testStation))
        whenever(mockAiRouter.ask(any(), anyOrNull(), anyOrNull(), any(), any())).thenAnswer {
            throw kotlinx.coroutines.TimeoutCancellationException::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }.newInstance("Timed out")
        }

        val vm = createViewModel()
        advanceUntilIdle()

        vm.setUserQuestion("Построй маршрут на Лукойл")
        vm.askUserQuestion()
        advanceUntilIdle()

        val messages = vm.chatMessages.value
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("Построй маршрут на Лукойл", messages[0].text)
        assertEquals("ai", messages[1].role)
        assertEquals("AI-помощник временно недоступен. Попробуйте позже.", messages[1].text)
        assertEquals(99, vm.pendingRouteStationId.value)
        assertEquals(com.navrot.aifuelassistant.features.dashboard.DashboardViewModel.PendingRouteMode.ROUTE, vm.pendingRouteMode.value)
    }

    @Test
    fun `askUserQuestion passes last 6 messages history to aiRouter`() = runTest {
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())
        whenever(mockAiRouter.ask(any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn("Ответ AI")

        val vm = createViewModel()
        advanceUntilIdle()

        // Add 8 previous messages
        for (i in 1..8) {
            vm.addChatMessage(
                com.navrot.aifuelassistant.features.dashboard.ChatMessage(
                    if (i % 2 == 1) "user" else "ai",
                    "Message $i"
                )
            )
        }

        vm.setUserQuestion("Где дешевле заправиться?")
        vm.askUserQuestion()
        advanceUntilIdle()

        val captor = argumentCaptor<List<com.navrot.aifuelassistant.features.dashboard.ChatMessage>>()
        verify(mockAiRouter).ask(any(), anyOrNull(), anyOrNull(), any(), captor.capture())

        val passedHistory = captor.firstValue
        assertEquals(6, passedHistory.size)
        assertEquals("Message 4", passedHistory[0].text)
        assertEquals("Где дешевле заправиться?", passedHistory.last().text)
    }
}