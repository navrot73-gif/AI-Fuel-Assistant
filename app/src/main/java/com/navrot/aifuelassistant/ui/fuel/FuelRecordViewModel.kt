package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FuelRecordViewModel(
    private val vehicleId: Long,
    private val repository: FuelRecordRepository
) : ViewModel() {

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
