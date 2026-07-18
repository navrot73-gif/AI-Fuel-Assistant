package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.navrot.aifuelassistant.app.FuelApplication

class FuelRecordViewModelFactory(
    private val vehicleId: Long,
    private val dao: com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FuelRecordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FuelRecordViewModel(vehicleId, dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}