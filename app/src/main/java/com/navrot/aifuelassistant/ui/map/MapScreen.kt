package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.map.components.LocationPermissionHandler
import com.navrot.aifuelassistant.ui.map.components.LocationStatusIndicator
import com.navrot.aifuelassistant.ui.map.components.MapErrorDialog
import com.navrot.aifuelassistant.ui.map.components.MapFloatingActions
import com.navrot.aifuelassistant.ui.map.components.MapSearchBar
import com.navrot.aifuelassistant.ui.map.components.MapTopBar
import com.navrot.aifuelassistant.ui.map.components.RouteOverlay
import com.navrot.aifuelassistant.ui.map.components.StationDetailOverlay
import com.navrot.aifuelassistant.ui.map.components.StationListBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    vehicleId: Long = 0L,
    vehicleName: String = "",
    onBack: () -> Unit = {},
    onVehiclesClick: () -> Unit = {},
    onStationClick: (GasStation) -> Unit = {},
    onRouteClick: (Int) -> Unit = {},
    pendingRouteStationId: Int? = null,
    onConsumePendingRoute: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFuelTypes by viewModel.selectedFuelTypes.collectAsStateWithLifecycle()
    val selectedBrands by viewModel.selectedBrands.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val isRouting by viewModel.isRouting.collectAsStateWithLifecycle()
    val openOnly by viewModel.openOnly.collectAsStateWithLifecycle()
    val aiRecommendation by viewModel.aiRecommendation.collectAsStateWithLifecycle()

    val fuelTypes = listOf("АИ-92", "АИ-95", "АИ-98", "АИ-100", "ДТ", "Газ")
    val recommendationTriple = aiRecommendation?.let { Triple(it.station, it.fuel, it.distanceKm) }

    var userLocation by remember { mutableStateOf<UserLocationState?>(null) }
    var locationStatus by remember { mutableStateOf("Определение местоположения...") }
    var currentCity by remember { mutableStateOf("Рядом с вами") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStationList by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<GasStation?>(null) }
    var routeStation by remember { mutableStateOf<GasStation?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }
    val yellowRouteVisible = selectedStation != null || (route != null && routeStation != null)

    val onLocationUpdate: (UserLocationState) -> Unit = { loc ->
        userLocation = loc
        locationStatus = "📍 Вы здесь"
        viewModel.updateUserLocation(loc.latitude, loc.longitude)
        viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
        scope.launch {
            try { currentCity = viewModel.detectCity(loc.latitude, loc.longitude) } catch (_: Exception) {}
        }
    }

    val buildRouteAndClose: (GasStation) -> Unit = { st ->
        userLocation?.let { loc -> viewModel.updateUserLocation(loc.latitude, loc.longitude) }
        viewModel.buildRouteTo(st)
        routeStation = st
        selectedStation = null
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            delay(300)
            viewModel.searchStations(searchQuery)
        }
    }

    LaunchedEffect(pendingRouteStationId, stations) {
        val id = pendingRouteStationId ?: return@LaunchedEffect
        stations.firstOrNull { it.id == id }?.let { station ->
            buildRouteAndClose(station)
            onConsumePendingRoute()
        }
    }

    LocationPermissionHandler(
        onLocationUpdate = onLocationUpdate,
        onPermissionDenied = {
            locationStatus = "❌ Геолокация отключена"
            viewModel.setSortMode(MapViewModel.SortMode.BEST)
        }
    )

    Scaffold(
        topBar = {
            MapTopBar(
                vehicleId = vehicleId, vehicleName = vehicleName, currentCity = currentCity,
                onBack = onBack, onSearchClick = { showSearch = !showSearch }, onVehiclesClick = onVehiclesClick
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MapSearchBar(
                visible = showSearch, query = searchQuery,
                onQueryChange = { newValue ->
                    searchQuery = newValue
                    if (newValue.isEmpty()) userLocation?.let { loc ->
                        viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
                    }
                },
                onClear = {
                    searchQuery = ""
                    userLocation?.let { loc ->
                        viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
                    }
                }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                OsmMapView(
                    userLocation = userLocation, stations = stations,
                    selectedFuelTypes = selectedFuelTypes, route = route,
                    recenterRequest = recenterTick, onStationClick = { selectedStation = it }
                )

                LocationStatusIndicator(status = locationStatus, visible = userLocation == null)

                RouteOverlay(
                    selectedStation = selectedStation, route = route, routeStation = routeStation,
                    aiRecommendation = recommendationTriple, showStationList = showStationList,
                    onBuildRoute = { selectedStation?.let { buildRouteAndClose(it) } },
                    onClearRoute = { viewModel.clearRoute(); routeStation = null },
                    onExpandList = { showStationList = true },
                    onSelectAiPick = { selectedStation = recommendationTriple?.first }
                )

                StationListBottomSheet(
                    visible = showStationList, isLoading = isLoading,
                    stations = viewModel.filterStationsByBrands(stations),
                    aiRecommendation = recommendationTriple?.first,
                    selectedFuelTypes = selectedFuelTypes, sortMode = sortMode,
                    userLocation = userLocation?.toGeoPoint(), fuelTypes = fuelTypes,
                    brands = viewModel.availableBrands(), selectedBrands = selectedBrands,
                    openOnly = openOnly,
                    onToggleOpenOnly = { viewModel.toggleOpenOnly() },
                    onToggleFuelType = { viewModel.toggleFuelType(it) },
                    onToggleBrand = { viewModel.toggleBrand(it) },
                    onSortChange = { mode ->
                        viewModel.setSortMode(mode, userLocation?.latitude, userLocation?.longitude)
                    },
                    onStationClick = { selectedStation = it; onStationClick(it) },
                    onToggleVisibility = { showStationList = !showStationList }
                )

                StationDetailOverlay(
                    station = selectedStation, selectedFuelTypes = selectedFuelTypes,
                    isRouting = isRouting,
                    routeText = route?.let { "${it.distanceText} · ${it.durationText} · до ${it.destination}" },
                    onClose = { selectedStation = null },
                    onBuildRoute = { selectedStation?.let { buildRouteAndClose(it) } },
                    onClearRoute = { viewModel.clearRoute(); routeStation = null },
                    onReportPrice = { stationId, fuelType, price ->
                        viewModel.reportPrice(stationId, fuelType, price)
                        selectedStation = selectedStation?.let { st ->
                            if (st.id == stationId) {
                                st.copy(fuelTypes = st.fuelTypes.map { f ->
                                    if (f.type == fuelType) f.copy(price = price) else f
                                })
                            } else st
                        }
                    }
                )

                MapFloatingActions(
                    bottomPadding = when {
                        showStationList -> 452.dp
                        aiRecommendation != null -> 150.dp
                        yellowRouteVisible -> 88.dp
                        else -> 16.dp
                    },
                    onRecenter = { if (userLocation != null) recenterTick++ }
                )
            }
        }
    }

    MapErrorDialog(error = error, onDismiss = { viewModel.clearError() })
}