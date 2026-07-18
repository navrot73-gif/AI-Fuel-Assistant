package com.navrot.aifuelassistant.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VehicleViewModel(
    private val repository: VehicleRepository
) : ViewModel() {

    // Стрим данных из БД преобразуем в StateFlow для Jetpack Compose
    val vehiclesState: StateFlow<List<VehicleEntity>> = repository.getAllVehicles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Добавление автомобиля с вызовом корректного метода репозитория
    fun addVehicle(
        name: String,
        brand: String,
        model: String,
        year: Int,
        fuelType: String,
        tankCapacity: Double,
        currentMileage: Double
    ) {
        viewModelScope.launch {
            val newVehicle = VehicleEntity(
                name = name,
                brand = brand,
                model = model,
                year = year,
                fuelType = fuelType,
                tankCapacity = tankCapacity,
                currentMileage = currentMileage
            )
            repository.insertVehicle(newVehicle)
        }
    }

    // Удаление автомобиля с вызовом корректного метода репозитория
    fun deleteVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }
}