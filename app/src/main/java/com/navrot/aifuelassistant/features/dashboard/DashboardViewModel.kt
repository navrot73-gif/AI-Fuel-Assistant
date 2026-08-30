package com.navrot.aifuelassistant.features.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.features.dashboard.delegate.AiChatDelegate
import com.navrot.aifuelassistant.features.dashboard.delegate.DashboardMetricsDelegate
import com.navrot.aifuelassistant.features.dashboard.delegate.StationRecommendationDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
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
    private val metricsDelegate: DashboardMetricsDelegate,
    private val recommendationDelegate: StationRecommendationDelegate,
    private val aiChatDelegate: AiChatDelegate,
) : ViewModel() {

    // Вторичный конструктор для обратной совместимости в тестах и при необходимости прямого инжекта
    constructor(
        fuelRecordRepository: FuelRecordRepository,
        vehicleRepository: VehicleRepository,
        gasStationRepository: GasStationRepositoryInterface,
        aiRouter: AiRouter,
        routeStateManager: RouteStateManager,
        getBestStationsUseCase: GetBestStationsUseCase,
        applicationContext: Context
    ) : this(
        metricsDelegate = DashboardMetricsDelegate(fuelRecordRepository),
        recommendationDelegate = StationRecommendationDelegate(
            vehicleRepository = vehicleRepository,
            gasStationRepository = gasStationRepository,
            getBestStationsUseCase = getBestStationsUseCase
        ),
        aiChatDelegate = AiChatDelegate(
            aiRouter = aiRouter,
            routeStateManager = routeStateManager,
            gasStationRepository = gasStationRepository,
            applicationContext = applicationContext
        )
    )

    enum class PendingRouteMode { NONE, ROUTE, CARD }

    // StateFlows delegators
    val metrics: StateFlow<DashboardMetrics> = metricsDelegate.metrics
    val weeklyConsumption: StateFlow<List<Pair<String, Float>>> = metricsDelegate.weeklyConsumption

    val selectedFuelType: StateFlow<String> = recommendationDelegate.selectedFuelType
    val stations: StateFlow<List<GasStation>> = recommendationDelegate.stations
    val bestStation: StateFlow<GasStation?> = recommendationDelegate.bestStation
    val vehicles: StateFlow<List<VehicleEntity>> = recommendationDelegate.vehicles
    val selectedVehicleId: StateFlow<Long?> = recommendationDelegate.selectedVehicleId

    val userQuestion: StateFlow<String> = aiChatDelegate.userQuestion
    val userAnswer: StateFlow<String?> = aiChatDelegate.userAnswer
    val pendingRouteStationId: StateFlow<Int?> = aiChatDelegate.pendingRouteStationId
    val pendingRouteMode: StateFlow<PendingRouteMode> = aiChatDelegate.pendingRouteMode
    val pendingOpenStationId: StateFlow<Int?> = aiChatDelegate.pendingOpenStationId
    val isAnalyzing: StateFlow<Boolean> = aiChatDelegate.isAnalyzing
    val error: StateFlow<String?> = aiChatDelegate.error
    val userLocation: StateFlow<Pair<Double, Double>?> = aiChatDelegate.userLocation
    val chatMessages: StateFlow<List<ChatMessage>> = aiChatDelegate.chatMessages

    init {
        recommendationDelegate.loadVehicles(viewModelScope) { firstVehicleId ->
            metricsDelegate.loadMetrics(viewModelScope, firstVehicleId)
        }
        recommendationDelegate.loadStations(viewModelScope)
        metricsDelegate.loadMetrics(viewModelScope, recommendationDelegate.selectedVehicleId.value)
    }

    fun updateUserLocation(lat: Double, lon: Double) {
        aiChatDelegate.updateUserLocation(lat, lon)
    }

    fun addChatMessage(message: ChatMessage) {
        aiChatDelegate.addChatMessage(message)
    }

    fun clearChatHistory() {
        aiChatDelegate.clearChatHistory()
    }

    fun selectVehicle(vehicleId: Long) {
        recommendationDelegate.selectVehicle(vehicleId)
        metricsDelegate.loadMetrics(viewModelScope, vehicleId)
    }

    fun selectFuelType(fuelType: String) {
        recommendationDelegate.selectFuelType(fuelType)
    }

    fun setUserQuestion(text: String) {
        aiChatDelegate.setUserQuestion(text)
    }

    fun askUserQuestion(text: String? = null) {
        aiChatDelegate.askUserQuestion(viewModelScope, recommendationDelegate, text)
    }

    fun onRouteHandoffConsumed() {
        aiChatDelegate.onRouteHandoffConsumed()
    }

    fun onCardHandoffConsumed() {
        aiChatDelegate.onCardHandoffConsumed()
    }
}
