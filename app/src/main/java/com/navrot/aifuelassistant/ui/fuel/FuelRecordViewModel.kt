package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FuelRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FuelRecordRepository
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