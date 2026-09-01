package com.navrot.aifuelassistant.features.dashboard

import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.features.dashboard.delegate.DashboardMetricsDelegate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class DashboardMetricsRegressionTest {

    @Test
    fun testMetricsCalculationWhenVehicleAndRecordsExist() = runTest {
        val repository = mock(FuelRecordRepository::class.java)
        val delegate = DashboardMetricsDelegate(repository)

        val records = listOf(
            FuelRecordEntity(id = 1, vehicleId = 1L, mileage = 1000.0, fuelAmount = 40.0, totalCost = 2000.0, date = 1000L),
            FuelRecordEntity(id = 2, vehicleId = 1L, mileage = 1500.0, fuelAmount = 40.0, totalCost = 2200.0, date = 2000L)
        )

        `when`(repository.getByVehicleId(1L)).thenReturn(flowOf(records))

        delegate.loadMetrics(this, 1L)
        testScheduler.advanceUntilIdle()

        val metrics = delegate.metrics.value
        assertEquals(2, metrics.fillCount)
        assertEquals(8.0f, metrics.consumption, 0.1f) // 40L / 500km * 100 = 8.0 L/100km
        assertEquals(80, metrics.efficiency)          // 100 - (8-6)*10 = 80%
        assertEquals(8.4f, metrics.rubPerKm, 0.1f)    // (2000+2200) / 500 = 8.4 rub/km
    }

    @Test
    fun testEmptyMetricsWhenNoRecordsExist() = runTest {
        val repository = mock(FuelRecordRepository::class.java)
        val delegate = DashboardMetricsDelegate(repository)

        `when`(repository.getByVehicleId(1L)).thenReturn(flowOf(emptyList()))

        delegate.loadMetrics(this, 1L)
        testScheduler.advanceUntilIdle()

        val metrics = delegate.metrics.value
        assertEquals(0, metrics.fillCount)
        assertEquals(0f, metrics.consumption, 0.001f)
        assertEquals(0, metrics.efficiency)
        assertEquals(0f, metrics.rubPerKm, 0.001f)
    }
}
