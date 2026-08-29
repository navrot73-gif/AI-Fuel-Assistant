package com.navrot.aifuelassistant.ui.map

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.geo.GeocodingProvider
import com.navrot.aifuelassistant.network.FuelApi
import com.navrot.aifuelassistant.network.NetworkMonitor
import com.navrot.aifuelassistant.ui.map.delegate.MapFilterDelegate
import com.navrot.aifuelassistant.ui.map.delegate.MapRouteDelegate
import com.navrot.aifuelassistant.ui.map.delegate.MapSearchDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val searchDelegate: MapSearchDelegate,
    private val routeDelegate: MapRouteDelegate,
    private val filterDelegate: MapFilterDelegate,
    private val repository: GasStationRepositoryInterface,
    private val benzonavtProvider: BenzonavtProvider,
    private val tileWarmupService: TileWarmupService,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Вторичный конструктор для сохранения полной обратной совместимости в тестах
    constructor(
        repository: GasStationRepositoryInterface,
        fuelApi: FuelApi,
        getBestStationsUseCase: GetBestStationsUseCase,
        benzonavtProvider: BenzonavtProvider,
        tileWarmupService: TileWarmupService,
        geocodingProvider: GeocodingProvider,
        routeStateManager: RouteStateManager,
        networkMonitor: NetworkMonitor,
        context: Context
    ) : this(
        searchDelegate = MapSearchDelegate(geocodingProvider, benzonavtProvider, repository, tileWarmupService),
        routeDelegate = MapRouteDelegate(fuelApi, routeStateManager),
        filterDelegate = MapFilterDelegate(repository, getBestStationsUseCase),
        repository = repository,
        benzonavtProvider = benzonavtProvider,
        tileWarmupService = tileWarmupService,
        networkMonitor = networkMonitor,
        context = context
    )

    companion object {
        private const val TAG = "MapViewModel"
        private const val PREFS_NAME = "map_prefs"
        private const val KEY_IS_DARK_MODE = "is_dark_mode"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _lastCacheUpdateTime = MutableStateFlow<Long?>(repository.getLastCacheUpdateTime())
    val lastCacheUpdateTime: StateFlow<Long?> = _lastCacheUpdateTime.asStateFlow()

    fun refreshCacheUpdateTime() {
        _lastCacheUpdateTime.value = repository.getLastCacheUpdateTime()
    }

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_IS_DARK_MODE, false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        prefs.edit().putBoolean(KEY_IS_DARK_MODE, newMode).apply()
    }

    // Делегированные свойства
    val geocodedLocation: StateFlow<GeoPoint?> = searchDelegate.geocodedLocation
    val currentCity: StateFlow<String> = searchDelegate.currentCity

    val stations: StateFlow<List<GasStation>> = filterDelegate.stations
    val selectedFuelTypes: StateFlow<Set<String>> = filterDelegate.selectedFuelTypes
    val selectedBrands: StateFlow<Set<String>> = filterDelegate.selectedBrands
    val openOnly: StateFlow<Boolean> = filterDelegate.openOnly
    val sortMode: StateFlow<SortMode> = filterDelegate.sortMode
    val cheapestStation: StateFlow<GasStation?> = filterDelegate.cheapestStation
    val bestStation: StateFlow<GasStation?> = filterDelegate.bestStation
    val aiRecommendation: StateFlow<AiRecommendation?> = filterDelegate.aiRecommendation

    val userCancelledRoute: StateFlow<Boolean> = routeDelegate.userCancelledRoute
    val autoBuildConsumed: StateFlow<Boolean> = routeDelegate.autoBuildConsumed
    val route: StateFlow<RouteOptionUiState?> = routeDelegate.route
    val isRouting: StateFlow<Boolean> = routeDelegate.isRouting

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    /** Лучшая станция из getBestStations (уже ранжированная). */
    val bestStationRanked: StateFlow<GasStation?> = stations
        .map { stationList -> stationList.firstOrNull() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Средняя цена по текущему выбранному типу топлива среди видимых станций. */
    val avgPrice: StateFlow<Double> = stations
        .map { stationList ->
            val fuelType = selectedFuelTypes.value.firstOrNull() ?: "АИ-95"
            val prices = stationList
                .flatMap { it.fuelTypes }
                .filter { it.type == fuelType && it.available }
                .map { it.price }
            if (prices.isEmpty()) 0.0 else prices.average()
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    init {
        // Warmup: pre-fetch prices for default city (chelyabinsk) so cache is hot
        // when user location is resolved.
        viewModelScope.launch {
            try {
                benzonavtProvider.fetchCityPrices()
            } catch (e: java.io.IOException) {
                Timber.tag(TAG).w("Benzonavt fetch failed during warmup: %s", e.message)
            } catch (e: org.json.JSONException) {
                Timber.tag(TAG).e(e, "Benzonavt parse error during warmup: %s", e.message)
            } catch (e: Exception) {
                Timber.tag(TAG).w("Failed to pre-fetch city prices: %s", e.message)
            }
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

    enum class SortMode {
        BEST, PRICE_ASC, PRICE_DESC, NEARBY, QUEUE
    }

    fun clearGeocodedLocation() {
        searchDelegate.clearGeocodedLocation()
    }

    fun updateUserLocation(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
        filterDelegate.updateAiRecommendation(lat, lon)
    }

    fun updateCityAndPrices(lat: Double, lon: Double) {
        searchDelegate.updateCityAndPrices(viewModelScope, lat, lon)
    }

    fun setManualCity(cityName: String) {
        searchDelegate.setManualCity(viewModelScope, cityName)
        loadStationsByCity(cityName)
    }

    fun loadNearbyStations(lat: Double, lon: Double, radiusKm: Double = 50.0) {
        filterDelegate.loadNearbyStations(
            scope = viewModelScope,
            lat = lat,
            lon = lon,
            radiusKm = radiusKm,
            onLoading = { _isLoading.value = it },
            onError = { _error.value = if (it.isEmpty()) null else it },
            onSuccess = { refreshCacheUpdateTime() }
        )
    }

    fun loadStationsByCity(city: String) {
        filterDelegate.loadStationsByCity(
            scope = viewModelScope,
            city = city,
            onLoading = { _isLoading.value = it },
            onError = { _error.value = if (it.isEmpty()) null else it }
        )
    }

    fun searchStations(query: String) {
        searchDelegate.searchStations(
            scope = viewModelScope,
            query = query,
            onSearchResult = { searchedStations, centerLat, centerLon ->
                filterDelegate.updateStations(searchedStations)
                if (centerLat != null && centerLon != null) {
                    filterDelegate.updateAiRecommendation(centerLat, centerLon)
                } else {
                    filterDelegate.updateAiRecommendation()
                }
            },
            onError = { _error.value = if (it.isEmpty()) null else it },
            onLoading = { _isLoading.value = it }
        )
    }

    fun toggleFuelType(fuelType: String) {
        filterDelegate.toggleFuelType(fuelType, _userLocation.value, viewModelScope)
    }

    fun setFuelType(fuelType: String) {
        filterDelegate.setFuelType(fuelType, _userLocation.value, viewModelScope)
    }

    fun toggleOpenOnly() {
        filterDelegate.toggleOpenOnly()
    }

    fun toggleBrand(brand: String) {
        filterDelegate.toggleBrand(brand)
    }

    fun availableBrands(): List<String> = filterDelegate.availableBrands()

    fun filterStationsByBrands(list: List<GasStation>): List<GasStation> =
        filterDelegate.filterStationsByBrands(list)

    fun reportPrice(stationId: Int, fuelType: String, price: Double) {
        filterDelegate.reportPrice(
            scope = viewModelScope,
            stationId = stationId,
            fuelType = fuelType,
            price = price,
            userLocation = _userLocation.value,
            onError = { _error.value = if (it.isEmpty()) null else it }
        )
    }

    fun setSortMode(mode: SortMode, lat: Double? = null, lon: Double? = null) {
        filterDelegate.setSortMode(
            scope = viewModelScope,
            mode = mode,
            lat = lat,
            lon = lon,
            onLoading = { _isLoading.value = it },
            onError = { _error.value = if (it.isEmpty()) null else it }
        )
    }

    fun findCheapest(lat: Double? = null, lon: Double? = null, radiusKm: Double = 50.0) {
        filterDelegate.findCheapest(
            scope = viewModelScope,
            lat = lat,
            lon = lon,
            radiusKm = radiusKm,
            onLoading = { _isLoading.value = it },
            onError = { _error.value = if (it.isEmpty()) null else it }
        )
    }

    fun resetUserCancelledRoute() {
        routeDelegate.resetUserCancelledRoute()
    }

    fun consumePendingRouteStationId() {
        routeDelegate.consumePendingRouteStationId()
    }

    fun markAutoBuildConsumed() {
        routeDelegate.markAutoBuildConsumed()
    }

    fun buildRouteToStation(station: GasStation) = buildRouteTo(station)

    fun buildRouteTo(station: GasStation) {
        routeDelegate.buildRouteTo(
            scope = viewModelScope,
            station = station,
            userLocation = _userLocation.value,
            onError = { _error.value = if (it.isEmpty()) null else it }
        )
    }

    fun clearRoute() {
        routeDelegate.clearRoute()
    }

    fun clearError() {
        _error.value = null
    }
}
