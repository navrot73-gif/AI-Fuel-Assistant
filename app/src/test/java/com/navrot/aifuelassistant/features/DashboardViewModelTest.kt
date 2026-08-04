package com.navrot.aifuelassistant.features

import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class DashboardViewModelTest {

    private val mockRecordRepo = mock<FuelRecordRepository>()
    private val mockVehicleRepo = mock<VehicleRepository>()
    private val mockStationRepo = mock<GasStationRepository>()
    private val mockAiRouter = mock<AiRouter>()

    private fun createViewModel(): com.navrot.aifuelassistant.features.dashboard.DashboardViewModel {
        return com.navrot.aifuelassistant.features.dashboard.DashboardViewModel(
            fuelRecordRepository = mockRecordRepo,
            vehicleRepository = mockVehicleRepo,
            gasStationRepository = mockStationRepo,
            aiRouter = mockAiRouter
        )
    }

    @Test
    fun `metrics empty records returns zeros`() = runBlocking {
        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(emptyList()))
        whenever(mockRecordRepo.getByVehicleId(any())).thenReturn(flowOf(emptyList()))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(emptyList()))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        kotlinx.coroutines.delay(100)

        val metrics = vm.metrics.value
        assertEquals(0, metrics.fillCount)
        assertEquals(0f, metrics.consumption, 0.01f)
    }

    @Test
    fun `metrics computes consumption correctly`() = runBlocking {
        val vehicle = VehicleEntity(
            id = 1, name = "Test", brand = "T", model = "M",
            year = 2024, fuelType = "АИ-95"
        )
        val records = listOf(
            FuelRecordEntity(id = 1, vehicleId = 1, date = 2000L, mileage = 500.0, fuelAmount = 40.0, pricePerLiter = 45.0, totalCost = 1800.0, fuelType = "АИ-95"),
            FuelRecordEntity(id = 2, vehicleId = 1, date = 1000L, mileage = 300.0, fuelAmount = 40.0, pricePerLiter = 44.0, totalCost = 1760.0, fuelType = "АИ-95")
        )

        whenever(mockRecordRepo.getAll()).thenReturn(flowOf(records))
        whenever(mockRecordRepo.getByVehicleId(eq(1L))).thenReturn(flowOf(records))
        whenever(mockVehicleRepo.getAllVehicles()).thenReturn(flowOf(listOf(vehicle)))
        whenever(mockStationRepo.getAllStations()).thenReturn(emptyList())

        val vm = createViewModel()
        kotlinx.coroutines.delay(100)

        val metrics = vm.metrics.value
        assertEquals(2, metrics.fillCount)
        assertEquals(20f, metrics.consumption, 0.5f)
    }
}
