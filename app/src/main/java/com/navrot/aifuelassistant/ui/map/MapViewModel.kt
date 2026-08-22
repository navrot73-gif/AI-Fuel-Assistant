package com.navrot.aifuelassistant.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.BuildConfig
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.geo.GeocodingProvider
import com.navrot.aifuelassistant.geo.GeoException
import com.navrot.aifuelassistant.network.FuelApi
import com.navrot.aifuelassistant.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: GasStationRepositoryInterface,
    private val fuelApi: FuelApi,
    private val getBestStationsUseCase: GetBestStationsUseCase,
    private val benzonavtProvider: BenzonavtProvider,
    private val tileWarmupService: TileWarmupService,
    private val geocodingProvider: GeocodingProvider,
    private val routeStateManager: RouteStateManager
) : ViewModel() {

    companion object {
        private const val TAG = "MapViewModel"
        private const val DISTANCE_WEIGHT = 1.2
        private const val DEFAULT_CITY_SLUG = "chelyabinsk"
    }

    // Race-guard для быстрого ввода: инкрементируется при каждом новом запросе
    private val searchRequestId = AtomicInteger(0)

    // Гэокодированная точка для фокуса карты (синяя метка адреса)
    private val _geocodedLocation = MutableStateFlow<GeoPoint?>(null)
    val geocodedLocation: StateFlow<GeoPoint?> = _geocodedLocation.asStateFlow()

    /** Сбрасывает гэокодированную точку (при очистке поиска). */
    fun clearGeocodedLocation() {
        _geocodedLocation.value = null
    }

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

    /** Лучшая станция из getBestStations (уже ранжированная). */
    val bestStationRanked: StateFlow<GasStation?> = _stations
        .map { stations ->
            stations.firstOrNull()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, null)

    /** Средняя цена по текущему выбранному типу топлива среди видимых станций. */
    val avgPrice: StateFlow<Double> = _stations
        .map { stations ->
            val fuelType = _selectedFuelTypes.value.firstOrNull() ?: "АИ-95"
            val prices = stations
                .flatMap { it.fuelTypes }
                .filter { it.type == fuelType && it.available }
                .map { it.price }
            if (prices.isEmpty()) 0.0 else prices.average()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, 0.0)

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _currentCity = MutableStateFlow("Рядом с вами")
    val currentCity: StateFlow<String> = _currentCity.asStateFlow()

    init {
        // Warmup: pre-fetch prices for default city (chelyabinsk) so cache is hot
        // when user location is resolved. Fire-and-forget, errors ignored.
        viewModelScope.launch {
            try { benzonavtProvider.fetchCityPrices() } catch (_: Exception) { /* ignore */ }
        }

        // Warmup: pre-fetch map tiles for Chelyabinsk (fire-and-forget, low priority)
        tileWarmupService.startPrefetch()
    }

    /** AI-рекомендация лучшей станции из текущего списка. */
    data class AiRecommendation(
        val station: GasStation,
        val fuel: FuelPrice,
        val distanceKm: Double
    )

    val userCancelledRoute: StateFlow<Boolean> = routeStateManager.userCancelledRoute
    val autoBuildConsumed: StateFlow<Boolean> = routeStateManager.autoBuildConsumed

    fun resetUserCancelledRoute() {
        routeStateManager.setUserCancelledRoute(false)
    }

    fun consumePendingRouteStationId() {
        routeStateManager.consumePendingRouteStationId()
    }

    fun markAutoBuildConsumed() {
        routeStateManager.setAutoBuildConsumed(true)
    }

    private val _aiRecommendation = MutableStateFlow<AiRecommendation?>(null)
    val aiRecommendation: StateFlow<AiRecommendation?> = _aiRecommendation.asStateFlow()

    data class RouteOptionUiState(
        val title: String = "Быстрый",
        val points: List<GeoPoint>,
        val distanceText: String,
        val durationText: String,
        val destination: String,
        val isStraightLine: Boolean = false,
        val isDirect: Boolean = isStraightLine,
        val distanceMeters: Double = 0.0,
        val durationSeconds: Double = 0.0
    )

    private val _routeOptions = MutableStateFlow<List<RouteOptionUiState>>(emptyList())
    val routeOptions: StateFlow<List<RouteOptionUiState>> = _routeOptions.asStateFlow()

    private val _activeRouteIndex = MutableStateFlow(0)
    val activeRouteIndex: StateFlow<Int> = _activeRouteIndex.asStateFlow()

    val activeRoute: StateFlow<RouteOptionUiState?> = combine(_routeOptions, _activeRouteIndex) { options, index ->
        options.getOrNull(index)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val route: StateFlow<RouteOptionUiState?> = activeRoute

    private val _isRouteOptionsPanelVisible = MutableStateFlow(false)
    val isRouteOptionsPanelVisible: StateFlow<Boolean> = _isRouteOptionsPanelVisible.asStateFlow()

    private val _isRouting = MutableStateFlow(false)
    val isRouting: StateFlow<Boolean> = _isRouting.asStateFlow()

    enum class SortMode {
        BEST, PRICE_ASC, PRICE_DESC, NEARBY, QUEUE
    }

    fun updateUserLocation(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
        updateAiRecommendation(lat, lon)
    }

    /**
     * Определяет город (reverse geocoding, Nominatim; fallback — хардкод),
     * сохраняет slug для BenzonavtProvider и пересчитывает цены на станциях.
     */
    fun updateCityAndPrices(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val cityName = GeoUtils.detectCity(lat, lon, geocodingProvider)
                _currentCity.value = cityName
                val slug = GeoUtils.toCitySlug(cityName)
                benzonavtProvider.setCity(slug)
                repository.refreshPrices()
            } catch (_: Exception) {
                benzonavtProvider.setCity(DEFAULT_CITY_SLUG)
            }
        }
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

    /**
     * Поиск АЗС с fallback на Nominatim-геокодинг.
     *
     * 1. Сначала ищем в локальном списке (repository.searchStations)
     * 2. Если локальных совпадений нет — геокодим запрос через Nominatim (debounce 400мс)
     * 3. Race-guard: только последний запрос срабатывает (AtomicInteger)
     */
    fun searchStations(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            clearGeocodedLocation()
            return
        }

        val currentRequestId = searchRequestId.incrementAndGet()
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 1. Локальный поиск
                val localResults = repository.searchStations(trimmed)

                if (localResults.isNotEmpty()) {
                    // Есть локальные совпадения — используем их, сбрасываем фокус
                    if (currentRequestId == searchRequestId.get()) {
                        _stations.value = localResults
                        _geocodedLocation.value = null
                        updateAiRecommendation()
                    }
                    return@launch
                }

                // 2. Нет локальных совпадений → геокодинг с debounce 400мс
                kotlinx.coroutines.delay(400)
                if (currentRequestId != searchRequestId.get()) return@launch // устарел

                val result = geocodingProvider.geocode(trimmed)
                if (currentRequestId != searchRequestId.get()) return@launch // устарел

                // Сохраняем геокодированную точку для фокуса карты (синяя метка)
                _geocodedLocation.value = result.point

                // Ищем станции рядом с геокодированной точкой
                val nearby = repository.getNearbyStations(
                    result.point.latitude, result.point.longitude, 10.0
                )
                if (currentRequestId == searchRequestId.get()) {
                    _stations.value = nearby
                    updateAiRecommendation(result.point.latitude, result.point.longitude)
                }

            } catch (e: GeoException.NoResults) {
                // Ничего не найдено — пустой список без ошибки
                if (currentRequestId == searchRequestId.get()) {
                    _stations.value = emptyList()
                    _geocodedLocation.value = null
                }
            } catch (e: GeoException.NetworkError) {
                // Ошибка сети — логируем, оставляем последнюю локацию
                Log.w(TAG, "Geocoding network error: ${e.message}")
                if (currentRequestId == searchRequestId.get()) {
                    _stations.value = emptyList()
                    _error.value = "Нет сети для геокодинга"
                }
            } catch (e: Exception) {
                if (currentRequestId == searchRequestId.get()) {
                    _error.value = "Ошибка поиска: ${e.message}"
                }
            } finally {
                if (currentRequestId == searchRequestId.get()) {
                    _isLoading.value = false
                }
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

    fun buildRouteToStation(station: GasStation) = buildRouteTo(station)

    fun buildRouteTo(station: GasStation) {
        routeStateManager.consumePendingRouteStationId()
        val start = _userLocation.value
        if (start == null) {
            _error.value = "Определение местоположения... Подождите и попробуйте снова"
            return
        }

        val distKm = GeoUtils.calculateDistance(
            start.first, start.second, station.latitude, station.longitude
        )
        val fallbackState = RouteOptionUiState(
            title = "Быстрый",
            points = listOf(
                GeoPoint(start.first, start.second),
                GeoPoint(station.latitude, station.longitude)
            ),
            distanceText = "${Format.km(distKm)} км",
            durationText = "по прямой",
            destination = station.brand,
            isStraightLine = true,
            isDirect = true,
            distanceMeters = distKm * 1000.0,
            durationSeconds = 0.0
        )

        viewModelScope.launch {
            _isRouting.value = true
            try {
                // GET worker route with alternatives=true: from={lon},{lat}&to={lon},{lat}
                var response = fuelApi.getRoute(
                    fromLon = start.second,
                    fromLat = start.first,
                    toLon = station.longitude,
                    toLat = station.latitude,
                    alternatives = true
                )
                var rawOptions = response.getRouteOptions()

                if (distKm > 5.0 && rawOptions.size == 1) {
                    try {
                        val retryResponse = fuelApi.getRoute(
                            fromLon = start.second,
                            fromLat = start.first,
                            toLon = station.longitude,
                            toLat = station.latitude,
                            alternatives = true
                        )
                        val retryOptions = retryResponse.getRouteOptions()
                        if (retryOptions.size > 1) {
                            response = retryResponse
                            rawOptions = retryOptions
                        }
                    } catch (retryEx: Exception) {
                        Log.w(TAG, "Retry routing failed: ${retryEx.message}")
                    }
                }

                if (rawOptions.isEmpty()) {
                    throw Exception("Worker returned no route options")
                }

                val titles = listOf("Быстрый", "Без пробок", "Альтернативный")
                val parsedOptions = rawOptions.mapIndexed { index, optionData ->
                    val routePoints = optionData.points.map { pt ->
                        GeoPoint(latitude = pt[0], longitude = pt[1])
                    }
                    val min = Math.round(optionData.durationSeconds / 60.0).toInt()
                    val durText = if (min < 60) "$min мин" else "${min / 60} ч ${min % 60} мин"
                    val title = titles.getOrElse(index) { "Вариант ${index + 1}" }

                    RouteOptionUiState(
                        title = title,
                        points = routePoints,
                        distanceText = "${Format.km(optionData.distanceMeters / 1000.0)} км",
                        durationText = durText,
                        destination = station.brand,
                        isStraightLine = false,
                        isDirect = false,
                        distanceMeters = optionData.distanceMeters,
                        durationSeconds = optionData.durationSeconds
                    )
                }

                _routeOptions.value = parsedOptions
                _activeRouteIndex.value = 0
                _isRouteOptionsPanelVisible.value = parsedOptions.size >= 2
            } catch (e: Exception) {
                Log.w(TAG, "Worker routing failed: ${e.message}, fallback to straight line")
                _routeOptions.value = listOf(fallbackState)
                _activeRouteIndex.value = 0
                _isRouteOptionsPanelVisible.value = false
            } finally {
                _isRouting.value = false
            }
        }
    }

    fun selectRouteOption(index: Int) {
        if (index in _routeOptions.value.indices) {
            _activeRouteIndex.value = index
        }
    }

    fun dismissRouteOptionsPanel() {
        _isRouteOptionsPanelVisible.value = false
    }

    fun clearRoute() {
        routeStateManager.setUserCancelledRoute(true)
        routeStateManager.consumePendingRouteStationId()
        _routeOptions.value = emptyList()
        _activeRouteIndex.value = 0
        _isRouteOptionsPanelVisible.value = false
        _isRouting.value = false
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
     * критериям (цена + очередь * 0.5 + штраф за ненадёжность + расстояние * 1.2).
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
                        + (100 - st.reliability) * GetBestStationsUseCase.RELIABILITY_WEIGHT
                        + (if (dist == Double.MAX_VALUE) 0.0 else dist * DISTANCE_WEIGHT)
            }

        _aiRecommendation.value = best?.let { (st, fuel, dist) ->
            AiRecommendation(station = st, fuel = fuel, distanceKm = dist)
        }
    }

    fun clearError() { _error.value = null }
}
