package com.navrot.aifuelassistant.features.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.navrot.aifuelassistant.ai.FuelAnalysisPromptBuilder
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import kotlin.coroutines.resume

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
    private val gasStationRepository: GasStationRepositoryInterface,
    private val aiRouter: AiRouter,
    @ApplicationContext private val applicationContext: Context,
) : ViewModel() {

    private val _metrics = MutableStateFlow(DashboardMetrics())
    val metrics: StateFlow<DashboardMetrics> = _metrics.asStateFlow()

    private val _weeklyConsumption = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val weeklyConsumption: StateFlow<List<Pair<String, Float>>> = _weeklyConsumption.asStateFlow()

    private val _selectedFuelType = MutableStateFlow("АИ-95")
    val selectedFuelType: StateFlow<String> = _selectedFuelType.asStateFlow()

    private val _stations = MutableStateFlow<List<GasStation>>(emptyList())
    val stations: StateFlow<List<GasStation>> = _stations.asStateFlow()

    private val _bestStation = MutableStateFlow<GasStation?>(null)
    val bestStation: StateFlow<GasStation?> = _bestStation.asStateFlow()

    private val _userQuestion = MutableStateFlow("")
    val userQuestion: StateFlow<String> = _userQuestion.asStateFlow()

    private val _userAnswer = MutableStateFlow<String?>(null)
    val userAnswer: StateFlow<String?> = _userAnswer.asStateFlow()

    private val _pendingRouteStationId = MutableStateFlow<Int?>(null)
    val pendingRouteStationId: StateFlow<Int?> = _pendingRouteStationId.asStateFlow()

    enum class PendingRouteMode { NONE, ROUTE, CARD }

    private val _pendingRouteMode = MutableStateFlow(PendingRouteMode.NONE)
    val pendingRouteMode: StateFlow<PendingRouteMode> = _pendingRouteMode.asStateFlow()

    private val _pendingOpenStationId = MutableStateFlow<Int?>(null)
    val pendingOpenStationId: StateFlow<Int?> = _pendingOpenStationId.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _vehicles = MutableStateFlow<List<VehicleEntity>>(emptyList())
    val vehicles: StateFlow<List<VehicleEntity>> = _vehicles.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)
    val selectedVehicleId: StateFlow<Long?> = _selectedVehicleId.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    // Chat history - last 20 messages persisted in SharedPreferences
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val chatPrefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    fun updateUserLocation(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
    }

    private fun loadChatHistory() {
        try {
            val json = chatPrefs.getString("messages", "[]") ?: "[]"
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val loaded: List<ChatMessage>? = gson.fromJson(json, type)
            _chatMessages.value = loaded?.filterNotNull() ?: emptyList()
        } catch (_: Exception) {
            _chatMessages.value = emptyList()
        }
    }

    private fun saveChatHistory(messages: List<ChatMessage>) {
        try {
            val json = gson.toJson(messages)
            chatPrefs.edit().putString("messages", json).apply()
        } catch (_: Exception) {}
    }

    fun addChatMessage(message: ChatMessage) {
        val current = _chatMessages.value.toList()
        val updated = (current + message).takeLast(20)
        _chatMessages.value = updated
        saveChatHistory(updated)
    }

    fun clearChatHistory() {
        if (_chatMessages.value.isEmpty()) return
        _chatMessages.value = emptyList()
        saveChatHistory(emptyList())
    }

    init {
        loadChatHistory()
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
                price + s.queueTime * 0.5 + (100 - s.reliability) * 0.2
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
                .catch { _metrics.value = DashboardMetrics(); _weeklyConsumption.value = emptyList() }
                .collect { records ->
                    _metrics.value = computeMetrics(records)
                    _weeklyConsumption.value = computeWeeklyConsumption(records)
                }
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

    private fun computeWeeklyConsumption(records: List<FuelRecordEntity>): List<Pair<String, Float>> {
        if (records.isEmpty()) return emptyList()

        // Filter records for the selected vehicle only
        val vehicleId = _selectedVehicleId.value
        val filteredRecords = if (vehicleId != null && vehicleId > 0) {
            records.filter { it.vehicleId == vehicleId }
        } else records

        // Group by day of week (Calendar.DAY_OF_WEEK: 1=Sun, 2=Mon, ..., 7=Sat)
        val dayNames = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
        val grouped = mutableMapOf<Int, MutableList<Float>>()

        val sorted = filteredRecords.sortedBy { it.date }
        for (i in 1 until sorted.size) {
            val curr = sorted[i]
            val prev = sorted[i - 1]
            val km = curr.mileage - prev.mileage
            val liters = curr.fuelAmount
            if (km > 0 && liters > 0) {
                val consumption = (liters / km * 100).toFloat()
                if (consumption in 2f..50f) {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = curr.date
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun...7=Sat
                    grouped.getOrPut(dayOfWeek) { mutableListOf() }.add(consumption)
                }
            }
        }

        // Build result: only days with data, in order Mon-Sun
        val result = mutableListOf<Pair<String, Float>>()
        for (day in 2..7) { // Mon=2 to Sat=7
            val consumptions = grouped[day]
            if (consumptions != null && consumptions.isNotEmpty()) {
                val avg = consumptions.average().toFloat()
                result.add(dayNames[day] to avg)
            }
        }
        // Add Sunday (1) at the end if it has data
        val sunConsumptions = grouped[1]
        if (sunConsumptions != null && sunConsumptions.isNotEmpty()) {
            result.add(dayNames[1] to sunConsumptions.average().toFloat())
        }

        return result
    }

    fun setUserQuestion(text: String) {
        _userQuestion.value = text
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocation(): Location? = suspendCancellableCoroutine { cont ->
        val fusedClient = LocationServices.getFusedLocationProviderClient(
            applicationContext
        )
        fusedClient.lastLocation.addOnSuccessListener { location ->
            cont.resume(location)
        }.addOnFailureListener { e ->
            cont.resume(null)
        }
    }

    private data class UserContext(val text: String, val nearestStationId: Int?)

    private suspend fun buildUserContext(): UserContext {
        val location = getLastLocation()
            ?: return UserContext("", null)

        val lat = location.latitude
        val lon = location.longitude

        // Get city name
        val city = GeoUtils.hardcodedDetectCity(lat, lon)

        // Get nearby stations (top 5 within 50km), closest first
        val nearbyStations = try {
            gasStationRepository.getNearbyStations(lat, lon, 50.0)
                .sortedBy { GeoUtils.calculateDistance(lat, lon, it.latitude, it.longitude) }
        } catch (_: Exception) {
            emptyList<GasStation>()
        }

        val top5 = nearbyStations.take(5)
        val stationsInfo = if (top5.isNotEmpty()) {
            top5.joinToString("\n") { station ->
                val price = station.fuelTypes
                    .filter { it.available }
                    .minByOrNull { it.price }?.price
                    ?: 0.0
                val distance = GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
                "${station.brand} — ${String.format("%.0f", price)}₽ — ${String.format("%.1f", distance)}км"
            }
        } else "нет станций в радиусе 50км"

        val text = "Пользователь: $lat, $lon, город: $city.\nБлижайшие АЗС:\n$stationsInfo"
        return UserContext(text, top5.firstOrNull()?.id)
    }

    fun askUserQuestion() {
        val question = _userQuestion.value.trim()
        if (question.isEmpty() || _isAnalyzing.value) return

        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val lower = question.lowercase()
                val isGreeting = listOf("привет", "здравств", "добрый", "hi", "hello", "как дела").any { lower.startsWith(it) }
                if (isGreeting) {
                    val now = System.currentTimeMillis()
                    addChatMessage(ChatMessage(role = "user", text = question, ts = now))
                    addChatMessage(
                        ChatMessage(
                            role = "ai",
                            text = "Привет! Я AI-помощник по топливу. Могу найти ближайшую АЗС, построить маршрут, подсказать цены и расход. Что сделать?",
                            ts = now
                        )
                    )
                    _pendingRouteMode.value = PendingRouteMode.NONE
                    _isAnalyzing.value = false
                    return@launch
                }

                _error.value = null
                _userAnswer.value = null

                val context = buildUserContext()
                val fullPrompt = if (context.text.isNotBlank()) {
                    "${context.text}\n\nВопрос пользователя: $question"
                } else question

                val history = _chatMessages.value.takeLast(6)

                val answer = aiRouter.ask(fullPrompt, history = history)
                val cleanAnswer = answer.replace("**", "").replace("*", "")
                _userAnswer.value = cleanAnswer

                addChatMessage(ChatMessage(role = "user", text = question, ts = System.currentTimeMillis()))
                addChatMessage(ChatMessage(role = "ai", text = cleanAnswer, ts = System.currentTimeMillis()))

                detectIntent(question)
            } catch (e: Exception) {
                _error.value = e.message ?: "Ошибка при запросе к AI"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private suspend fun detectIntent(question: String) {
        val lowerQuestion = question.lowercase()
        val hasRouteKeyword = listOf("маршрут", "построй", "доведи", "ближайшая", "дешевле").any { lowerQuestion.contains(it) }
        val hasFuelKeyword = listOf("топливо", "цена", "наличие", "заправка").any { lowerQuestion.contains(it) }
        val hasSpecificFuelType = Regex("где.*92|где.*95|где.*98|где.*дт").containsMatchIn(lowerQuestion)

        var currentStations = _stations.value
        if (currentStations.isEmpty()) {
            val location = userLocation.value?.let { Location("").apply { latitude = it.first; longitude = it.second } }
                ?: getLastLocation()
            if (location != null) {
                try {
                    val loaded = gasStationRepository.getNearbyStations(location.latitude, location.longitude, 50.0)
                    if (loaded.isNotEmpty()) {
                        _stations.value = loaded
                        updateBestStation()
                        currentStations = loaded
                    }
                } catch (_: Exception) {}
            }
            if (currentStations.isEmpty()) {
                try {
                    val all = gasStationRepository.getAllStations()
                    if (all.isNotEmpty()) {
                        _stations.value = all
                        updateBestStation()
                        currentStations = all
                    }
                } catch (_: Exception) {}
            }
        }

        if (hasRouteKeyword || hasSpecificFuelType) {
            val userLat = _userLocation.value?.first
            val userLon = _userLocation.value?.second
            val selectedStation = _bestStation.value
                ?: currentStations.minByOrNull { GeoUtils.calculateDistance(userLat ?: 0.0, userLon ?: 0.0, it.latitude, it.longitude) }
                ?: currentStations.minByOrNull { it.fuelTypes.filter { ft -> ft.available }.minByOrNull { ft -> ft.price }?.price ?: Double.MAX_VALUE }

            selectedStation?.let { station ->
                _pendingRouteStationId.value = station.id
                _pendingRouteMode.value = PendingRouteMode.ROUTE
            }
        } else if (hasFuelKeyword && !hasRouteKeyword) {
            _pendingRouteMode.value = PendingRouteMode.CARD
        }
    }


    fun onRouteHandoffConsumed() {
        _pendingRouteStationId.value = null
        _pendingRouteMode.value = PendingRouteMode.NONE
    }

    fun onCardHandoffConsumed() {
        _pendingOpenStationId.value = null
        _pendingRouteMode.value = PendingRouteMode.NONE
    }
}