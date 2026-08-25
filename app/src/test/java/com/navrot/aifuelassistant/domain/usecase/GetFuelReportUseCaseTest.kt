package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.model.ReportPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetFuelReportUseCaseTest {

    private lateinit var repository: FuelRecordRepository
    private lateinit var useCase: GetFuelReportUseCase

    @Before
    fun setUp() {
        repository = mock()
        useCase = GetFuelReportUseCase(repository)
    }

    @Test
    fun `calculateReport with empty records returns zeros`() {
        val report = useCase.calculateReport(emptyList(), 1000L, 2000L)

        assertEquals(0, report.totalRefuels)
        assertEquals(0.0, report.totalLiters, 0.001)
        assertEquals(0.0, report.totalCost, 0.001)
        assertEquals(0.0, report.totalDistanceKm, 0.001)
        assertEquals(0.0, report.averageConsumptionPer100Km, 0.001)
        assertEquals(0.0, report.averagePricePerLiter, 0.001)
        assertEquals(0.0, report.costPerKm, 0.001)
        assertEquals(1000L, report.periodStartEpochMillis)
        assertEquals(2000L, report.periodEndEpochMillis)
    }

    @Test
    fun `calculateReport with single record returns totals with zero distance and zero consumption`() {
        val record = FuelRecordEntity(
            id = 1,
            vehicleId = 1,
            date = 1500L,
            mileage = 10000.0,
            fuelAmount = 40.0,
            pricePerLiter = 50.0,
            totalCost = 2000.0
        )

        val report = useCase.calculateReport(listOf(record), 1000L, 2000L)

        assertEquals(1, report.totalRefuels)
        assertEquals(40.0, report.totalLiters, 0.001)
        assertEquals(2000.0, report.totalCost, 0.001)
        assertEquals(0.0, report.totalDistanceKm, 0.001)
        assertEquals(0.0, report.averageConsumptionPer100Km, 0.001)
        assertEquals(50.0, report.averagePricePerLiter, 0.001)
        assertEquals(0.0, report.costPerKm, 0.001)
    }

    @Test
    fun `calculateReport with multiple records computes distance and metrics correctly`() {
        val record1 = FuelRecordEntity(
            id = 1,
            vehicleId = 1,
            date = 1000L,
            mileage = 10000.0,
            fuelAmount = 40.0,
            pricePerLiter = 50.0,
            totalCost = 2000.0
        )
        val record2 = FuelRecordEntity(
            id = 2,
            vehicleId = 1,
            date = 2000L,
            mileage = 10500.0,
            fuelAmount = 35.0,
            pricePerLiter = 50.0,
            totalCost = 1750.0
        )

        val report = useCase.calculateReport(listOf(record1, record2), 1000L, 2000L)

        assertEquals(2, report.totalRefuels)
        assertEquals(75.0, report.totalLiters, 0.001)
        assertEquals(3750.0, report.totalCost, 0.001)
        assertEquals(500.0, report.totalDistanceKm, 0.001)
        // 35L consumed over 500km -> 7.0 L/100km
        assertEquals(7.0, report.averageConsumptionPer100Km, 0.001)
        // 3750 / 75 = 50.0 ₽/L
        assertEquals(50.0, report.averagePricePerLiter, 0.001)
        // 3750 / 500 = 7.5 ₽/km
        assertEquals(7.5, report.costPerKm, 0.001)
    }

    @Test
    fun `calculateReport handles multi-vehicle records grouped independently`() {
        val v1r1 = FuelRecordEntity(id = 1, vehicleId = 1, date = 1000L, mileage = 10000.0, fuelAmount = 40.0, totalCost = 2000.0)
        val v1r2 = FuelRecordEntity(id = 2, vehicleId = 1, date = 2000L, mileage = 10400.0, fuelAmount = 32.0, totalCost = 1600.0)

        val v2r1 = FuelRecordEntity(id = 3, vehicleId = 2, date = 1100L, mileage = 50000.0, fuelAmount = 50.0, totalCost = 2500.0)
        val v2r2 = FuelRecordEntity(id = 4, vehicleId = 2, date = 2100L, mileage = 50600.0, fuelAmount = 42.0, totalCost = 2100.0)

        val report = useCase.calculateReport(listOf(v1r1, v1r2, v2r1, v2r2), 1000L, 3000L)

        assertEquals(4, report.totalRefuels)
        assertEquals(164.0, report.totalLiters, 0.001)
        assertEquals(8200.0, report.totalCost, 0.001)
        assertEquals(1000.0, report.totalDistanceKm, 0.001)
        assertEquals(7.4, report.averageConsumptionPer100Km, 0.001)
        assertEquals(8.2, report.costPerKm, 0.001)
    }

    @Test
    fun `execute with ReportPeriod queries repository with computed timestamp range`() {
        runBlocking {
            val now = 1_000_000_000L
            val (expectedStart, expectedEnd) = ReportPeriod.LAST_7_DAYS.getPeriodRange(now)

            val records = listOf(
                FuelRecordEntity(id = 1, vehicleId = 1, date = now - 1000, mileage = 100.0, fuelAmount = 10.0, totalCost = 500.0)
            )

            whenever(repository.getByDateRange(expectedStart, expectedEnd)).thenReturn(flowOf(records))

            val report = useCase.execute(ReportPeriod.LAST_7_DAYS, vehicleId = null, nowMillis = now).first()

            assertEquals(1, report.totalRefuels)
            assertEquals(expectedStart, report.periodStartEpochMillis)
            assertEquals(expectedEnd, report.periodEndEpochMillis)
            verify(repository).getByDateRange(expectedStart, expectedEnd)
        }
    }

    @Test
    fun `execute with vehicleId queries getByVehicleIdAndDateRange`() {
        runBlocking {
            val start = 1000L
            val end = 2000L
            val vehicleId = 2L

            whenever(repository.getByVehicleIdAndDateRange(vehicleId, start, end)).thenReturn(flowOf(emptyList()))

            val report = useCase.execute(startDateMillis = start, endDateMillis = end, vehicleId = vehicleId).first()

            assertEquals(0, report.totalRefuels)
            verify(repository).getByVehicleIdAndDateRange(vehicleId, start, end)
            verify(repository, never()).getByDateRange(anyLong(), anyLong())
        }
    }
}
