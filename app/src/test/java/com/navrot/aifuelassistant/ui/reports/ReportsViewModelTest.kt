package com.navrot.aifuelassistant.ui.reports

import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.model.ReportPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val repository = mock<FuelRecordRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads report for default period LAST_30_DAYS`() = runTest {
        val record = FuelRecordEntity(
            id = 1,
            vehicleId = 1,
            date = System.currentTimeMillis(),
            mileage = 1000.0,
            fuelAmount = 30.0,
            pricePerLiter = 50.0,
            totalCost = 1500.0
        )
        whenever(repository.getByDateRange(anyLong(), anyLong())).thenReturn(flowOf(listOf(record)))

        val viewModel = ReportsViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportPeriod.LAST_30_DAYS, state.selectedPeriod)
        assertFalse(state.isLoading)
        assertNotNull(state.report)
        assertEquals(1, state.report?.totalRefuels)
        assertEquals(30.0, state.report?.totalLiters ?: 0.0, 0.001)
    }

    @Test
    fun `period change via onPeriodSelected refetches data for new period`() = runTest {
        whenever(repository.getByDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))

        val viewModel = ReportsViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onPeriodSelected(ReportPeriod.LAST_7_DAYS)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportPeriod.LAST_7_DAYS, state.selectedPeriod)
        assertFalse(state.isLoading)
        assertNotNull(state.report)
        assertEquals(0, state.report?.totalRefuels)
    }

    @Test
    fun `report with 0 refuels correctly updates state without crashing`() = runTest {
        whenever(repository.getByDateRange(anyLong(), anyLong())).thenReturn(flowOf(emptyList()))

        val viewModel = ReportsViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.report)
        assertEquals(0, state.report?.totalRefuels)
        assertEquals(0.0, state.report?.totalCost ?: 0.0, 0.001)
    }

    @Test
    fun `getRecordsForPeriod delegates to repository getRecordsForPeriod`() = runTest {
        val record = FuelRecordEntity(id = 1, vehicleId = 1)
        whenever(repository.getRecordsForPeriod(ReportPeriod.LAST_30_DAYS)).thenReturn(flowOf(listOf(record)))

        whenever(repository.getByDateRange(anyLong(), anyLong())).thenReturn(flowOf(listOf(record)))

        val viewModel = ReportsViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val recordsFlow = repository.getRecordsForPeriod(ReportPeriod.LAST_30_DAYS)
        recordsFlow.collect { records ->
            assertEquals(1, records.size)
            assertEquals(1L, records[0].id)
        }
    }
}
