package com.navrot.aifuelassistant.ui.reports

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.model.FuelReport
import com.navrot.aifuelassistant.data.model.ReportPeriod
import com.navrot.aifuelassistant.domain.usecase.GetFuelReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

fun FuelRecordRepository.getReportForPeriod(period: ReportPeriod): Flow<FuelReport> {
    return GetFuelReportUseCase(this).execute(period)
}

data class ReportsUiState(
    val selectedPeriod: ReportPeriod = ReportPeriod.LAST_30_DAYS,
    val report: FuelReport? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: FuelRecordRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.LAST_30_DAYS)

    val uiState: StateFlow<ReportsUiState> = _selectedPeriod
        .flatMapLatest { period ->
            repository.getReportForPeriod(period)
                .map { report ->
                    ReportsUiState(
                        selectedPeriod = period,
                        report = report,
                        isLoading = false
                    )
                }
                .catch { e ->
                    Log.e("ReportsViewModel", "Error fetching fuel report for period $period", e)
                    emit(
                        ReportsUiState(
                            selectedPeriod = period,
                            report = null,
                            isLoading = false
                        )
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReportsUiState()
        )

    fun onPeriodSelected(period: ReportPeriod) {
        _selectedPeriod.value = period
    }
}
