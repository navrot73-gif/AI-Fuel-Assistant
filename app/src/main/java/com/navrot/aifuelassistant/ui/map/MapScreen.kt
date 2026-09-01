package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import timber.log.Timber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navrot.aifuelassistant.data.model.GasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import com.navrot.aifuelassistant.data.UserPreferencesRepository
import com.navrot.aifuelassistant.ui.map.components.LocationPermissionHandler
import com.navrot.aifuelassistant.ui.map.components.LocationStatusIndicator
import com.navrot.aifuelassistant.ui.map.components.MapErrorDialog
import com.navrot.aifuelassistant.ui.map.components.MapFloatingActions
import com.navrot.aifuelassistant.ui.map.components.MapSearchBar
import com.navrot.aifuelassistant.ui.map.components.MapTopBar
import com.navrot.aifuelassistant.ui.map.components.RouteOverlay
import com.navrot.aifuelassistant.ui.components.OfflineBanner
import com.navrot.aifuelassistant.ui.map.components.StationDetailOverlay
import com.navrot.aifuelassistant.ui.map.components.StationListBottomSheet
import com.navrot.aifuelassistant.geo.GeoPoint
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint as OsmGeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    vehicleId: Long = 0L,
    vehicleName: String = "",
    onBack: () -> Unit = {},
    onVehiclesClick: () -> Unit = {},
    pendingRouteStationId: Int? = null,
    pendingOpenStationId: Int? = null,
    aiAnswerText: String? = null,
    showStationList: Boolean = false,
    onConsumePendingRoute: () -> Unit = {},
    onConsumePendingOpenStation: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFuelTypes by viewModel.selectedFuelTypes.collectAsStateWithLifecycle()
    val selectedBrands by viewModel.selectedBrands.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val isRouting by viewModel.isRouting.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val mapEngine by viewModel.mapEngine.collectAsStateWithLifecycle()
    val openOnly by viewModel.openOnly.collectAsStateWithLifecycle()
    val aiRecommendation by viewModel.aiRecommendation.collectAsStateWithLifecycle()
    val userCancelledRoute by viewModel.userCancelledRoute.collectAsStateWithLifecycle()
    val autoBuildConsumed by viewModel.autoBuildConsumed.collectAsStateWithLifecycle()
    val currentCity by viewModel.currentCity.collectAsStateWithLifecycle()
    val geocodedLocation by viewModel.geocodedLocation.collectAsStateWithLifecycle()
    val bestStationRanked by viewModel.bestStationRanked.collectAsStateWithLifecycle()
    val avgPrice by viewModel.avgPrice.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val lastCacheUpdateMs by viewModel.lastCacheUpdateTime.collectAsStateWithLifecycle()

    val fuelTypes = listOf("АИ-92", "АИ-95", "АИ-98", "АИ-100", "ДТ", "Газ")
    val recommendationTriple = aiRecommendation?.let { Triple(it.station, it.fuel, it.distanceKm) }

    var userLocation by remember { mutableStateOf<UserLocationState?>(null) }
    var locationStatus by remember { mutableStateOf("Определение местоположения...") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStationList by remember { mutableStateOf(showStationList) }
    var selectedStation by remember { mutableStateOf<GasStation?>(null) }
    var routeStation by remember { mutableStateOf<GasStation?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }
    var zoomInTick by remember { mutableIntStateOf(0) }
    var zoomOutTick by remember { mutableIntStateOf(0) }
    var hasInitialCentered by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val yellowRouteVisible = selectedStation != null || (route != null && routeStation != null)

    val supportedCities = listOf(
        "Челябинск",
        "Троицк",
        "Миасс",
        "Златоуст",
        "Магнитогорск",
        "Копейск",
        "Снежинск",
        "Озёрск",
        "Южноуральск",
        "Аша",
        "Москва",
        "Екатеринбург",
        "Тюмень",
        "Пермь"
    )

    val bottomPadding = when {
        showStationList -> 452.dp
        aiRecommendation != null -> 150.dp
        yellowRouteVisible -> 88.dp
        else -> 16.dp
    }

    val onLocationUpdate: (UserLocationState) -> Unit = { loc ->
        userLocation = loc
        locationStatus = "📍 Вы здесь"
        viewModel.updateUserLocation(loc.latitude, loc.longitude)
        viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
        viewModel.updateCityAndPrices(loc.latitude, loc.longitude)

        if (!hasInitialCentered) {
            hasInitialCentered = true
            recenterTick++
        }
    }

    val buildRouteAndClose: (GasStation) -> Unit = { st ->
        userLocation?.let { loc -> viewModel.updateUserLocation(loc.latitude, loc.longitude) }
        viewModel.resetUserCancelledRoute()
        viewModel.buildRouteTo(st)
        routeStation = st
        selectedStation = null
        showStationList = false
        onConsumePendingRoute()
    }


    LaunchedEffect(pendingRouteStationId, stations, userLocation, userCancelledRoute, autoBuildConsumed) {
        if (userCancelledRoute || autoBuildConsumed) return@LaunchedEffect
        val id = pendingRouteStationId ?: return@LaunchedEffect
        // Ждём и локацию, и станцию, и маршрут — до 6 секунд
        var waited = 0
        var foundStation: GasStation? = null // Variable to hold the found station
        while (waited < 6000) {
            val loc = userLocation
            // Diagnostic log when stations list is not empty
            if (stations.isNotEmpty()) {
                 Timber.tag("RouteHandoff").d("want id=%d, have ids=%s, names=%s", id, stations.take(10).map { it.id }, stations.take(5).map { it.name })
            }

            // Triple fallback logic for finding the station
            val st = stations.firstOrNull { it.id == id }
                ?: loc?.let { l ->
                    // не нашли по id — берём БЛИЖАЙШУЮ к пользователю
                    stations.minByOrNull {
                        com.navrot.aifuelassistant.geo.GeoUtils
                            .calculateDistance(l.latitude, l.longitude, it.latitude, it.longitude)
                    }
                }
                ?: stations.firstOrNull()

            // НОВОЕ: если станций нет — загрузи сам
            if (loc != null && stations.isEmpty() && waited == 0) {
                Timber.tag("RouteHandoff").d("loading stations near %s", loc)
                viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
            }

            if (loc != null && st != null) {
                viewModel.markAutoBuildConsumed()
                viewModel.updateUserLocation(loc.latitude, loc.longitude)
                viewModel.buildRouteTo(st)
                onConsumePendingRoute()
                routeStation = st
                selectedStation = st
                showStationList = false
                foundStation = st // Assign the found station
                Timber.tag("RouteHandoff").d("route built to %s (id=%d, wanted=%d)", st.name, st.id, id)
                break
            }

            // НОВОЕ: если прошло 2 сек и станций всё ещё нет — попробуй ещё раз
            if (waited > 2000 && stations.isEmpty() && waited % 2000 == 0) {
                Timber.tag("RouteHandoff").d("retry loading stations")
                userLocation?.let {
                    viewModel.loadNearbyStations(it.latitude, it.longitude, 50.0)
                }
            }

            kotlinx.coroutines.delay(300)
            waited += 300
        }
        // fallback: если st всё ещё не найден после 6 сек —
        // всё равно закрой панель и покажи маркер
        if (foundStation == null) {
            Timber.tag("RouteHandoff").w("station %d NOT FOUND after 6s, closing panel", id)
            showStationList = false
        }

        Timber.tag("RouteHandoff").d("Consuming pending route request.")
        onConsumePendingRoute()
    }

    // Handle pending open station from AI card handoff - bind route to opened station
    LaunchedEffect(pendingOpenStationId, stations, userCancelledRoute) {
        if (userCancelledRoute) return@LaunchedEffect
        val id = pendingOpenStationId ?: return@LaunchedEffect
        stations.firstOrNull { it.id == id }?.let { station ->
            selectedStation = station
            // Bug 4 fix: Build route to the opened station
            buildRouteAndClose(station)
            onConsumePendingOpenStation()
        }
    }

    // Snackbar for AI answer handoff and routing errors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(aiAnswerText, snackbarHostState) {
        aiAnswerText?.let { text ->
            val snippet = if (text.length > 100) text.substring(0, 100) + "…" else text
            snackbarHostState.showSnackbar(message = snippet)
        }
    }
    LaunchedEffect(error, snackbarHostState) {
        error?.let { errMsg ->
            snackbarHostState.showSnackbar(message = errMsg)
        }
    }

    LocationPermissionHandler(
        onLocationUpdate = onLocationUpdate,
        onPermissionDenied = {
            locationStatus = "❌ Геолокация отключена"
            viewModel.setSortMode(MapViewModel.SortMode.BEST)
        }
    )

    // Fallback: 10s timeout → center on Chelyabinsk (55.164, 61.436) zoom 12 + banner
    LaunchedEffect(Unit) {
        delay(10_000)
        if (userLocation == null) {
            Timber.tag("MapScreen").d("Location: timeout 10s, fallback to Chelyabinsk center")
            locationStatus = "📍 Включите геолокацию для точности"
            // Center map on Chelyabinsk via recenter mechanism
            // Note: OsmMapView will use default center if userLocation is null
        }
    }

    Scaffold(
        topBar = {
            MapTopBar(
                vehicleId = vehicleId, vehicleName = vehicleName, currentCity = currentCity,
                onBack = onBack, onSearchClick = { showSearch = !showSearch },
                onSettingsClick = { showSettingsDialog = true },
                onVehiclesClick = onVehiclesClick,
                onCityClick = { showCityPicker = true }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OfflineBanner(
                isOnline = isOnline,
                lastCacheUpdateMs = lastCacheUpdateMs
            )

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
                    viewModel.clearGeocodedLocation()
                    userLocation?.let { loc ->
                        viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
                    }
                }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (mapEngine == UserPreferencesRepository.ENGINE_MAPLIBRE) {
                    MapLibreView(
                        userLocation = userLocation, stations = stations,
                        selectedFuelTypes = selectedFuelTypes,
                        isDarkMode = isDarkMode,
                        recenterRequest = recenterTick,
                        zoomInRequest = zoomInTick, zoomOutRequest = zoomOutTick,
                        onStationClick = { selectedStation = it }
                    )
                } else {
                    OsmMapView(
                        userLocation = userLocation, stations = stations,
                        selectedFuelTypes = selectedFuelTypes, route = route,
                        isDarkMode = isDarkMode,
                        recenterRequest = recenterTick,
                        zoomInRequest = zoomInTick, zoomOutRequest = zoomOutTick,
                        focusPoint = geocodedLocation?.let { OsmGeoPoint(it.latitude, it.longitude) },
                        onStationClick = { selectedStation = it }
                    )
                }

                LocationStatusIndicator(status = locationStatus, visible = userLocation == null)

                RouteOverlay(
                    selectedStation = selectedStation, route = route, routeStation = routeStation,
                    aiRecommendation = recommendationTriple, showStationList = showStationList,
                    onBuildRoute = { selectedStation?.let { buildRouteAndClose(it) } },
                    onClearRoute = {
                        viewModel.clearRoute()
                        routeStation = null
                        selectedStation = null
                        onConsumePendingRoute()
                        onConsumePendingOpenStation()
                    },
                    onExpandList = { showStationList = true },
                    onSelectAiPick = { selectedStation = recommendationTriple?.first }
                )

                StationListBottomSheet(
                    visible = showStationList, isLoading = isLoading,
                    stations = viewModel.filterStationsByBrands(stations),
                    aiRecommendation = recommendationTriple?.first,
                    bestStation = bestStationRanked,
                    avgPrice = avgPrice,
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
                    onStationClick = { selectedStation = it },
                    onToggleVisibility = { showStationList = !showStationList }
                )

                StationDetailOverlay(
                    station = selectedStation, selectedFuelTypes = selectedFuelTypes,
                    isRouting = isRouting,
                    routeText = route?.let {
                        if (it.isDirect || it.isStraightLine) {
                            "${it.distanceText} по прямой"
                        } else {
                            "${it.distanceText} · ${it.durationText}"
                        }
                    },
                    onClose = { selectedStation = null },
                    onBuildRoute = { selectedStation?.let { buildRouteAndClose(it) } },
                    onClearRoute = {
                        viewModel.clearRoute()
                        routeStation = null
                        selectedStation = null
                        onConsumePendingRoute()
                        onConsumePendingOpenStation()
                    },
                    onReportPrice = { stationId, fuelType, price ->
                        viewModel.reportPrice(stationId, fuelType, price)
                        selectedStation = selectedStation?.let { st ->
                            if (st.id == stationId) {
                                st.copy(
                                    fuelTypes = st.fuelTypes.map { f ->
                                        if (f.type == fuelType) {
                                            f.copy(
                                                price = price,
                                                source = com.navrot.aifuelassistant.data.model.FuelDataSource.USER_REPORT,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                        } else f
                                    },
                                    dataSources = st.dataSources + com.navrot.aifuelassistant.data.model.FuelDataSource.USER_REPORT
                                )
                            } else st
                        }
                    }
                )

                if (selectedStation == null && !showStationList) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = bottomPadding),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (yellowRouteVisible || route != null) {
                            androidx.compose.material3.SmallFloatingActionButton(
                                onClick = {
                                    viewModel.clearRoute()
                                    routeStation = null
                                    selectedStation = null
                                    onConsumePendingRoute()
                                    onConsumePendingOpenStation()
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Сбросить маршрут",
                                    tint = Color(0xFFF08070)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        androidx.compose.material3.SmallFloatingActionButton(
                            onClick = { viewModel.toggleDarkMode() },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ) {
                            Icon(
                                if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = "Переключить тему карты",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        androidx.compose.material3.FloatingActionButton(
                            onClick = { if (userLocation != null) recenterTick++ },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Моё местоположение",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shadowElevation = 4.dp
                        ) {
                            IconButton(onClick = { zoomInTick++ }) {
                                Icon(Icons.Default.Add, contentDescription = "Увеличить", tint = Color.White, modifier = Modifier.size(44.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shadowElevation = 4.dp
                        ) {
                            IconButton(onClick = { zoomOutTick++ }) {
                                Box(
                                    modifier = Modifier.size(44.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp, 2.5.dp)
                                            .background(Color.White, RoundedCornerShape(1.dp))
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Настройки карты") },
            text = {
                Column {
                    Text(
                        "Движок карты",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setMapEngine(UserPreferencesRepository.ENGINE_OSMDROID) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (mapEngine == UserPreferencesRepository.ENGINE_OSMDROID),
                            onClick = { viewModel.setMapEngine(UserPreferencesRepository.ENGINE_OSMDROID) }
                        )
                        Text("Классика (osmdroid)", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setMapEngine(UserPreferencesRepository.ENGINE_MAPLIBRE) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (mapEngine == UserPreferencesRepository.ENGINE_MAPLIBRE),
                            onClick = { viewModel.setMapEngine(UserPreferencesRepository.ENGINE_MAPLIBRE) }
                        )
                        Text("Вектор (beta)", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Готово")
                }
            }
        )
    }

    if (showCityPicker) {
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = { Text("Выберите город") },
            text = {
                LazyColumn {
                    items(supportedCities) { city ->
                        Text(
                            text = city,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setManualCity(city)
                                    showCityPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCityPicker = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    MapErrorDialog(error = error, onDismiss = { viewModel.clearError() })
}