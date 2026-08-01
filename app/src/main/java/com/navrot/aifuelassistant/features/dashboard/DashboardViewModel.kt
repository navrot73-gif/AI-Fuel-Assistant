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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val fuelRecordRepository: FuelRecordRepository,
    private val vehicleRepository: VehicleRepository,
    private val aiRouter: AiRouter
) : ViewModel() {

    // =========================
    // FUEL DISPATCHER (выбор топлива и АЗС)
    // =========================

    private val _selectedFuelType = MutableStateFlow("АИ-95")
    val selectedFuelType: StateFlow<String> =
        _selectedFuelType.asStateFlow()

    private val _stations = MutableStateFlow<List<FuelStation>>(emptyList())
    val stations: StateFlow<List<FuelStation>> =
        _stations.asStateFlow()

    private val _bestStation = MutableStateFlow<FuelStation?>(null)
    val bestStation: StateFlow<FuelStation?> =
        _bestStation.asStateFlow()

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
        val fuelType = _selectedFuelType.value
        val ranked = FuelDispatcher.rank(
            stations = _stations.value,
            fuelType = fuelType
        )
        _bestStation.value = ranked.firstOrNull()
    }

    // =========================
    // AI ANALYSIS
    // =========================

    private val _analysis = MutableStateFlow<String?>(null)
    val analysis: StateFlow<String?> =
        _analysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> =
        _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> =
        _error.asStateFlow()

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