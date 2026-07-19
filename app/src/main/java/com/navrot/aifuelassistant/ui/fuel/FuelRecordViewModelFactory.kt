package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.navrot.aifuelassistant.FuelApplication
import com.navrot.aifuelassistant.data.FuelRecordRepositoryImpl

class FuelRecordViewModelFactory(private val vehicleId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FuelRecordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val dao = FuelApplication.instance.database.fuelRecordDao()
            val repository = FuelRecordRepositoryImpl(dao)
            return FuelRecordViewModel(vehicleId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}