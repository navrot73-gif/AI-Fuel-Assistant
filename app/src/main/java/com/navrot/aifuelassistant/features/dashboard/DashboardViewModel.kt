package com.navrot.aifuelassistant.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.ai.FuelAnalysisPromptBuilder
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.model.GasStation
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
    private val gasStationRepository: GasStationRepository,
    private val aiRouter: AiRouter,
) : ViewModel() {

    private val _metrics = MutableStateFlow(DashboardMetrics())
    val metrics: StateFlow<DashboardMetrics> = _metrics.asStateFlow()

    private val _selectedFuelType = MutableStateFlow("АИ-95")
    val selectedFuelType: StateFlow<String> = _selectedFuelType.asStateFlow()

    private val _stations = MutableStateFlow<List<GasStation>>(emptyList())
    val stations: StateFlow<List<GasStation>> = _stations.asStateFlow()

    private val _bestStation = MutableStateFlow<GasStation?>(null)
    val bestStation: StateFlow<GasStation?> = _bestStation.asStateFlow()

    private val _analysis = MutableStateFlow<String?>(null)
    val analysis: StateFlow<String?> = _analysis.asStateFlow()

    private val _userQuestion = MutableStateFlow("")
    val userQuestion: StateFlow<String> = _userQuestion.asStateFlow()

    private val _userAnswer = MutableStateFlow<String?>(null)
    val userAnswer: StateFlow<String?> = _userAnswer.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _vehicles = MutableStateFlow<List<VehicleEntity>>(emptyList())
    val vehicles: StateFlow<List<VehicleEntity>> = _vehicles.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)
    val selectedVehicleId: StateFlow<Long?> = _selectedVehicleId.asStateFlow()

    init {
        loadVehicles()
        loadStations()
        loadMetrics()
    }

    fun selectVehicle(vehicleId: Long) {
        _selectedVehicleId.value = vehicleId
        loadMetrics()
    }

    fun selectFuelType(fuelType: String) {
        _selectedFuelType.value = fuelType
        updateBestStation()
    }

    private fun loadVehicles() {
        viewModelScope.launch {
            vehicleRepository.getAllVehicles()
                .catch { }
                .collect { list ->
                    _vehicles.value = list
                    if (_selectedVehicleId.value == null && list.isNotEmpty()) {
                        _selectedVehicleId.value = list.first().id
                    }
                }
        }
    }

    private fun loadStations() {
        viewModelScope.launch {
            try {
                _stations.value = gasStationRepository.getAllStations()
                updateBestStation()
            } catch (_: Exception) {
                // Если не удалось загрузить — оставляем пустой список
            }
        }
    }

    private fun updateBestStation() {
        val fuelType = _selectedFuelType.value
        val best = _stations.value
            .filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .minByOrNull { s ->
                val price = s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE
                price + s.queueTime * 0.5 - (100 - s.reliability) * 0.2
            }
        _bestStation.value = best
    }

    private fun loadMetrics() {
        viewModelScope.launch {
            val vehicleId = _selectedVehicleId.value
            val flow = if (vehicleId != null && vehicleId > 0) {
                fuelRecordRepository.getByVehicleId(vehicleId)
            } else {
                fuelRecordRepository.getAll()
            }
            flow
                .catch { _metrics.value = DashboardMetrics() }
                .collect { records -> _metrics.value = computeMetrics(records) }
        }
    }

    private fun computeMetrics(records: List<FuelRecordEntity>): DashboardMetrics {
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

    private fun computeSparkline(records: List<FuelRecordEntity>): List<Float> {
        if (records.size < 2) return emptyList()

        // От старых к новым — пары «предыдущая → следующая» корректны
        val sorted = records.sortedBy { it.date }
        val result = mutableListOf<Float>()

        for (i in 1 until sorted.size) {
            val curr = sorted[i]
            val prev = sorted[i - 1]
            val km = curr.mileage - prev.mileage
            val liters = curr.fuelAmount
            if (km > 0 && liters > 0) {
                val consumption = (liters / km * 100).toFloat()
                // Отбрасываем аномалии датчика (< 2 или > 50 л/100км)
                if (consumption in 2f..50f) {
                    result.add(consumption)
                }
            }
        }

        // Если ни одной валидной точки — fallback на средний расход
        if (result.isEmpty()) {
            val totalLiters = sorted.sumOf { it.fuelAmount }
            val totalKm = (sorted.last().mileage - sorted.first().mileage)
                .coerceAtLeast(0.0)
            if (totalKm > 0) {
                val avg = (totalLiters / totalKm * 100).toFloat()
                result.addAll(List(3) { avg })
            }
        }

        return result
    }

    fun askAi() {
        if (_isAnalyzing.value) return

        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null

            try {
                val vehicleId = _selectedVehicleId.value
                val records = if (vehicleId != null && vehicleId > 0) {
                    fuelRecordRepository.getByVehicleId(vehicleId).first()
                } else {
                    fuelRecordRepository.getAll().first()
                }
                val vehicle = _vehicles.value.firstOrNull { it.id == vehicleId }

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
        fun setUserQuestion(text: String) {
        _userQuestion.value = text
    }

    fun askUserQuestion() {
        val question = _userQuestion.value.trim()
        if (question.isEmpty() || _isAnalyzing.value) return

        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _userAnswer.value = null
            try {
                val answer = aiRouter.ask(question)
                _userAnswer.value = answer
            } catch (e: Throwable) {
                _error.value = e.message ?: "Не удалось получить ответ"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}
