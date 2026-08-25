package com.navrot.aifuelassistant.ui.reports

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.backup.ReportCsvExporter
import com.navrot.aifuelassistant.data.model.FuelReport
import com.navrot.aifuelassistant.data.model.ReportPeriod
import com.navrot.aifuelassistant.domain.usecase.GetFuelReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    fun exportAndShareCsv(context: Context) {
        viewModelScope.launch {
            try {
                val currentPeriod = _selectedPeriod.value
                val records = repository.getRecordsForPeriod(currentPeriod).first()
                val csvContent = ReportCsvExporter.generateCsv(records)

                withContext(Dispatchers.IO) {
                    val fileName = "fuel_report_${currentPeriod.name.lowercase()}.csv"
                    val file = File(context.cacheDir, fileName)
                    file.writeText(csvContent, Charsets.UTF_8)

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooser = Intent.createChooser(intent, "Поделиться отчётом CSV").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Failed to export/share CSV report", e)
            }
        }
    }
}
