package com.navrot.aifuelassistant.ui.map.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.ui.map.MapViewModel
import com.navrot.aifuelassistant.ui.map.UserLocationState

/**
 * Contract for rendering interactive map items, routes, location indicators, and overlays
 * independent of specific map engine implementation.
 */
interface MapRenderer {
    @Composable
    fun Render(
        modifier: Modifier,
        stationItems: List<StationMapItem>,
        userLocation: UserLocationState?,
        route: MapViewModel.RouteOptionUiState?,
        isDarkMode: Boolean,
        recenterRequest: Int,
        zoomInRequest: Int,
        zoomOutRequest: Int,
        focusPoint: GeoPoint?,
        onStationClick: (Int) -> Unit
    )
}
