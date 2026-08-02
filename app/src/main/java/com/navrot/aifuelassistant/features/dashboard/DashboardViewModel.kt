package com.navrot.aifuelassistant.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.ai.FuelAnalysisPromptBuilder
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.domain.fuel.DemoFuelStations
import com.navrot.aifuelassistant.domain.fuel.FuelDispatcher
import com.navrot.aifuelassistant.domain.fuel.FuelStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardMetrics(
    val fillCount: Int = 0,
    val consumption: Float = 0f,
    val efficiency: Int = 0,
    val rubPerKm: Float = 0f,
    val sparklineData: List<Float> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val fuelRecordRepository: FuelRecordRepository,
    private val vehicleRepository: VehicleRepository,
    private val aiRouter: AiRouter,
) : ViewModel() {

    private val _metrics = MutableStateFlow(DashboardMetrics())
    val metrics: StateFlow<DashboardMetrics> = _metrics.asStateFlow()

    private val _selectedFuelType = MutableStateFlow("АИ-95")
    val selectedFuelType: StateFlow<String> = _selectedFuelType.asStateFlow()

    private val _stations = MutableStateFlow<List<FuelStation>>(emptyList())
    val stations: StateFlow<List<FuelStation>> = _stations.asStateFlow()

    private val _bestStation = MutableStateFlow<FuelStation?>(null)
    val bestStation: StateFlow<FuelStation?> = _bestStation.asStateFlow()

    private val _analysis = MutableStateFlow<String?>(null)
    val analysis: StateFlow<String?> = _analysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadStations()
        loadMetrics()
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

    private fun loadMetrics() {
        viewModelScope.launch {
            fuelRecordRepository.getAll()
                .catch { _metrics.value = DashboardMetrics() }
                .collect { records -> _metrics.value = computeMetrics(records) }
        }
    }

    private fun computeMetrics(
        records: List<com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity>
    ): DashboardMetrics {
        if (records.isEmpty()) return DashboardMetrics()

        val sorted = records.sortedByDescending { it.date }
        var totalLiters = 0.0
        var totalKm = 0.0
        for (i in 0 until sorted.size - 1) {
            val curr = sorted[i]
            val prev = sorted[i + 1]
            val km = curr.mileage - prev.mileage
            if (km > 0) {
                totalLiters += curr.fuelAmount
                totalKm += km
            }
        }
        val consumption = if (totalKm > 0) (totalLiters / totalKm * 100).toFloat() else 0f

        val efficiency = (100f - (consumption - 6f) * 10f).coerceIn(0f, 100f).toInt()

        val totalCost = records.sumOf { it.totalCost }
        val sortedByDate = records.sortedBy { it.date }
        val totalKmAll = if (sortedByDate.size >= 2) {
            (sortedByDate.last().mileage - sortedByDate.first().mileage).coerceAtLeast(0.0)
        } else 0.0
        val rubPerKm = if (totalKmAll > 0) (totalCost / totalKmAll).toFloat() else 0f

        val sparkline = computeSparkline(records.sortedByDescending { it.date }.take(14))

        return DashboardMetrics(
            fillCount = records.size,
            consumption = consumption,
            efficiency = efficiency,
            rubPerKm = rubPerKm,
            sparklineData = sparkline,
        )
    }

    private fun computeSparkline(records: List<com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity>): List<Float> {
        if (records.size < 2) return emptyList()
        val sorted = records.sortedByDescending { it.date }
        val result = mutableListOf<Float>()
        for (i in 0 until sorted.size - 1) {
            val curr = sorted[i]
            val prev = sorted[i + 1]
            val km = curr.mileage - prev.mileage
            if (km > 0 && curr.fuelAmount > 0) {
                result.add((curr.fuelAmount / km * 100).toFloat())
            }
        }
        return result.reversed()
    }

    fun askAi() {
        if (_isAnalyzing.value) return

        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null

            try {
                val records = fuelRecordRepository.getAll().first()
                val vehicle = vehicleRepository.getAllVehicles().first().firstOrNull()

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