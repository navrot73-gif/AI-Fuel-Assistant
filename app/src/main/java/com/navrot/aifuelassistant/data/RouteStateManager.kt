package com.navrot.aifuelassistant.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteStateManager @Inject constructor() {
    private val _userCancelledRoute = MutableStateFlow(false)
    val userCancelledRoute: StateFlow<Boolean> = _userCancelledRoute.asStateFlow()

    private val _pendingRouteStationId = MutableStateFlow<Int?>(null)
    val pendingRouteStationId: StateFlow<Int?> = _pendingRouteStationId.asStateFlow()

    private val _autoBuildConsumed = MutableStateFlow(false)
    val autoBuildConsumed: StateFlow<Boolean> = _autoBuildConsumed.asStateFlow()

    fun setUserCancelledRoute(cancelled: Boolean) {
        _userCancelledRoute.value = cancelled
    }

    fun setPendingRouteStationId(id: Int?) {
        _pendingRouteStationId.value = id
    }

    fun consumePendingRouteStationId() {
        _pendingRouteStationId.value = null
    }

    fun setAutoBuildConsumed(consumed: Boolean) {
        _autoBuildConsumed.value = consumed
    }

    fun resetForNewIntent() {
        _userCancelledRoute.value = false
        _autoBuildConsumed.value = false
    }
}
