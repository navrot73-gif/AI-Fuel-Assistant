package com.navrot.aifuelassistant.features.dashboard.delegate

import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.database.entity.toPersonalFuelEvent
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.stationListSignature
import com.navrot.aifuelassistant.data.repository.PredictiveRepository
import com.navrot.aifuelassistant.domain.predictive.EventSource
import com.navrot.aifuelassistant.domain.predictive.UserAction
import com.navrot.aifuelassistant.domain.predictive.UserOutcome
import com.navrot.aifuelassistant.domain.predictive.usecase.EvaluateRecommendationOutcomeUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.PersonalStationPreferenceUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.PredictConsumptionUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.PredictTripFuelCostUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.RecordRecommendationFeedbackUseCase
import com.navrot.aifuelassistant.domain.recommendation.BestStationResult
import com.navrot.aifuelassistant.domain.recommendation.BestStationUseCase
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.smart.GetSmartFuelRecommendationUseCase
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.features.dashboard.BestStationUiState
import com.navrot.aifuelassistant.features.dashboard.FeedbackUiState
import com.navrot.aifuelassistant.features.dashboard.FeedbackUiStatus
import com.navrot.aifuelassistant.geo.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

class StationRecommendationDelegate @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val gasStationRepository: GasStationRepositoryInterface,
    private val getBestStationsUseCase: GetBestStationsUseCase,
    private val bestStationUseCase: BestStationUseCase = BestStationUseCase(),
    private val getSmartFuelRecommendationUseCase: GetSmartFuelRecommendationUseCase = GetSmartFuelRecommendationUseCase(),
    private val predictConsumptionUseCase: PredictConsumptionUseCase = PredictConsumptionUseCase(),
    private val predictTripFuelCostUseCase: PredictTripFuelCostUseCase = PredictTripFuelCostUseCase(),
    private val personalStationPreferenceUseCase: PersonalStationPreferenceUseCase = PersonalStationPreferenceUseCase(),
    private val recordRecommendationFeedbackUseCase: RecordRecommendationFeedbackUseCase = RecordRecommendationFeedbackUseCase(),
    private val evaluateRecommendationOutcomeUseCase: EvaluateRecommendationOutcomeUseCase = EvaluateRecommendationOutcomeUseCase(),
    private val fuelRecordRepository: FuelRecordRepository? = null,
    private val predictiveRepository: PredictiveRepository? = null
) {
    private val _vehicles = MutableStateFlow<List<VehicleEntity>>(emptyList())
    val vehicles: StateFlow<List<VehicleEntity>> = _vehicles.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)
    val selectedVehicleId: StateFlow<Long?> = _selectedVehicleId.asStateFlow()

    private val _selectedFuelType = MutableStateFlow("АИ-95")
    val selectedFuelType: StateFlow<String> = _selectedFuelType.asStateFlow()

    private val _stations = MutableStateFlow<List<GasStation>>(emptyList())
    val stations: StateFlow<List<GasStation>> = _stations.asStateFlow()

    private val _bestStation = MutableStateFlow<GasStation?>(null)
    val bestStation: StateFlow<GasStation?> = _bestStation.asStateFlow()

    private val _bestStationUiState = MutableStateFlow(BestStationUiState())
    val bestStationUiState: StateFlow<BestStationUiState> = _bestStationUiState.asStateFlow()

    private var userLat: Double? = null
    private var userLon: Double? = null

    private var lastStationsSignature: String? = null

    companion object {
        private const val TAG = "StationRecommendationDelegate"
    }

    fun loadVehicles(scope: CoroutineScope, onVehicleSelected: (Long) -> Unit) {
        scope.launch {
            vehicleRepository.getAllVehicles()
                .catch { e -> Timber.tag(TAG).e(e, "Error collecting vehicles") }
                .collect { list ->
                    _vehicles.value = list
                    if (_selectedVehicleId.value == null && list.isNotEmpty()) {
                        val firstId = list.first().id
                        _selectedVehicleId.value = firstId
                        onVehicleSelected(firstId)
                    }
                }
        }
    }

    fun selectVehicle(vehicleId: Long, scope: CoroutineScope? = null) {
        _selectedVehicleId.value = vehicleId
        updateBestStation(scope)
    }

    fun selectFuelType(fuelType: String, scope: CoroutineScope? = null) {
        _selectedFuelType.value = fuelType
        updateBestStation(scope)
    }

    fun updateUserLocation(lat: Double?, lon: Double?, scope: CoroutineScope? = null) {
        this.userLat = lat
        this.userLon = lon
        updateBestStation(scope)
    }

    fun loadStations(scope: CoroutineScope) {
        scope.launch {
            try {
                _stations.value = gasStationRepository.getAllStations()
                updateBestStation(scope)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load stations: %s", e.message)
            }
        }
    }

    fun setStations(stations: List<GasStation>, scope: CoroutineScope? = null) {
        val newSig = stations.stationListSignature()
        if (newSig == lastStationsSignature && _stations.value.isNotEmpty()) {
            return
        }
        lastStationsSignature = newSig
        _stations.value = stations
        updateBestStation(scope)
    }

    fun recordRouteStartedFeedback(chosenStationId: Int, scope: CoroutineScope? = null) {
        val currentBestId = _bestStationUiState.value.smartRecommendation?.stationId?.toLong()
            ?: _bestStationUiState.value.recommendation?.station?.id?.toLong()
            ?: chosenStationId.toLong()

        val recId = UUID.randomUUID().toString()

        if (predictiveRepository != null) {
            val feedback = recordRecommendationFeedbackUseCase.createFeedback(
                recommendationId = recId,
                recommendedStationId = currentBestId,
                chosenStationId = chosenStationId.toLong(),
                fuelType = _selectedFuelType.value,
                action = UserAction.ROUTE_STARTED,
                outcome = UserOutcome.UNKNOWN,
                routeStarted = true
            )
            val launchScope = scope ?: CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            launchScope.launch {
                try {
                    predictiveRepository.recordFeedback(feedback)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error saving recommendation feedback")
                }
            }
        }

        _bestStationUiState.value = _bestStationUiState.value.copy(
            feedbackUiState = FeedbackUiState(
                status = FeedbackUiStatus.PROMPT,
                submittedFeedbackId = recId
            )
        )
    }

    fun onRefuelPromptAnswer(confirmed: Boolean?, scope: CoroutineScope? = null) {
        val currentRec = _bestStationUiState.value.smartRecommendation
        val currentStationId = currentRec?.stationId?.toLong()
            ?: _bestStationUiState.value.recommendation?.station?.id?.toLong()
            ?: return

        when (confirmed) {
            true -> {
                _bestStationUiState.value = _bestStationUiState.value.copy(
                    feedbackUiState = _bestStationUiState.value.feedbackUiState.copy(
                        status = FeedbackUiStatus.DETAILS,
                        isRefuelConfirmed = true
                    )
                )
            }
            false -> {
                // Negative / Arrived without refuel
                val feedback = recordRecommendationFeedbackUseCase.createFeedback(
                    recommendationId = _bestStationUiState.value.feedbackUiState.submittedFeedbackId ?: UUID.randomUUID().toString(),
                    recommendedStationId = currentStationId,
                    chosenStationId = currentStationId,
                    fuelType = _selectedFuelType.value,
                    action = UserAction.ARRIVED,
                    outcome = UserOutcome.FAILED,
                    predictedAvailability = currentRec?.availability,
                    actualAvailability = FuelAvailabilityStatus.UNAVAILABLE,
                    userConfirmed = true,
                    source = EventSource.USER_CONFIRMED
                )

                val launchScope = scope ?: CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                launchScope.launch {
                    try {
                        predictiveRepository?.recordFeedback(feedback)
                        updateBestStation(this)
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Error saving negative feedback")
                    }
                }

                _bestStationUiState.value = _bestStationUiState.value.copy(
                    feedbackUiState = FeedbackUiState(
                        status = FeedbackUiStatus.SUBMITTED,
                        isRefuelConfirmed = false
                    )
                )
            }
            null -> {
                // Skipped / Unknown
                val feedback = recordRecommendationFeedbackUseCase.createFeedback(
                    recommendationId = _bestStationUiState.value.feedbackUiState.submittedFeedbackId ?: UUID.randomUUID().toString(),
                    recommendedStationId = currentStationId,
                    chosenStationId = currentStationId,
                    fuelType = _selectedFuelType.value,
                    action = UserAction.SKIPPED,
                    outcome = UserOutcome.UNKNOWN,
                    userConfirmed = false,
                    source = EventSource.USER_CONFIRMED
                )

                val launchScope = scope ?: CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                launchScope.launch {
                    try {
                        predictiveRepository?.recordFeedback(feedback)
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Error saving skipped feedback")
                    }
                }

                _bestStationUiState.value = _bestStationUiState.value.copy(
                    feedbackUiState = FeedbackUiState(status = FeedbackUiStatus.CANCELLED)
                )
            }
        }
    }

    fun submitRefuelDetails(
        fuelAvailable: Boolean,
        priceMatched: Boolean,
        actualPrice: Double?,
        hasQueue: Boolean,
        queueMinutes: Int?,
        scope: CoroutineScope? = null
    ) {
        val currentRec = _bestStationUiState.value.smartRecommendation
        val currentStation = currentRec?.station ?: _bestStationUiState.value.recommendation?.station ?: return
        val currentStationId = currentStation.id.toLong()
        val fuelType = _selectedFuelType.value

        val predPrice = currentRec?.price ?: currentStation.fuelTypes.find { it.type == fuelType }?.price
        val finalActualPrice = if (priceMatched) predPrice else actualPrice ?: predPrice

        val predQueue = currentRec?.queueTimeMinutes ?: currentStation.queueTime
        val finalActualQueue = if (!hasQueue) 0 else queueMinutes ?: predQueue

        val feedback = recordRecommendationFeedbackUseCase.createFeedback(
            recommendationId = _bestStationUiState.value.feedbackUiState.submittedFeedbackId ?: UUID.randomUUID().toString(),
            recommendedStationId = currentStationId,
            chosenStationId = currentStationId,
            fuelType = fuelType,
            action = UserAction.REFUELLED,
            outcome = if (fuelAvailable) UserOutcome.SUCCESS else UserOutcome.FAILED,
            predictedAvailability = currentRec?.availability,
            actualAvailability = if (fuelAvailable) FuelAvailabilityStatus.AVAILABLE else FuelAvailabilityStatus.UNAVAILABLE,
            predictedPrice = predPrice,
            actualPrice = finalActualPrice,
            predictedQueue = predQueue,
            actualQueue = finalActualQueue,
            userConfirmed = true,
            source = EventSource.USER_CONFIRMED
        )

        val launchScope = scope ?: CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        launchScope.launch {
            try {
                predictiveRepository?.recordFeedback(feedback)

                if (fuelAvailable && fuelRecordRepository != null && _selectedVehicleId.value != null) {
                    val record = FuelRecordEntity(
                        vehicleId = _selectedVehicleId.value!!,
                        date = System.currentTimeMillis(),
                        mileage = 0.0,
                        fuelAmount = 0.0,
                        pricePerLiter = finalActualPrice ?: 0.0,
                        totalCost = 0.0,
                        fuelType = fuelType,
                        stationName = currentStation.name,
                        notes = "Confirmed refuel feedback",
                        stationId = currentStation.id,
                        latitude = currentStation.latitude,
                        longitude = currentStation.longitude
                    )
                    fuelRecordRepository.insert(record)
                }

                updateBestStation(this)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error recording refuel feedback details")
            }
        }

        _bestStationUiState.value = _bestStationUiState.value.copy(
            feedbackUiState = FeedbackUiState(
                status = FeedbackUiStatus.SUBMITTED,
                isRefuelConfirmed = fuelAvailable
            )
        )
    }

    fun dismissFeedback() {
        _bestStationUiState.value = _bestStationUiState.value.copy(
            feedbackUiState = FeedbackUiState(status = FeedbackUiStatus.CANCELLED)
        )
    }

    fun updateBestStation(scope: CoroutineScope? = null) {
        val fuelType = _selectedFuelType.value
        val currentStations = _stations.value

        if (currentStations.isEmpty()) {
            _bestStation.value = null
            _bestStationUiState.value = BestStationUiState(
                isLoading = false,
                recommendation = null,
                alternatives = emptyList(),
                error = null
            )
            return
        }

        val result: BestStationResult = bestStationUseCase.execute(
            stations = currentStations,
            fuelType = fuelType,
            userLat = userLat,
            userLon = userLon
        )

        val smartResult = getSmartFuelRecommendationUseCase.execute(
            stations = currentStations,
            fuelType = fuelType,
            userLat = userLat,
            userLon = userLon,
            vehicleId = _selectedVehicleId.value
        )

        val bestStationModel = smartResult.topRecommendation?.station ?: result.best?.station
        if (_bestStation.value != bestStationModel) {
            _bestStation.value = bestStationModel
            if (bestStationModel != null) {
                val elapsed = System.currentTimeMillis() - com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker.t0Ms
                Timber.tag("StartupTimeline").i("T+%dms recommendation_shown (%s)", elapsed, bestStationModel.name)
            }
        }

        val prevFeedbackUiState = _bestStationUiState.value.feedbackUiState

        _bestStationUiState.value = BestStationUiState(
            isLoading = false,
            recommendation = result.best,
            alternatives = result.alternatives.take(2),
            smartRecommendation = smartResult.topRecommendation,
            smartAlternatives = smartResult.alternatives,
            error = null,
            feedbackUiState = prevFeedbackUiState
        )

        if (bestStationModel != null && scope != null && fuelRecordRepository != null) {
            val vehicleId = _selectedVehicleId.value
            if (vehicleId != null) {
                scope.launch {
                    try {
                        val records = fuelRecordRepository.getByVehicleId(vehicleId).firstOrNull() ?: emptyList()
                        val events = records.map { it.toPersonalFuelEvent() }
                        val feedbacks = predictiveRepository?.getAllFeedbacks() ?: emptyList()

                        val updatedSmartResult = getSmartFuelRecommendationUseCase.execute(
                            stations = currentStations,
                            fuelType = fuelType,
                            userLat = userLat,
                            userLon = userLon,
                            vehicleId = vehicleId,
                            personalEvents = events,
                            feedbacks = feedbacks
                        )

                        val learning = personalStationPreferenceUseCase.getStationLearning(
                            stationId = bestStationModel.id,
                            events = events,
                            feedbacks = feedbacks
                        )

                        val personalVisitCount = if (learning.refuelsCount > 0 || learning.successfulRefuelCount > 0) {
                            maxOf(learning.refuelsCount, learning.successfulRefuelCount)
                        } else null
                        val topSmartRec = updatedSmartResult.topRecommendation

                        val consumptionPred = predictConsumptionUseCase(
                            vehicleId = vehicleId,
                            fuelType = fuelType,
                            events = events
                        )

                        val distKm = if (userLat != null && userLon != null && userLat != 0.0 && userLon != 0.0) {
                            GeoUtils.calculateDistance(userLat!!, userLon!!, bestStationModel.latitude, bestStationModel.longitude)
                        } else null

                        val expectedPrice = bestStationModel.fuelTypes.find { it.type == fuelType }?.price

                        val tripCost = predictTripFuelCostUseCase(
                            distanceKm = distKm,
                            consumptionPrediction = consumptionPred,
                            expectedPricePerLiter = expectedPrice
                        )

                        _bestStationUiState.value = _bestStationUiState.value.copy(
                            smartRecommendation = topSmartRec,
                            smartAlternatives = updatedSmartResult.alternatives,
                            tripCostPrediction = tripCost,
                            personalVisitCount = personalVisitCount,
                            feedbackUiState = prevFeedbackUiState
                        )
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Error evaluating predictive stats for best station")
                    }
                }
            }
        }
    }
}
