package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.ui.map.renderer.MapRenderer
import com.navrot.aifuelassistant.ui.map.renderer.OsmMapRenderer
import com.navrot.aifuelassistant.ui.map.renderer.StationMapMapper
import org.osmdroid.util.GeoPoint as OsmGeoPoint

@Composable
fun OsmMapView(
    userLocation: UserLocationState?,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    route: MapViewModel.RouteOptionUiState? = null,
    isDarkMode: Boolean = false,
    recenterRequest: Int = 0,
    zoomInRequest: Int = 0,
    zoomOutRequest: Int = 0,
    focusPoint: OsmGeoPoint? = null,
    onStationClick: (GasStation) -> Unit,
    stationMapMapper: StationMapMapper = remember { StationMapMapper() },
    renderer: MapRenderer = remember { OsmMapRenderer() }
) {
    val stationItems = remember(stations, selectedFuelTypes) {
        stationMapMapper.mapToMapItems(stations, selectedFuelTypes)
    }

    val genericFocusPoint = remember(focusPoint) {
        focusPoint?.let { GeoPoint(it.latitude, it.longitude) }
    }

    renderer.Render(
        modifier = Modifier.fillMaxSize(),
        stationItems = stationItems,
        userLocation = userLocation,
        route = route,
        isDarkMode = isDarkMode,
        recenterRequest = recenterRequest,
        zoomInRequest = zoomInRequest,
        zoomOutRequest = zoomOutRequest,
        focusPoint = genericFocusPoint,
        onStationClick = { stationId ->
            stations.find { it.id == stationId }?.let { onStationClick(it) }
        }
    )
}
