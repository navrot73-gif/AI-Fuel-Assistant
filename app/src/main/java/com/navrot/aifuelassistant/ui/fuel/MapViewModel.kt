package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.BuildConfig
import com.navrot.aifuelassistant.data.GasStationRepository
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.geo.OpenRouteServiceProvider
import com.navrot.aifuelassistant.geo.RoutingProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapViewModel : ViewModel() {

    private val repository = GasStationRepository

    // Роутинг по дорогам: работает, если в local.properties задан ORS_API_KEY
    private val routingProvider: RoutingProvider? =
        BuildConfig.ORS_API_KEY
            .takeIf { it.isNotBlank() }
            ?.let {
                OpenRouteServiceProvider(
                    apiKey = it,
                    httpClient = OkHttpClient.Builder()
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(8, TimeUnit.SECONDS)
                        .build()
                )
            }

    private val _stations = MutableStateFlow<List<GasStation>>(emptyList())
    val stations: StateFlow<List<GasStation>> = _stations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedFuelTypes = MutableStateFlow<Set<String>>(setOf("АИ-95"))
    val selectedFuelTypes: StateFlow<Set<String>> = _selectedFuelTypes.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.BEST)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _cheapestStation = MutableStateFlow<GasStation?>(null)
    val cheapestStation: StateFlow<GasStation?> = _cheapestStation.asStateFlow()

    private val _bestStation = MutableStateFlow<GasStation?>(null)
    val bestStation: StateFlow<GasStation?> = _bestStation.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

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

    fun loadNearbyStations(lat: Double, lon: Double, radiusKm: Double = 50.0) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _stations.value = repository.getNearbyStations(lat, lon, radiusKm)
                updateBestAndCheapest(lat, lon, radiusKm)
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
            _userLocation.value?.let { (lat, lon) -> updateBestAndCheapest(lat, lon, 50.0) }
        }
    }

    fun setFuelType(fuelType: String) {
        _selectedFuelTypes.value = setOf(fuelType)
        viewModelScope.launch {
            _userLocation.value?.let { (lat, lon) -> updateBestAndCheapest(lat, lon, 50.0) }
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

    // ==================== МАРШРУТЫ ====================

    /**
     * Маршрут строится СРАЗУ (прямая линия — мгновенная линия на карте),
     * затем в фоне уточняется по дорогам через OpenRouteService, если есть ключ.
     */
    fun buildRouteTo(station: GasStation) {
        val start = _userLocation.value
        if (start == null) {
            _error.value = "Сначала дождитесь определения местоположения 📍"
            return
        }

        // 1) Мгновенно: прямая линия
        val distKm = haversineKm(start.first, start.second, station.latitude, station.longitude)
        _route.value = RouteUiState(
            points = listOf(
                GeoPoint(start.first, start.second),
                GeoPoint(station.latitude, station.longitude)
            ),
            distanceText = String.format("≈ %.1f км", distKm),
            durationText = "по прямой",
            destination = station.brand,
            isStraightLine = true
        )

        // 2) В фоне: уточняем по дорогам, если доступен ORS
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
                // ORS недоступен — остаётся прямая линия, ошибки не показываем
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

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun updateBestAndCheapest(lat: Double, lon: Double, radiusKm: Double) {
        _bestStation.value = repository.getBestStations(
            _selectedFuelTypes.value.first(), lat, lon, radiusKm
        ).firstOrNull()
        _cheapestStation.value = repository.getCheapestStation(
            _selectedFuelTypes.value.first(), lat, lon, radiusKm
        )
    }

    fun clearError() { _error.value = null }
}