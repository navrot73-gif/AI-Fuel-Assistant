package com.navrot.aifuelassistant.features.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.navrot.aifuelassistant.ai.FuelAnalysisPromptBuilder
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.ui.common.ErrorContext
import com.navrot.aifuelassistant.ui.common.ErrorMessageMapper
import com.navrot.aifuelassistant.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
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
    private val routeStateManager: RouteStateManager,
    private val getBestStationsUseCase: GetBestStationsUseCase,
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

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    private fun loadChatHistory() {
        try {
            val json = chatPrefs.getString("messages", "[]") ?: "[]"
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val loaded: List<ChatMessage>? = gson.fromJson(json, type)
            _chatMessages.value = loaded?.filterNotNull() ?: emptyList()
        } catch (e: com.google.gson.JsonSyntaxException) {
            Timber.tag(TAG).e(e, "Failed to parse chat history JSON: %s", e.message)
            _chatMessages.value = emptyList()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load chat history: %s", e.message)
            _chatMessages.value = emptyList()
        }
    }

    private fun saveChatHistory(messages: List<ChatMessage>) {
        try {
            val json = gson.toJson(messages)
            chatPrefs.edit().putString("messages", json).apply()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save chat history: %s", e.message)
        }
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
                .catch { e -> Timber.tag(TAG).e(e, "Error collecting vehicles") }
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
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load stations: %s", e.message)
                // Если не удалось загрузить — оставляем пустой список
            }
        }
    }

    private fun updateBestStation() {
        val fuelType = _selectedFuelType.value
        val best = _stations.value
            .filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .minByOrNull { s ->
                getBestStationsUseCase.calculateScore(s, fuelType)
            }
        _bestStation.value = best
    }

    private fun loadMetrics() {
        viewModelScope.launch {
            try {
                val vehicleId = _selectedVehicleId.value
                val flow = if (vehicleId != null && vehicleId > 0) {
                    fuelRecordRepository.getByVehicleId(vehicleId)
                } else {
                    fuelRecordRepository.getAll()
                }
                flow
                    .catch { e ->
                        Timber.tag("Metrics").e(e, "Error loading fuel records")
                        _metrics.value = DashboardMetrics()
                        _weeklyConsumption.value = emptyList()
                    }
                    .collect { records ->
                        try {
                            _metrics.value = computeMetrics(records)
                            _weeklyConsumption.value = computeWeeklyConsumption(records)
                        } catch (e: Exception) {
                            Timber.tag("Metrics").e(e, "Error computing metrics")
                            _metrics.value = DashboardMetrics()
                            _weeklyConsumption.value = emptyList()
                        }
                    }
            } catch (e: Exception) {
                Timber.tag("Metrics").e(e, "Error in loadMetrics")
                _metrics.value = DashboardMetrics()
                _weeklyConsumption.value = emptyList()
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

        val dayNames = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
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
                    val calDay = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon, ..., 7=Sat
                    val dayOfWeek = if (calDay == Calendar.SUNDAY) 7 else calDay - 1 // 1=Mon..7=Sun
                    grouped.getOrPut(dayOfWeek) { mutableListOf() }.add(consumption)
                }
            }
        }

        val result = mutableListOf<Pair<String, Float>>()
        for (day in 1..7) { // 1=Mon..7=Sun
            val consumptions = grouped[day]
            if (consumptions != null && consumptions.isNotEmpty()) {
                val avg = consumptions.average().toFloat()
                val idx = (day - 1).coerceIn(0, 6)
                result.add(dayNames[idx] to avg)
            }
        }

        return result
    }

    fun setUserQuestion(text: String) {
        _userQuestion.value = text
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocation(): Location? = try {
        suspendCancellableCoroutine { cont ->
            val fusedClient = LocationServices.getFusedLocationProviderClient(
                applicationContext
            )
            fusedClient.lastLocation.addOnSuccessListener { location ->
                cont.resume(location)
            }.addOnFailureListener { e ->
                Timber.tag(TAG).w("Location provider failed: %s", e.message)
                cont.resume(null)
            }
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w("Failed to get last location: %s", e.message)
        null
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
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to fetch nearby stations for user context: %s", e.message)
            emptyList<GasStation>()
        }

        val stationsList = if (nearbyStations.isNotEmpty()) nearbyStations else _stations.value
        val stationsInfo = if (stationsList.isNotEmpty()) {
            stationsList.joinToString("\n") { station ->
                val price = station.fuelTypes
                    .filter { it.available }
                    .minByOrNull { it.price }?.price
                    ?: 0.0
                "[${station.id}] ${station.brand} (${station.name}), ${station.address}, ${Format.price(price)}₽"
            }
        } else "нет доступных АЗС"

        val text = "Пользователь: $lat, $lon, город: $city.\nСписок АЗС:\n$stationsInfo"
        return UserContext(text, stationsList.firstOrNull()?.id)
    }

    fun askUserQuestion() {
        val question = _userQuestion.value.trim()
        if (question.isEmpty() || _isAnalyzing.value) return

        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                // 1. greeting-проверка (как есть)
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
                    return@launch
                }

                // 2. addChatMessage(user-сообщение) — СРАЗУ, до любого AI-запроса
                addChatMessage(ChatMessage(role = "user", text = question, ts = System.currentTimeMillis()))

                // 3. try { aiRouter.ask } catch (e)
                _error.value = null
                _userAnswer.value = null

                try {
                    val context = buildUserContext()
                    val fullPrompt = if (context.text.isNotBlank()) {
                        "${context.text}\n\nВопрос пользователя: $question"
                    } else question

                    val history = _chatMessages.value.takeLast(6)

                    val rawAnswer = withTimeout(20_000L) {
                        aiRouter.ask(fullPrompt, history = history)
                    }
                    val routeTagRegex = Regex("\\[ROUTE:(\\d+)\\]")
                    val match = routeTagRegex.find(rawAnswer)

                    if (match != null) {
                        val stationId = match.groupValues[1].toIntOrNull()
                        val cleanedAnswer = rawAnswer.replace(routeTagRegex, "").replace("**", "").replace("*", "").trim()
                        _userAnswer.value = cleanedAnswer
                        addChatMessage(ChatMessage(role = "ai", text = cleanedAnswer, ts = System.currentTimeMillis()))

                        if (stationId != null) {
                            _pendingRouteStationId.value = stationId
                            routeStateManager.setPendingRouteStationId(stationId)
                            _pendingRouteMode.value = PendingRouteMode.ROUTE
                        } else {
                            detectIntent(question)
                        }
                    } else {
                        val answer = rawAnswer.replace("**", "").replace("*", "")
                        _userAnswer.value = answer
                        addChatMessage(ChatMessage(role = "ai", text = answer, ts = System.currentTimeMillis()))
                        detectIntent(question)
                    }
                } catch (e: TimeoutCancellationException) {
                    val userMsg = ErrorMessageMapper.mapToUserMessage(
                        java.net.SocketTimeoutException("AI timeout"),
                        ErrorContext.AI
                    )
                    _error.value = userMsg
                    addChatMessage(
                        ChatMessage(
                            role = "ai",
                            text = userMsg,
                            ts = System.currentTimeMillis()
                        )
                    )
                    detectIntent(question)
                } catch (e: Exception) {
                    val userMsg = ErrorMessageMapper.mapToUserMessage(e, ErrorContext.AI)
                    _error.value = userMsg
                    addChatMessage(
                        ChatMessage(
                            role = "ai",
                            text = userMsg,
                            ts = System.currentTimeMillis()
                        )
                    )
                    detectIntent(question)
                }
            } catch (topLevelError: Exception) {
                Timber.tag("DashboardViewModel").e(topLevelError, "Unhandled error in askUserQuestion")
                val userMsg = ErrorMessageMapper.mapToUserMessage(topLevelError, ErrorContext.AI)
                addChatMessage(
                    ChatMessage(
                        role = "ai",
                        text = userMsg,
                        ts = System.currentTimeMillis()
                    )
                )
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private suspend fun detectIntent(question: String) {
        routeStateManager.resetForNewIntent()
        val lowerQuestion = question.lowercase()
        val brands = listOf("газпром", "роснефть", "татнефть", "смарт", "шелл", "лукойл")
        val mentionedBrand = brands.find { lowerQuestion.contains(it) }

        val hasRouteKeyword = listOf("маршрут", "построй", "доведи", "ближайшая", "дешевле").any { lowerQuestion.contains(it) }
        val hasFuelKeyword = listOf("топливо", "цена", "наличие", "заправка").any { lowerQuestion.contains(it) }
        val hasSpecificFuelType = Regex("где.*92|где.*95|где.*98|где.*дт").containsMatchIn(lowerQuestion)

        if (hasRouteKeyword || hasSpecificFuelType || hasFuelKeyword || mentionedBrand != null) {
            if (_stations.value.isEmpty()) {
                val loc = _userLocation.value ?: getLastLocation()?.let { it.latitude to it.longitude }
                val stationsList = if (loc != null) {
                    try {
                        gasStationRepository.getNearbyStations(loc.first, loc.second, 50.0)
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("Failed to fetch nearby stations in detectIntent: %s", e.message)
                        try {
                            gasStationRepository.getAllStations()
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Failed to fetch all stations fallback in detectIntent")
                            emptyList()
                        }
                    }
                } else {
                    try {
                        gasStationRepository.getAllStations()
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to fetch all stations in detectIntent")
                        emptyList()
                    }
                }
                _stations.value = stationsList
                updateBestStation()
            }

            val userLat = _userLocation.value?.first ?: 0.0
            val userLon = _userLocation.value?.second ?: 0.0

            if (hasRouteKeyword || hasSpecificFuelType || mentionedBrand != null) {
                var selectedStation: GasStation? = null

                if (mentionedBrand != null) {
                    val matchingStations = _stations.value.filter { st ->
                        st.name.lowercase().contains(mentionedBrand) ||
                                st.brand.lowercase().contains(mentionedBrand)
                    }
                    if (matchingStations.isNotEmpty()) {
                        selectedStation = matchingStations.minByOrNull { st ->
                            GeoUtils.calculateDistance(userLat, userLon, st.latitude, st.longitude)
                        }
                    }
                }

                if (selectedStation == null) {
                    selectedStation = _bestStation.value
                        ?: _stations.value.minByOrNull { GeoUtils.calculateDistance(userLat, userLon, it.latitude, it.longitude) }
                        ?: _stations.value.minByOrNull { it.fuelTypes.filter { ft -> ft.available }.minByOrNull { ft -> ft.price }?.price ?: Double.MAX_VALUE }
                }

                selectedStation?.let { station ->
                    _pendingRouteStationId.value = station.id
                    routeStateManager.setPendingRouteStationId(station.id)
                    _pendingRouteMode.value = PendingRouteMode.ROUTE
                }
            } else if (hasFuelKeyword) {
                _pendingRouteMode.value = PendingRouteMode.CARD
            }
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