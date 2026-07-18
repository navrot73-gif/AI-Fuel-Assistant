package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class FuelRecordViewModelFactory(private val vehicleId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FuelRecordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FuelRecordViewModel(vehicleId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}