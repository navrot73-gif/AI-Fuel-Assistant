package com.navrot.aifuelassistant.features.dashboard.delegate

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.stationListSignature
import com.navrot.aifuelassistant.domain.recommendation.BestStationUseCase
import com.navrot.aifuelassistant.domain.recommendation.BestStationResult
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.features.dashboard.BestStationUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class StationRecommendationDelegate @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val gasStationRepository: GasStationRepositoryInterface,
    private val getBestStationsUseCase: GetBestStationsUseCase,
    private val bestStationUseCase: BestStationUseCase = BestStationUseCase(),
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

    fun selectVehicle(vehicleId: Long) {
        _selectedVehicleId.value = vehicleId
    }

    fun selectFuelType(fuelType: String) {
        _selectedFuelType.value = fuelType
        updateBestStation()
    }

    fun updateUserLocation(lat: Double?, lon: Double?) {
        this.userLat = lat
        this.userLon = lon
        updateBestStation()
    }

    fun loadStations(scope: CoroutineScope) {
        scope.launch {
            try {
                _stations.value = gasStationRepository.getAllStations()
                updateBestStation()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load stations: %s", e.message)
            }
        }
    }

    fun setStations(stations: List<GasStation>) {
        val newSig = stations.stationListSignature()
        if (newSig == lastStationsSignature && _stations.value.isNotEmpty()) {
            return
        }
        lastStationsSignature = newSig
        _stations.value = stations
        updateBestStation()
    }

    fun updateBestStation() {
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

        val bestStationModel = result.best?.station
        if (_bestStation.value != bestStationModel) {
            _bestStation.value = bestStationModel
            if (bestStationModel != null) {
                val elapsed = System.currentTimeMillis() - com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker.t0Ms
                Timber.tag("StartupTimeline").i("T+%dms recommendation_shown (%s)", elapsed, bestStationModel.name)
            }
        }

        _bestStationUiState.value = BestStationUiState(
            isLoading = false,
            recommendation = result.best,
            alternatives = result.alternatives.take(2),
            error = null
        )
    }
}
