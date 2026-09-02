package com.navrot.aifuelassistant.ui.map.delegate

import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.network.FuelApi
import com.navrot.aifuelassistant.ui.common.ErrorContext
import com.navrot.aifuelassistant.ui.common.ErrorMessageMapper
import com.navrot.aifuelassistant.ui.map.MapViewModel.RouteOptionUiState
import com.navrot.aifuelassistant.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class MapRouteDelegate @Inject constructor(
    private val fuelApi: FuelApi,
    private val routeStateManager: RouteStateManager
) {

    companion object {
        private const val TAG = "MapRouteDelegate"
    }

    val userCancelledRoute: StateFlow<Boolean> = routeStateManager.userCancelledRoute
    val autoBuildConsumed: StateFlow<Boolean> = routeStateManager.autoBuildConsumed

    private val _route = MutableStateFlow<RouteOptionUiState?>(null)
    val route: StateFlow<RouteOptionUiState?> = _route.asStateFlow()

    private val _isRouting = MutableStateFlow(false)
    val isRouting: StateFlow<Boolean> = _isRouting.asStateFlow()

    private val _showStraightLineBanner = MutableStateFlow(false)
    val showStraightLineBanner: StateFlow<Boolean> = _showStraightLineBanner.asStateFlow()

    fun resetUserCancelledRoute() {
        routeStateManager.setUserCancelledRoute(false)
    }

    fun consumePendingRouteStationId() {
        routeStateManager.consumePendingRouteStationId()
    }

    fun markAutoBuildConsumed() {
        routeStateManager.setAutoBuildConsumed(true)
    }

    fun buildRouteTo(
        scope: CoroutineScope,
        station: GasStation,
        userLocation: Pair<Double, Double>?,
        onError: (String) -> Unit
    ) {
        routeStateManager.consumePendingRouteStationId()
        if (userLocation == null) {
            onError("Определение местоположения... Подождите и попробуйте снова")
            return
        }

        val start = userLocation
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

        scope.launch {
            _isRouting.value = true
            try {
                val routeResult = fuelApi.getRoute(
                    fromLon = start.second,
                    fromLat = start.first,
                    toLon = station.longitude,
                    toLat = station.latitude,
                    alternatives = false
                )

                val response = routeResult.getOrNull()
                if (response == null || response.getRouteOptions().isEmpty()) {
                    Timber.tag(TAG).w("Routing failed, fallback to straight line without modal dialog")
                    _route.value = fallbackState
                    _showStraightLineBanner.value = true
                    scope.launch {
                        kotlinx.coroutines.delay(4000L)
                        _showStraightLineBanner.value = false
                    }
                } else {
                    val rawOptions = response.getRouteOptions()
                    val optionData = rawOptions.first()
                    val routePoints = optionData.points.map { pt ->
                        GeoPoint(latitude = pt[0], longitude = pt[1])
                    }
                    val min = Math.round(optionData.durationSeconds / 60.0).toInt()
                    val durText = if (min < 60) "$min мин" else "${min / 60} ч ${min % 60} мин"

                    _route.value = RouteOptionUiState(
                        title = "Быстрый",
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
            } catch (e: Exception) {
                val errMsg = e.message ?: "неизвестная ошибка"
                Timber.tag(TAG).w("Routing failed: %s, fallback to straight line without modal dialog", errMsg)
                _route.value = fallbackState
                _showStraightLineBanner.value = true
                scope.launch {
                    kotlinx.coroutines.delay(4000L)
                    _showStraightLineBanner.value = false
                }
            } finally {
                _isRouting.value = false
            }
        }
    }

    fun clearRoute() {
        routeStateManager.setUserCancelledRoute(true)
        routeStateManager.consumePendingRouteStationId()
        _route.value = null
        _isRouting.value = false
    }
}
