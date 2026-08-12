package com.navrot.aifuelassistant.ui.fuel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.backup.CsvBackupManager
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FuelRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FuelRecordRepository,
    private val backupManager: CsvBackupManager
) : ViewModel() {

    private val vehicleId: Long = checkNotNull(savedStateHandle["vehicleId"]) {
        "vehicleId is required as a navigation argument"
    }

    val records: StateFlow<List<FuelRecordEntity>> =
        repository.getByVehicleId(vehicleId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    /** Экспорт всех заправок и автомобилей в CSV-файл. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = backupManager.exportToUri(uri)
                _backupMessage.value = "Экспортировано $count"
            } catch (e: Exception) {
                _backupMessage.value = "Ошибка экспорта: ${e.message}"
            }
        }
    }

    /** Импорт заправок и автомобилей из CSV-файла. */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = backupManager.importFromUri(uri)
                _backupMessage.value = "Импортировано ${result.imported}, пропущено ${result.skipped}"
            } catch (e: Exception) {
                _backupMessage.value = "Ошибка импорта: ${e.message}"
            }
        }
    }

    fun clearBackupMessage() {
        _backupMessage.value = null
    }

    fun addRecord(record: FuelRecordEntity) {
        viewModelScope.launch {
            repository.insert(record)
        }
    }

    fun updateRecord(record: FuelRecordEntity) {
        viewModelScope.launch {
            repository.update(record)
        }
    }

    fun deleteRecord(record: FuelRecordEntity) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }
}
