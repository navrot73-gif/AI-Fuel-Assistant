package com.navrot.aifuelassistant.features.dashboard.delegate

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.features.dashboard.ChatMessage
import com.navrot.aifuelassistant.features.dashboard.DashboardViewModel.PendingRouteMode
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.ui.common.ErrorContext
import com.navrot.aifuelassistant.ui.common.ErrorMessageMapper
import com.navrot.aifuelassistant.util.Format
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

class AiChatDelegate @Inject constructor(
    private val aiRouter: AiRouter,
    private val routeStateManager: RouteStateManager,
    private val gasStationRepository: GasStationRepositoryInterface,
    @ApplicationContext private val applicationContext: Context
) {
    private val _userQuestion = MutableStateFlow("")
    val userQuestion: StateFlow<String> = _userQuestion.asStateFlow()

    private val _userAnswer = MutableStateFlow<String?>(null)
    val userAnswer: StateFlow<String?> = _userAnswer.asStateFlow()

    private val _pendingRouteStationId = MutableStateFlow<Int?>(null)
    val pendingRouteStationId: StateFlow<Int?> = _pendingRouteStationId.asStateFlow()

    private val _pendingRouteMode = MutableStateFlow(PendingRouteMode.NONE)
    val pendingRouteMode: StateFlow<PendingRouteMode> = _pendingRouteMode.asStateFlow()

    private val _pendingOpenStationId = MutableStateFlow<Int?>(null)
    val pendingOpenStationId: StateFlow<Int?> = _pendingOpenStationId.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val chatPrefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    companion object {
        private const val TAG = "AiChatDelegate"
    }

    init {
        loadChatHistory()
    }

    fun updateUserLocation(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
    }

    fun setUserQuestion(text: String) {
        _userQuestion.value = text
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

    private suspend fun buildUserContext(stationsFallback: List<GasStation>): UserContext {
        val location = getLastLocation()
            ?: return UserContext("", null)

        val lat = location.latitude
        val lon = location.longitude

        val city = GeoUtils.hardcodedDetectCity(lat, lon)

        val nearbyStations = try {
            gasStationRepository.getNearbyStations(lat, lon, 50.0)
                .sortedBy { GeoUtils.calculateDistance(lat, lon, it.latitude, it.longitude) }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to fetch nearby stations for user context: %s", e.message)
            emptyList<GasStation>()
        }

        val stationsList = if (nearbyStations.isNotEmpty()) nearbyStations else stationsFallback
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

    fun askUserQuestion(
        scope: CoroutineScope,
        recommendationDelegate: StationRecommendationDelegate
    ) {
        val question = _userQuestion.value.trim()
        if (question.isEmpty() || _isAnalyzing.value) return

        scope.launch {
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
                    return@launch
                }

                addChatMessage(ChatMessage(role = "user", text = question, ts = System.currentTimeMillis()))

                _error.value = null
                _userAnswer.value = null

                try {
                    val context = buildUserContext(recommendationDelegate.stations.value)
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
                            detectIntent(question, recommendationDelegate)
                        }
                    } else {
                        val answer = rawAnswer.replace("**", "").replace("*", "")
                        _userAnswer.value = answer
                        addChatMessage(ChatMessage(role = "ai", text = answer, ts = System.currentTimeMillis()))
                        detectIntent(question, recommendationDelegate)
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
                    detectIntent(question, recommendationDelegate)
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
                    detectIntent(question, recommendationDelegate)
                }
            } catch (topLevelError: Exception) {
                Timber.tag(TAG).e(topLevelError, "Unhandled error in askUserQuestion")
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

    private suspend fun detectIntent(
        question: String,
        recommendationDelegate: StationRecommendationDelegate
    ) {
        routeStateManager.resetForNewIntent()
        val lowerQuestion = question.lowercase()
        val brands = listOf("газпром", "роснефть", "татнефть", "смарт", "шелл", "лукойл")
        val mentionedBrand = brands.find { lowerQuestion.contains(it) }

        val hasRouteKeyword = listOf("маршрут", "построй", "доведи", "ближайшая", "дешевле").any { lowerQuestion.contains(it) }
        val hasFuelKeyword = listOf("топливо", "цена", "наличие", "заправка").any { lowerQuestion.contains(it) }
        val hasSpecificFuelType = Regex("где.*92|где.*95|где.*98|где.*дт").containsMatchIn(lowerQuestion)

        if (hasRouteKeyword || hasSpecificFuelType || hasFuelKeyword || mentionedBrand != null) {
            if (recommendationDelegate.stations.value.isEmpty()) {
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
                recommendationDelegate.setStations(stationsList)
            }

            val userLat = _userLocation.value?.first ?: 0.0
            val userLon = _userLocation.value?.second ?: 0.0
            val currentStations = recommendationDelegate.stations.value

            if (hasRouteKeyword || hasSpecificFuelType || mentionedBrand != null) {
                var selectedStation: GasStation? = null

                if (mentionedBrand != null) {
                    val matchingStations = currentStations.filter { st ->
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
                    selectedStation = recommendationDelegate.bestStation.value
                        ?: currentStations.minByOrNull { GeoUtils.calculateDistance(userLat, userLon, it.latitude, it.longitude) }
                        ?: currentStations.minByOrNull { it.fuelTypes.filter { ft -> ft.available }.minByOrNull { ft -> ft.price }?.price ?: Double.MAX_VALUE }
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
