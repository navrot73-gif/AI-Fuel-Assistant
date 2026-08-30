package com.navrot.aifuelassistant.features.dashboard.delegate

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
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
        _stations.value = stations
        updateBestStation()
    }

    fun updateBestStation() {
        val fuelType = _selectedFuelType.value
        val best = _stations.value
            .filter { s -> s.fuelTypes.any { it.type == fuelType } }
            .minByOrNull { s ->
                getBestStationsUseCase.calculateScore(s, fuelType)
            }
        _bestStation.value = best
    }
}
