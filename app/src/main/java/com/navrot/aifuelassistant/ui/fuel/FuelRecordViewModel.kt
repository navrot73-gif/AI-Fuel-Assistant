package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.FuelApplication
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FuelRecordViewModel(private val vehicleId: Long) : ViewModel() {

    private val dao = FuelApplication.instance.database.fuelRecordDao()

    val records: StateFlow<List<FuelRecordEntity>> = dao.getByVehicleId(vehicleId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addRecord(record: FuelRecordEntity) {
        viewModelScope.launch {
            dao.insert(record)
        }
    }

    fun deleteRecord(record: FuelRecordEntity) {
        viewModelScope.launch {
            dao.delete(record)
        }
    }
}