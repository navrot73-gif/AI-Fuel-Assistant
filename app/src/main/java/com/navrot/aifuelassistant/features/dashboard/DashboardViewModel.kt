package com.navrot.aifuelassistant.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.FuelApplication
import com.navrot.aifuelassistant.ai.AiRouterFactory
import com.navrot.aifuelassistant.ai.FuelAnalysisPromptBuilder
import com.navrot.aifuelassistant.data.FuelRecordRepositoryImpl
import com.navrot.aifuelassistant.data.VehicleRepositoryImpl
import com.navrot.aifuelassistant.domain.fuel.DemoFuelStations
import com.navrot.aifuelassistant.domain.fuel.FuelDispatcher
import com.navrot.aifuelassistant.domain.fuel.FuelStation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardMetrics(
    val avgConsumption: Float = 0f,
    val rubPerKm: Float = 0f,
    val efficiency: Int = 0,
    val sparklineData: List<Float> = emptyList(),
    val totalKm: Float = 0f,
    val totalCostRub: Float = 0f,
    val fillCount: Int = 0,
)

class DashboardViewModel : ViewModel() {

    // =========================
    // РЕПОЗИТОРИИ
    // =========================

    private val fuelRecordRepository = FuelRecordRepositoryImpl(
        FuelApplication.instance.database.fuelRecordDao()
    )

    private val vehicleRepository = VehicleRepositoryImpl(
        FuelApplication.instance.database.vehicleDao()
    )

    private val aiRouter = AiRouterFactory.create()

    // =========================
    // ТЕЛЕМЕТРИЯ
    // =========================

    private val _metrics = MutableStateFlow(DashboardMetrics())
    val metrics: StateFlow<DashboardMetrics> = _metrics.asStateFlow()

    // =========================
    // FUEL DISPATCHER
    // =========================

    private val _selectedFuelType = MutableStateFlow("АИ-95")
    val selectedFuelType: StateFlow<String> = _selectedFuelType.asStateFlow()

    private val _stations = MutableStateFlow<List<FuelStation>>(emptyList())
    val stations: StateFlow<List<FuelStation>> = _stations.asStateFlow()

    private val _bestStation = MutableStateFlow<FuelStation?>(null)
    val bestStation: StateFlow<FuelStation?> = _bestStation.asStateFlow()

    // =========================
    // AI ANALYSIS
    // =========================

    private val _analysis = MutableStateFlow<String?>(null)
    val analysis: StateFlow<String?> = _analysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // =========================
    // INIT — после ВСЕХ свойств
    // =========================

    init {
        loadStations()
        observeMetrics()
    }

    // =========================
    // МЕТОДЫ
    // =========================

    private fun observeMetrics() {
        viewModelScope.launch {
            fuelRecordRepository.getAll()
                .map { records -> calculateMetrics(records) }
                .catch { _metrics.value = DashboardMetrics() }
                .collect { _metrics.value = it }
        }
    }

    private fun calculateMetrics(records: List<com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity>): DashboardMetrics {
        if (records.isEmpty()) return DashboardMetrics()

        val sorted = records.sortedBy { it.mileage }

        val consumptionValues = mutableListOf<Float>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val mileageDiff = curr.mileage - prev.mileage
            if (mileageDiff > 0 && curr.fuelAmount > 0) {
                val consumption = (curr.fuelAmount / mileageDiff * 100).toFloat()
                consumptionValues.add(consumption)
            }
        }

        val avgConsumption = if (consumptionValues.isNotEmpty()) {
            consumptionValues.average().toFloat()
        } else 0f

        val totalKm = if (sorted.size >= 2) {
            (sorted.last().mileage - sorted.first().mileage).toFloat()
        } else 0f

        val totalCostRub = sorted.sumOf { it.totalCost }.toFloat()

        val rubPerKm = if (totalKm > 0) totalCostRub / totalKm else 0f

        val efficiency = if (avgConsumption > 0) {
            (100f - (avgConsumption - 6f) * 10f).coerceIn(0f, 100f).toInt()
        } else 0

        val sparklineData = consumptionValues.takeLast(14)

        return DashboardMetrics(
            avgConsumption = avgConsumption,
            rubPerKm = rubPerKm,
            efficiency = efficiency,
            sparklineData = sparklineData,
            totalKm = totalKm,
            totalCostRub = totalCostRub,
            fillCount = sorted.size,
        )
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
        val fuelType = _selectedFuelType.value
        val ranked = FuelDispatcher.rank(
            stations = _stations.value,
            fuelType = fuelType
        )
        _bestStation.value = ranked.firstOrNull()
    }

    fun askAi() {
        if (_isAnalyzing.value) return

        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null

            try {
                val records = fuelRecordRepository
                    .getAll()
                    .first()

                val vehicle = vehicleRepository
                    .getAllVehicles()
                    .first()
                    .firstOrNull()

                val prompt = FuelAnalysisPromptBuilder.build(
                    vehicle = vehicle,
                    records = records
                )

                _analysis.value = aiRouter.ask(prompt)

            } catch (e: Throwable) {
                _error.value = e.message ?: "Не удалось получить AI-анализ"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}