package com.navrot.aifuelassistant.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.navrot.aifuelassistant.data.VehicleRepository

class VehicleViewModelFactory(
    private val repository: VehicleRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VehicleViewModel::class.java)) {
            return VehicleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}