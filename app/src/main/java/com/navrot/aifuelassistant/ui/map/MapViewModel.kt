package com.navrot.aifuelassistant.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeocodingProvider
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.geo.RoutingProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Nullable

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: GasStationRepositoryInterface,
    @Nullable private val routingProvider: RoutingProvider?,
    private val geocodingProvider: GeocodingProvider,
    private val getBestStationsUseCase: GetBestStationsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "MapViewModel"
    }

    /** Данные для AI-рекомендации (лучшая станция + расстояние). */
    data class AiRecommendation(
        val station: GasStation,
        val fuel: FuelPrice,
        val distanceKm: Double
    )

    private val _stations = MutableStateFlow<List<GasStation>>(emptyList())
    val stations: StateFlow<List<GasStation>> = _stations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    /** AI-рекомендация лучшей станции из текущего списка. */
    private val _aiRecommendation = MutableStateFlow<AiRecommendation?>(null)
    val aiRecommendation: StateFlow<AiRecommendation?> = _aiRecommendation.asStateFlow()

    data class RouteUiState(
        val points: List<GeoPoint>,
        val distanceText: String,
        val durationText: String,
        val destination: String,
        val isStraightLine: Boolean = false
    )

    private val _route = MutableStateFlow<RouteUiState?>(null)
    val route: StateFlow<RouteUiState?> = _route.asStateFlow()

    private val _isRouting = MutableStateFlow(false)
    val isRouting: StateFlow<Boolean> = _isRouting.asStateFlow()

    enum class SortMode {
        BEST, PRICE_ASC, PRICE_DESC, NEARBY, QUEUE
    }

    fun updateUserLocation(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
    }

    /** Определить город через Nominatim (с fallback на хардкод). */
    suspend fun detectCity(lat: Double, lon: Double): String {
        return GeoUtils.detectCity(lat, lon, geocodingProvider)
    }

    fun loadNearbyStations(lat: Double, lon: Double, radiusKm: Double = 50.0) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _stations.value = repository.getNearbyStations(lat, lon, radiusKm)
                updateBestAndCheapest(lat, lon, radiusKm)
                updateAiRecommendation(lat, lon)
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadStationsByCity(city: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _stations.value = repository.getStationsByCity(city)
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchStations(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _stations.value = repository.searchStations(query)
                updateAiRecommendation()
            } catch (e: Exception) {
                _error.value = "Ошибка поиска: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFuelType(fuelType: String) {
        val current = _selectedFuelTypes.value.toMutableSet()
        if (current.contains(fuelType)) {
            if (current.size > 1) current.remove(fuelType)
        } else {
            current.add(fuelType)
        }
        _selectedFuelTypes.value = current
        viewModelScope.launch {
            _userLocation.value?.let { (lat, lon) ->
                updateBestAndCheapest(lat, lon, 50.0)
                updateAiRecommendation(lat, lon)
            } ?: updateAiRecommendation()
        }
    }

    fun setFuelType(fuelType: String) {
        _selectedFuelTypes.value = setOf(fuelType)
        viewModelScope.launch {
            _userLocation.value?.let { (lat, lon) ->
                updateBestAndCheapest(lat, lon, 50.0)
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

    /** Все уникальные бренды текущего списка АЗС (для чипов-фильтров). */
    fun availableBrands(): List<String> =
        _stations.value.map { it.brand }.distinct().sorted()

    /** Фильтр списка АЗС по выбранным брендам. */
    fun filterStationsByBrands(list: List<GasStation>): List<GasStation> {
        val brands = _selectedBrands.value
        return if (brands.isEmpty()) list else list.filter { it.brand in brands }
    }

    /**
     * Сообщить пользовательскую цену. Цена сохраняется в SharedPreferences
     * и немедленно применяется к списку станций (переживёт рестарт приложения).
     */
    fun reportPrice(stationId: Int, fuelType: String, price: Double) {
        viewModelScope.launch {
            try {
                val updated = repository.reportUserPrice(stationId, fuelType, price)
                _stations.value = updated
                _userLocation.value?.let { (lat, lon) ->
                    updateBestAndCheapest(lat, lon, 50.0)
                    updateAiRecommendation(lat, lon)
                }
            } catch (e: Exception) {
                _error.value = "Не удалось сохранить цену: ${e.message}"
            }
        }
    }

    fun setSortMode(mode: SortMode, lat: Double? = null, lon: Double? = null) {
        _sortMode.value = mode
        viewModelScope.launch {
            _isLoading.value = true
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
                _error.value = "Ошибка сортировки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun findCheapest(lat: Double? = null, lon: Double? = null, radiusKm: Double = 50.0) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _cheapestStation.value = repository.getCheapestStation(
                    _selectedFuelTypes.value.first(), lat, lon, radiusKm
                )
            } catch (e: Exception) {
                _error.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun buildRouteTo(station: GasStation) {
        val start = _userLocation.value
        if (start == null) {
            _error.value = "Сначала дождитесь определения местоположения"
            return
        }

        val distKm = GeoUtils.calculateDistance(
            start.first, start.second, station.latitude, station.longitude
        )
        _route.value = RouteUiState(
            points = listOf(
                GeoPoint(start.first, start.second),
                GeoPoint(station.latitude, station.longitude)
            ),
            distanceText = String.format("~ %.1f км", distKm),
            durationText = "по прямой",
            destination = station.brand,
            isStraightLine = true
        )

        val provider = routingProvider ?: return
        viewModelScope.launch {
            _isRouting.value = true
            try {
                val result = provider.route(
                    from = GeoPoint(start.first, start.second),
                    to = GeoPoint(station.latitude, station.longitude)
                )
                _route.value = RouteUiState(
                    points = result.points,
                    distanceText = String.format("%.1f км", result.distanceMeters / 1000.0),
                    durationText = formatDuration(result.durationSeconds),
                    destination = station.brand
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build route via ORS", e)
            } finally {
                _isRouting.value = false
            }
        }
    }

    fun clearRoute() {
        _route.value = null
    }

    private fun formatDuration(seconds: Double): String {
        val min = Math.round(seconds / 60.0).toInt()
        return if (min < 60) "$min мин" else "${min / 60} ч ${min % 60} мин"
    }

    private fun updateBestAndCheapest(lat: Double, lon: Double, radiusKm: Double) {
        viewModelScope.launch {
            _bestStation.value = repository.getBestStations(
                _selectedFuelTypes.value.first(), lat, lon, radiusKm
            ).firstOrNull()
            _cheapestStation.value = repository.getCheapestStation(
                _selectedFuelTypes.value.first(), lat, lon, radiusKm
            )
        }
    }

    /**
     * Обновляет AI-рекомендацию: находит лучшую станцию по текущим
     * критериям (цена + очередь * 0.5 - надёжность + расстояние * 1.2).
     *
     * Веса совпадают с [GetBestStationsUseCase], плюс добавляется
     * расстояние с весом 1.2 руб/км для визуальной рекомендации.
     */
    private fun updateAiRecommendation(lat: Double? = null, lon: Double? = null) {
        val stationList = _stations.value
        val fuelFilter = _selectedFuelTypes.value

        val best = stationList
            .mapNotNull { st ->
                val fuel = st.fuelTypes.firstOrNull {
                    (fuelFilter.isEmpty() || fuelFilter.contains(it.type)) && it.available
                } ?: return@mapNotNull null
                val dist = if (lat != null && lon != null) {
                    GeoUtils.calculateDistance(lat, lon, st.latitude, st.longitude)
                } else Double.MAX_VALUE
                Triple(st, fuel, dist)
            }
            .minByOrNull { (st, fuel, dist) ->
                fuel.price
                        + st.queueTime * GetBestStationsUseCase.QUEUE_WEIGHT
                        - (100 - st.reliability) * GetBestStationsUseCase.RELIABILITY_WEIGHT
                        + (if (dist == Double.MAX_VALUE) 0.0 else dist * 1.2)
            }

        _aiRecommendation.value = best?.let { (st, fuel, dist) ->
            AiRecommendation(station = st, fuel = fuel, distanceKm = dist)
        }
    }

    fun clearError() { _error.value = null }
}
