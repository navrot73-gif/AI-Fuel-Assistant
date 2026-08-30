package com.navrot.aifuelassistant.ui.map.delegate

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.ui.common.ErrorContext
import com.navrot.aifuelassistant.ui.common.ErrorMessageMapper
import com.navrot.aifuelassistant.ui.map.MapViewModel.AiRecommendation
import com.navrot.aifuelassistant.ui.map.MapViewModel.SortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class MapFilterDelegate @Inject constructor(
    private val repository: GasStationRepositoryInterface,
    private val getBestStationsUseCase: GetBestStationsUseCase
) {

    private val _stations = MutableStateFlow<List<GasStation>>(emptyList())
    val stations: StateFlow<List<GasStation>> = _stations.asStateFlow()

    private val _selectedFuelTypes = MutableStateFlow<Set<String>>(setOf("АИ-95"))
    val selectedFuelTypes: StateFlow<Set<String>> = _selectedFuelTypes.asStateFlow()

    private val _selectedBrands = MutableStateFlow<Set<String>>(emptySet())
    val selectedBrands: StateFlow<Set<String>> = _selectedBrands.asStateFlow()

    private val _openOnly = MutableStateFlow(false)
    val openOnly: StateFlow<Boolean> = _openOnly.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.BEST)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _cheapestStation = MutableStateFlow<GasStation?>(null)
    val cheapestStation: StateFlow<GasStation?> = _cheapestStation.asStateFlow()

    private val _bestStation = MutableStateFlow<GasStation?>(null)
    val bestStation: StateFlow<GasStation?> = _bestStation.asStateFlow()

    private val _aiRecommendation = MutableStateFlow<AiRecommendation?>(null)
    val aiRecommendation: StateFlow<AiRecommendation?> = _aiRecommendation.asStateFlow()

    fun updateStations(newList: List<GasStation>) {
        _stations.value = newList
    }

    fun toggleFuelType(fuelType: String, userLocation: Pair<Double, Double>?, scope: CoroutineScope) {
        val current = _selectedFuelTypes.value.toMutableSet()
        if (current.contains(fuelType)) {
            if (current.size > 1) current.remove(fuelType)
        } else {
            current.add(fuelType)
        }
        _selectedFuelTypes.value = current
        scope.launch {
            userLocation?.let { (lat, lon) ->
                updateBestAndCheapest(scope, lat, lon, 50.0)
                updateAiRecommendation(lat, lon)
            } ?: updateAiRecommendation()
        }
    }

    fun setFuelType(fuelType: String, userLocation: Pair<Double, Double>?, scope: CoroutineScope) {
        _selectedFuelTypes.value = setOf(fuelType)
        scope.launch {
            userLocation?.let { (lat, lon) ->
                updateBestAndCheapest(scope, lat, lon, 50.0)
                updateAiRecommendation(lat, lon)
            } ?: updateAiRecommendation()
        }
    }

    fun toggleOpenOnly() {
        _openOnly.value = !_openOnly.value
    }

    fun toggleBrand(brand: String) {
        val current = _selectedBrands.value.toMutableSet()
        if (current.contains(brand)) current.remove(brand) else current.add(brand)
        _selectedBrands.value = current
    }

    fun availableBrands(): List<String> =
        _stations.value.map { it.brand }.distinct().sorted()

    fun filterStationsByBrands(list: List<GasStation>): List<GasStation> {
        val brands = _selectedBrands.value
        return if (brands.isEmpty()) list else list.filter { it.brand in brands }
    }

    fun loadNearbyStations(
        scope: CoroutineScope,
        lat: Double,
        lon: Double,
        radiusKm: Double = 50.0,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit,
        onSuccess: () -> Unit
    ) {
        scope.launch {
            onLoading(true)
            onError("")
            var isFirstEmit = true
            try {
                repository.getNearbyStationsFlow(lat, lon, radiusKm).collect { nearby ->
                    val stationsToUse = if (nearby.isNotEmpty()) {
                        nearby
                    } else {
                        repository.getAllStations()
                    }
                    _stations.value = stationsToUse
                    updateBestAndCheapest(scope, lat, lon, radiusKm)
                    updateAiRecommendation(lat, lon)
                    onSuccess()

                    if (isFirstEmit) {
                        isFirstEmit = false
                        onLoading(false)
                    }
                }
            } catch (e: Exception) {
                try {
                    val fallback = repository.getAllStations()
                    if (fallback.isNotEmpty()) {
                        _stations.value = fallback
                        updateBestAndCheapest(scope, lat, lon, radiusKm)
                        updateAiRecommendation(lat, lon)
                        onSuccess()
                    } else {
                        onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.PRICES))
                    }
                } catch (fallbackEx: Exception) {
                    onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.PRICES))
                }
            } finally {
                onLoading(false)
            }
        }
    }

    fun loadStationsByCity(
        scope: CoroutineScope,
        city: String,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            onLoading(true)
            onError("")
            try {
                val cityStations = repository.getStationsByCity(city)
                _stations.value = if (cityStations.isNotEmpty()) cityStations else repository.getAllStations()
            } catch (e: Exception) {
                try {
                    val fallback = repository.getAllStations()
                    if (fallback.isNotEmpty()) {
                        _stations.value = fallback
                    } else {
                        onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.PRICES))
                    }
                } catch (fallbackEx: Exception) {
                    onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.PRICES))
                }
            } finally {
                onLoading(false)
            }
        }
    }

    fun reportPrice(
        scope: CoroutineScope,
        stationId: Int,
        fuelType: String,
        price: Double,
        userLocation: Pair<Double, Double>?,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val updated = repository.reportUserPrice(stationId, fuelType, price)
                _stations.value = updated
                userLocation?.let { (lat, lon) ->
                    updateBestAndCheapest(scope, lat, lon, 50.0)
                    updateAiRecommendation(lat, lon)
                }
            } catch (e: Exception) {
                onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.PRICES))
            }
        }
    }

    fun setSortMode(
        scope: CoroutineScope,
        mode: SortMode,
        lat: Double? = null,
        lon: Double? = null,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        _sortMode.value = mode
        scope.launch {
            onLoading(true)
            try {
                _stations.value = when (mode) {
                    SortMode.BEST -> repository.getBestStations(
                        _selectedFuelTypes.value.first(), lat, lon, 50.0
                    )
                    SortMode.PRICE_ASC -> repository.getStationsSortedByPriceAsc(
                        _selectedFuelTypes.value.first(), lat, lon, 50.0
                    )
                    SortMode.PRICE_DESC -> repository.getStationsSortedByPriceDesc(
                        _selectedFuelTypes.value.first(), lat, lon, 50.0
                    )
                    SortMode.NEARBY -> if (lat != null && lon != null) {
                        repository.getNearbyStations(lat, lon, 50.0)
                    } else _stations.value
                    SortMode.QUEUE -> repository.getStationsByQueue(
                        _selectedFuelTypes.value.first(), lat, lon, 50.0
                    )
                }
                updateAiRecommendation(lat, lon)
            } catch (e: Exception) {
                onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.PRICES))
            } finally {
                onLoading(false)
            }
        }
    }

    fun findCheapest(
        scope: CoroutineScope,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = 50.0,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            onLoading(true)
            try {
                _cheapestStation.value = repository.getCheapestStation(
                    _selectedFuelTypes.value.first(), lat, lon, radiusKm
                )
            } catch (e: Exception) {
                onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.PRICES))
            } finally {
                onLoading(false)
            }
        }
    }

    fun updateBestAndCheapest(scope: CoroutineScope, lat: Double, lon: Double, radiusKm: Double) {
        scope.launch {
            _bestStation.value = repository.getBestStations(
                _selectedFuelTypes.value.first(), lat, lon, radiusKm
            ).firstOrNull()
            _cheapestStation.value = repository.getCheapestStation(
                _selectedFuelTypes.value.first(), lat, lon, radiusKm
            )
        }
    }

    fun updateAiRecommendation(lat: Double? = null, lon: Double? = null) {
        val stationList = _stations.value
        val fuelFilter = _selectedFuelTypes.value

        val best = stationList
            .mapNotNull { st ->
                val fuel = st.fuelTypes.firstOrNull {
                    fuelFilter.isEmpty() || fuelFilter.contains(it.type)
                } ?: return@mapNotNull null
                val dist = if (lat != null && lon != null) {
                    GeoUtils.calculateDistance(lat, lon, st.latitude, st.longitude)
                } else Double.MAX_VALUE
                Triple(st, fuel, dist)
            }
            .minByOrNull { (st, fuel, dist) ->
                getBestStationsUseCase.calculateScore(st, fuel.type, dist)
            }

        _aiRecommendation.value = best?.let { (st, fuel, dist) ->
            AiRecommendation(station = st, fuel = fuel, distanceKm = dist)
        }
    }
}
