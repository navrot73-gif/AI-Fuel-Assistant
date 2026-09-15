package com.navrot.aifuelassistant.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.database.entity.toPersonalFuelEvent
import com.navrot.aifuelassistant.data.repository.PredictiveRepository
import com.navrot.aifuelassistant.domain.predictive.ConsumptionPrediction
import com.navrot.aifuelassistant.domain.predictive.NextRefuelPrediction
import com.navrot.aifuelassistant.domain.predictive.usecase.PredictConsumptionUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.PredictNextRefuelUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.ResetPersonalLearningUseCase
import com.navrot.aifuelassistant.ui.components.VehicleCardUiState
import com.navrot.aifuelassistant.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
    private val fuelRecordRepository: FuelRecordRepository,
    private val predictConsumptionUseCase: PredictConsumptionUseCase = PredictConsumptionUseCase(),
    private val predictNextRefuelUseCase: PredictNextRefuelUseCase = PredictNextRefuelUseCase(),
    private val resetPersonalLearningUseCase: ResetPersonalLearningUseCase = ResetPersonalLearningUseCase(),
    private val predictiveRepository: PredictiveRepository? = null
) : ViewModel() {

    private val _activeVehicleId = MutableStateFlow<Long?>(null)
    val activeVehicleId: StateFlow<Long?> = _activeVehicleId

    fun setActiveVehicle(vehicleId: Long?) {
        _activeVehicleId.value = vehicleId
    }

    fun getVehicleRecords(vehicleId: Long): Flow<List<FuelRecordEntity>> {
        return fuelRecordRepository.getByVehicleId(vehicleId)
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

    suspend fun getConsumptionPrediction(vehicle: VehicleEntity, records: List<FuelRecordEntity>): ConsumptionPrediction {
        val events = records.map { it.toPersonalFuelEvent() }
        val metadata = predictiveRepository?.getModelMetadata(vehicle.id)
        return predictConsumptionUseCase(
            vehicleId = vehicle.id,
            fuelType = vehicle.fuelType,
            events = events,
            metadata = metadata,
            currentOdometerKm = vehicle.currentMileage
        )
    }

    suspend fun getNextRefuelPrediction(vehicle: VehicleEntity, records: List<FuelRecordEntity>): NextRefuelPrediction {
        val events = records.map { it.toPersonalFuelEvent() }
        return predictNextRefuelUseCase(
            vehicleId = vehicle.id,
            fuelType = vehicle.fuelType,
            events = events,
            currentOdometerKm = vehicle.currentMileage
        )
    }

    fun resetPersonalLearning(vehicleId: Long) {
        viewModelScope.launch {
            predictiveRepository?.resetModel(vehicleId)
        }
    }

    suspend fun getVehicleEntity(id: Long): VehicleEntity? {
        return vehicleRepository.getVehicleById(id)
    }

    fun addVehicle(
        name: String,
        brand: String,
        model: String,
        year: Int,
        fuelType: String,
        tankCapacity: Double,
        currentMileage: Double,
        photoUrl: String? = null
    ) {
        viewModelScope.launch {
            val newVehicle = VehicleEntity(
                name = name,
                brand = brand,
                model = model,
                year = year,
                fuelType = fuelType,
                tankCapacity = tankCapacity,
                currentMileage = currentMileage,
                photoUrl = photoUrl
            )
            vehicleRepository.insertVehicle(newVehicle)
        }
    }

    fun updateVehicle(
        id: Long,
        name: String,
        brand: String,
        model: String,
        year: Int,
        fuelType: String,
        tankCapacity: Double,
        currentMileage: Double,
        photoUrl: String? = null
    ) {
        viewModelScope.launch {
            val updatedVehicle = VehicleEntity(
                id = id,
                name = name,
                brand = brand,
                model = model,
                year = year,
                fuelType = fuelType,
                tankCapacity = tankCapacity,
                currentMileage = currentMileage,
                photoUrl = photoUrl
            )
            vehicleRepository.updateVehicle(updatedVehicle)
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

    val fillPercent = if (lastFill != null && tankCapacity > 0) {
        (lastFill.fuelAmount / tankCapacity * 100).toInt().coerceIn(0, 100)
    } else 0

    val currentFuel = tankCapacity * fillPercent / 100.0
    val rangeKm = if (avgConsumption > 0 && currentFuel > 0) {
        (currentFuel / avgConsumption * 100).toInt()
    } else 0

    val bars = consumptions.takeLast(7)

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
        brand = brand,
        modelLine = modelLine,
        fuelGrade = fuelType.ifBlank { "—" },
        tankLiters = tankCapacity.toInt(),
        fillPercent = fillPercent,
        rangeKm = rangeKm,
        mileageText = Format.km(currentMileage / 1000.0),
        consumptionText = if (avgConsumption > 0) Format.number(avgConsumption, 1) else "—",
        fillCount = records.size,
        bars = bars,
        toKmLeft = toKmLeft,
        toPercent = toPercent,
        lastFillDate = if (lastFill != null) dateFormat.format(Date(lastFill.date)) else "—",
        lastFillLiters = if (lastFill != null) Format.number(lastFill.fuelAmount, 1) else "—",
        lastFillBrand = lastFill?.stationName?.ifBlank { "—" } ?: "—",
        lastFillPrice = if (lastFill != null) Format.price(lastFill.totalCost) else "—",
        photoUrl = photoUrl,
    )
}
