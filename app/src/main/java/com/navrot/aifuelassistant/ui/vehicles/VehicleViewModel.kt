package com.navrot.aifuelassistant.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.ui.components.VehicleCardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
open class VehicleViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val fuelRecordRepository: FuelRecordRepository
) : ViewModel() {

    // Track active vehicle ID (selected by user tapping a card)
    private val _activeVehicleId = MutableStateFlow<Long?>(null)
    val activeVehicleId: StateFlow<Long?> = _activeVehicleId

    fun setActiveVehicle(vehicleId: Long?) {
        _activeVehicleId.value = vehicleId
    }

    val vehiclesWithStats: StateFlow<List<VehicleCardUiState>> = combine(
        vehicleRepository.getAllVehicles(),
        fuelRecordRepository.getAll()
    ) { vehicles, records ->
        vehicles.map { vehicle ->
            val vehicleRecords = records
                .filter { it.vehicleId == vehicle.id }
            vehicle.toUiState(vehicleRecords)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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
            vehicleRepository.insertVehicle(newVehicle)
        }
    }

    fun deleteVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            vehicleRepository.deleteVehicle(vehicle)
        }
    }
}

private fun VehicleEntity.toUiState(records: List<FuelRecordEntity>): VehicleCardUiState {
    val sorted = records.sortedByDescending { it.date }
    val lastFill = sorted.firstOrNull()

    // Расчёт расхода по последовательным заправкам (по пробегу)
    val byMileage = records.sortedBy { it.mileage }
    val consumptions = mutableListOf<Float>()
    for (i in 1 until byMileage.size) {
        val prev = byMileage[i - 1]
        val curr = byMileage[i]
        val diff = curr.mileage - prev.mileage
        if (diff > 0 && curr.fuelAmount > 0) {
            consumptions.add((curr.fuelAmount / diff * 100).toFloat())
        }
    }

    val avgConsumption = if (consumptions.isNotEmpty()) consumptions.average().toFloat() else 0f

    // % бака: по последней заправке относительно объёма бака
    val fillPercent = if (lastFill != null && tankCapacity > 0) {
        (lastFill.fuelAmount / tankCapacity * 100).toInt().coerceIn(0, 100)
    } else 0

    // Запас хода: по ТОКУ в баке (не полный бак!)
    val currentFuel = tankCapacity * fillPercent / 100.0
    val rangeKm = if (avgConsumption > 0 && currentFuel > 0) {
        (currentFuel / avgConsumption * 100).toInt()
    } else 0

    // Бары: последние 7 расходов
    val bars = consumptions.takeLast(7)

    // ТО: интервал 15 000 км
    val toInterval = 15_000.0
    val kmSinceLastTo = currentMileage % toInterval
    val toKmLeft = (toInterval - kmSinceLastTo).toInt().coerceAtLeast(0)
    val toPercent = (kmSinceLastTo / toInterval * 100).toInt()

    val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())

    val modelLine = listOf(brand, model)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .let { if (it.isBlank()) "—" else "$it · $year" }

    return VehicleCardUiState(
        id = id,
        name = name.ifBlank { "Без названия" },
        modelLine = modelLine,
        fuelGrade = fuelType.ifBlank { "—" },
        tankLiters = tankCapacity.toInt(),
        fillPercent = fillPercent,
        rangeKm = rangeKm,
        mileageText = String.format("%.1f", currentMileage / 1000.0),
        consumptionText = if (avgConsumption > 0) String.format("%.1f", avgConsumption) else "—",
        fillCount = records.size,
        bars = bars,
        toKmLeft = toKmLeft,
        toPercent = toPercent,
        lastFillDate = if (lastFill != null) dateFormat.format(Date(lastFill.date)) else "—",
        lastFillLiters = if (lastFill != null) String.format("%.1f", lastFill.fuelAmount) else "—",
        lastFillBrand = lastFill?.stationName?.ifBlank { "—" } ?: "—",
        lastFillPrice = if (lastFill != null) String.format("%.0f", lastFill.totalCost) else "—",
    )
}
