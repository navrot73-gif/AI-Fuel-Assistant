package com.navrot.aifuelassistant.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.map.SortBar
import org.osmdroid.util.GeoPoint

/**
 * Команда для карты: построить маршрут до АЗС (ставится экраном АЗС).
 */
var pendingRouteStation: GasStation? = null

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MapScreen(
    vehicleId: Long = 0L,
    vehicleName: String = "",
    onBack: () -> Unit = {},
    onVehiclesClick: () -> Unit = {},
    onStationClick: (GasStation) -> Unit = {},
    routeTarget: GasStation? = null,
    onRouteHandled: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFuelTypes by viewModel.selectedFuelTypes.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val isRouting by viewModel.isRouting.collectAsStateWithLifecycle()

    val fuelTypes = listOf("АИ-92", "АИ-95", "АИ-98", "АИ-100", "ДТ", "Газ")

    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var locationStatus by remember { mutableStateOf("Определение местоположения...") }
    var currentCity by remember { mutableStateOf("Рядом с вами") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStationList by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<GasStation?>(null) }
    var routeStation by remember { mutableStateOf<GasStation?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }

    // Построить маршрут в приложении и закрыть большую карточку
    val buildRouteAndClose: (GasStation) -> Unit = { st ->
        userLocation?.let { loc ->
            viewModel.updateUserLocation(loc.latitude, loc.longitude)
        }
        viewModel.buildRouteTo(st)
        routeStation = st
        selectedStation = null
    }

    // Команда с детального экрана (через параметр)
    LaunchedEffect(routeTarget) {
        if (routeTarget != null) {
            userLocation?.let { loc ->
                viewModel.updateUserLocation(loc.latitude, loc.longitude)
            }
            viewModel.buildRouteTo(routeTarget)
            routeStation = routeTarget
            onRouteHandled()
        }
    }

    // Команда с детального экрана (через статическую переменную)
    LaunchedEffect(Unit) {
        pendingRouteStation?.let { st ->
            pendingRouteStation = null
            userLocation?.let { loc ->
                viewModel.updateUserLocation(loc.latitude, loc.longitude)
            }
            viewModel.buildRouteTo(st)
            routeStation = st
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                locationStatus = "Получение координат..."
                getCurrentLocation(context) { location ->
                    userLocation = GeoPoint(location.latitude, location.longitude)
                    locationStatus = "📍 Вы здесь"
                    currentCity = detectCity(location.latitude, location.longitude)
                    viewModel.updateUserLocation(location.latitude, location.longitude)
                    viewModel.loadNearbyStations(location.latitude, location.longitude, 50.0)
                }
            }
            else -> {
                locationStatus = "❌ Геолокация отключена"
                viewModel.setSortMode(MapViewModel.SortMode.BEST)
            }
        }
    }

    LaunchedEffect(Unit) {
        when {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation(context) { location ->
                    userLocation = GeoPoint(location.latitude, location.longitude)
                    locationStatus = "📍 Вы здесь"
                    currentCity = detectCity(location.latitude, location.longitude)
                    viewModel.updateUserLocation(location.latitude, location.longitude)
                    viewModel.loadNearbyStations(location.latitude, location.longitude, 50.0)
                }
            }
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation(context) { location ->
                    userLocation = GeoPoint(location.latitude, location.longitude)
                    locationStatus = "📍 Вы здесь"
                    currentCity = detectCity(location.latitude, location.longitude)
                    viewModel.updateUserLocation(location.latitude, location.longitude)
                    viewModel.loadNearbyStations(location.latitude, location.longitude, 50.0)
                }
            }
            else -> {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (vehicleId == 0L) "Где бензин?"
                            else "Карта: $vehicleName",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Топливо в $currentCity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (vehicleId != 0L) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск")
                    }
                    IconButton(onClick = onVehiclesClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Мои автомобили")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.length >= 2) {
                            viewModel.searchStations(it)
                        } else if (it.isEmpty()) {
                            userLocation?.let { loc ->
                                viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
                            }
                        }
                    },
                    label = { Text("Поиск АЗС...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                userLocation?.let { loc ->
                                    viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
                                }
                            }) {
                                Text("✕")
                            }
                        }
                    }
                )
            }

            SortBar(
                currentSort = sortMode,
                onSortChange = { mode ->
                    viewModel.setSortMode(mode, userLocation?.latitude, userLocation?.longitude)
                }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (!showStationList) {
                    val arrowPulse = rememberInfiniteTransition(label = "pl").animateFloat(
                        0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pl"
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = FueldeckColors.Surface,
                        border = BorderStroke(1.dp, FueldeckColors.Line),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { showStationList = true }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "▲",
                                color = FueldeckColors.Amber.copy(alpha = 0.5f + 0.5f * arrowPulse.value),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp
                            )
                            Text(
                                "АЗС рядом",
                                color = FueldeckColors.Ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                OsmMapView(
                    userLocation = userLocation,
                    stations = stations,
                    selectedFuelTypes = selectedFuelTypes,
                    route = route,
                    recenterRequest = recenterTick,
                    onStationClick = { selectedStation = it }
                )

                if (userLocation == null) {
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = locationStatus,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // ===== Кнопки справа: аккуратная колонка без перекрытий =====
                val yellowRouteVisible =
                    selectedStation != null || (route != null && routeStation != null)

                // Фаза 1: карточка АЗС открыта — строим маршрут в приложении
                if (selectedStation != null) {
                    ExtendedFloatingActionButton(
                        onClick = { selectedStation?.let { buildRouteAndClose(it) } },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = if (showStationList) 380.dp else 16.dp),
                        containerColor = FueldeckColors.Amber,
                        contentColor = Color.Black,
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        text = { Text("Маршрут", fontWeight = FontWeight.Bold) }
                    )
                } else if (route != null && routeStation != null) {
                    // Фаза 2: маршрут построен — открываем навигатор (Google Maps)
                    ExtendedFloatingActionButton(
                        onClick = {
                            routeStation?.let { st ->
                                openMapsRoute(context, st.latitude, st.longitude, st.brand)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = if (showStationList) 380.dp else 16.dp),
                        containerColor = FueldeckColors.Amber,
                        contentColor = Color.Black,
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        text = { Text("Маршрут", fontWeight = FontWeight.Bold) }
                    )
                }

                // Сброс маршрута (✕) — всегда выше жёлтой кнопки
                if (route != null) {
                    SmallFloatingActionButton(
                        onClick = {
                            viewModel.clearRoute()
                            routeStation = null
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 16.dp,
                                bottom = when {
                                    showStationList -> 524.dp
                                    yellowRouteVisible -> 160.dp
                                    else -> 88.dp
                                }
                            ),
                        containerColor = FueldeckColors.Surface,
                        contentColor = FueldeckColors.Coral
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Сбросить маршрут")
                    }
                }

                // Возврат к моему местоположению (📍)
                FloatingActionButton(
                    onClick = {
                        if (userLocation != null) recenterTick++
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = when {
                                showStationList -> 452.dp
                                yellowRouteVisible -> 88.dp
                                else -> 16.dp
                            }
                        ),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Моё местоположение",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                val recommendation = remember(stations, selectedFuelTypes, userLocation) {
                    stations
                        .mapNotNull { st ->
                            val fuel = st.fuelTypes.firstOrNull {
                                (selectedFuelTypes.isEmpty() || selectedFuelTypes.contains(it.type)) && it.available
                            } ?: return@mapNotNull null
                            val dist = userLocation?.let {
                                GeoUtils.calculateDistance(
                                    it.latitude, it.longitude,
                                    st.latitude, st.longitude
                                )
                            } ?: Double.MAX_VALUE
                            Triple(st, fuel, dist)
                        }
                        .minByOrNull { (st, fuel, dist) ->
                            fuel.price +
                                    st.queueTime * 0.5 +
                                    (if (dist == Double.MAX_VALUE) 0.0 else dist * 0.3)
                        }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    // Карточка AI скрывается, пока активен маршрут
                    AnimatedVisibility(
                        visible = !showStationList && recommendation != null && route == null,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                    ) {
                        AiRecommendationCard(
                            recommendation = recommendation,
                            onExpandList = { showStationList = true }
                        )
                    }

                    StationBottomSheet(
                        visible = showStationList,
                        isLoading = isLoading,
                        stations = stations,
                        selectedFuelTypes = selectedFuelTypes,
                        sortMode = sortMode,
                        userLocation = userLocation,
                        fuelTypes = fuelTypes,
                        onToggleFuelType = { viewModel.toggleFuelType(it) },
                        onSortChange = { mode ->
                            viewModel.setSortMode(mode, userLocation?.latitude, userLocation?.longitude)
                        },
                        onStationClick = {
                            selectedStation = it
                            onStationClick(it)
                        },
                        onToggleVisibility = { showStationList = !showStationList }
                    )

                    AnimatedVisibility(
                        visible = selectedStation != null,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                    ) {
                        selectedStation?.let { station ->
                            StationDetailCard(
                                station = station,
                                selectedFuelTypes = selectedFuelTypes,
                                onClose = { selectedStation = null },
                                onBuildRoute = { buildRouteAndClose(station) },
                                isRouting = isRouting,
                                routeText = route?.let {
                                    "${it.distanceText} · ${it.durationText} · до ${it.destination}"
                                },
                                onClearRoute = {
                                    viewModel.clearRoute()
                                    routeStation = null
                                }
                            )
                        }
                    }
                }
            }
        }

        error?.let { errorMsg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Ошибка") },
                text = { Text(errorMsg) },
                confirmButton = {
                    Button(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

private fun detectCity(lat: Double, lon: Double): String = when {
    lat in 55.1..55.3 && lon in 61.2..61.6 -> "Челябинске"
    lat in 54.0..54.2 && lon in 61.4..61.7 -> "Троицке"
    lat in 55.0..55.1 && lon in 60.0..60.2 -> "Миассе"
    lat in 55.1..55.2 && lon in 59.5..59.8 -> "Златоусте"
    lat in 53.3..53.5 && lon in 58.9..59.2 -> "Магнитогорске"
    lat in 55.0..55.1 && lon in 61.5..61.7 -> "Копейске"
    lat in 56.0..56.1 && lon in 60.6..60.8 -> "Снежинске"
    lat in 55.7..55.8 && lon in 60.6..60.8 -> "Озёрске"
    lat in 54.4..54.5 && lon in 61.1..61.3 -> "Южноуральске"
    lat in 54.9..55.0 && lon in 57.2..57.4 -> "Аше"
    lat in 55.7..55.8 && lon in 37.5..37.7 -> "Москве"
    else -> "вашем районе"
}