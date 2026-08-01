package com.navrot.aifuelassistant.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.ai.FuelAnalysisPromptBuilder
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.domain.fuel.DemoFuelStations
import com.navrot.aifuelassistant.domain.fuel.FuelDispatcher
import com.navrot.aifuelassistant.domain.fuel.FuelStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardMetrics(
    val consumption: Float = 0f,
    val efficiency: Int = 0,
    val rubPerKm: Float = 0f,
    val sparkline: List<Float> = emptyList(),
    val fillCount: Int = 0,
    val hasData: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val fuelRecordRepository: FuelRecordRepository,
    private val vehicleRepository: VehicleRepository,
    private val aiRouter: AiRouter
) : ViewModel() {

    // Реальные метрики из Room
    val metrics: StateFlow<DashboardMetrics> = fuelRecordRepository.getAll()
        .map { records -> calculateMetrics(records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    // =========================
    // FUEL DISPATCHER (выбор топлива и АЗС)
    // =========================

    private val _selectedFuelType = MutableStateFlow("АИ-95")
    val selectedFuelType: StateFlow<String> = _selectedFuelType.asStateFlow()

    private val _stations = MutableStateFlow<List<FuelStation>>(emptyList())
    val stations: StateFlow<List<FuelStation>> = _stations.asStateFlow()

    private val _bestStation = MutableStateFlow<FuelStation?>(null)
    val bestStation: StateFlow<FuelStation?> = _bestStation.asStateFlow()

    init {
        loadStations()
    }

    fun selectFuelType(fuelType: String) {
        _selectedFuelType.value = fuelType
        updateRecommendation()
    }

    private fun loadStations() {
        _stations.value = DemoFuelStations.stations
        updateRecommendation()
    }

    private fun updateRecommendation() {
        val ranked = FuelDispatcher.rank(stations = _stations.value, fuelType = _selectedFuelType.value)
        _bestStation.value = ranked.firstOrNull()
    }

    // =========================
    // AI ANALYSIS
    // =========================

    private val _analysis = MutableStateFlow<String?>(null)
    val analysis: StateFlow<String?> = _analysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun askAi() {
        if (_isAnalyzing.value) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            try {
                val records = fuelRecordRepository.getAll().first()
                val vehicle = vehicleRepository.getAllVehicles().first().firstOrNull()
                val prompt = FuelAnalysisPromptBuilder.build(vehicle = vehicle, records = records)
                _analysis.value = aiRouter.ask(prompt)
            } catch (e: Throwable) {
                _error.value = e.message ?: "Не удалось получить AI-анализ"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // =========================
    // Расчёт метрик
    // =========================

    private fun calculateMetrics(records: List<FuelRecordEntity>): DashboardMetrics {
        if (records.isEmpty()) return DashboardMetrics()

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

        // Эффективность: идеальный расход 6 л/100км = 100%
        val efficiency = if (avgConsumption > 0) {
            (6.0f / avgConsumption * 100).toInt().coerceIn(0, 100)
        } else 0

        // Стоимость 1 км
        val totalCost = records.sumOf { it.totalCost }
        val totalKm = if (byMileage.size >= 2) {
            byMileage.last().mileage - byMileage.first().mileage
        } else 0.0
        val rubPerKm = if (totalKm > 0) (totalCost / totalKm * 1000).toFloat() else 0f

        return DashboardMetrics(
            consumption = avgConsumption,
            efficiency = efficiency,
            rubPerKm = rubPerKm,
            sparkline = consumptions.takeLast(14),
            fillCount = records.size,
            hasData = consumptions.isNotEmpty(),
        )
    }
}