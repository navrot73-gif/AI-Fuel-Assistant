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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

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

    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var locationStatus by remember { mutableStateOf("Определение местоположения...") }
    var currentCity by remember { mutableStateOf("Рядом с вами") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStationList by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<GasStation?>(null) }
    var routeStation by remember { mutableStateOf<GasStation?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }

    val onLocationReady: (GeoPoint) -> Unit = { loc ->
        userLocation = loc
        locationStatus = "📍 Вы здесь"
        viewModel.updateUserLocation(loc.latitude, loc.longitude)
        viewModel.loadNearbyStations(loc.latitude, loc.longitude, 50.0)
        // Определяем город через Nominatim асинхронно
        scope.launch {
            try {
                currentCity = viewModel.detectCity(loc.latitude, loc.longitude)
            } catch (_: Exception) {
                // Оставляем предыдущее значение currentCity
            }
        }
    }

    val buildRouteAndClose: (GasStation) -> Unit = { st ->
        userLocation?.let { loc ->
            viewModel.updateUserLocation(loc.latitude, loc.longitude)
        }
        viewModel.buildRouteTo(st)
        routeStation = st
        selectedStation = null
    }

    // Обработка routeTarget из savedStateHandle (приходит от station_detail)
    LaunchedEffect(routeTarget) {
        if (routeTarget != null) {
            // Ждём определения местоположения (до 5 сек),
            // иначе buildRouteTo не сможет построить маршрут.
            var attempts = 0
            while (userLocation == null && attempts < 50) {
                delay(100)
                attempts++
            }
            userLocation?.let { loc ->
                viewModel.updateUserLocation(loc.latitude, loc.longitude)
            }
            viewModel.buildRouteTo(routeTarget)
            routeStation = routeTarget
            onRouteHandled()
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
                    onLocationReady(GeoPoint(location.latitude, location.longitude))
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
                    onLocationReady(GeoPoint(location.latitude, location.longitude))
                }
            }
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation(context) { location ->
                    onLocationReady(GeoPoint(location.latitude, location.longitude))
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

            Box(modifier = Modifier.fillMaxSize()) {
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

                // ===== Кнопки справа =====
                val yellowRouteVisible =
                    selectedStation != null || (route != null && routeStation != null)

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

                // AI-рекомендация из ViewModel (централизованный скоринг)
                val recommendationTriple = aiRecommendation?.let {
                    Triple(it.station, it.fuel, it.distanceKm)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    if (!showStationList) {
                        val arrowPulse = rememberInfiniteTransition(label = "pl").animateFloat(
                            0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pl"
                        )
                        Surface(
                            modifier = Modifier.padding(16.dp),
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

                        if (recommendationTriple != null && route == null) {
                            AiRecommendationCard(
                                recommendation = recommendationTriple,
                                onExpandList = { selectedStation = recommendationTriple.first }
                            )
                        }
                    }

                    StationBottomSheet(
                        visible = showStationList,
                        isLoading = isLoading,
                        stations = viewModel.filterStationsByBrands(stations),
                        aiRecommendation = recommendationTriple?.first,
                        selectedFuelTypes = selectedFuelTypes,
                        sortMode = sortMode,
                        userLocation = userLocation,
                        fuelTypes = fuelTypes,
                        brands = viewModel.availableBrands(),
                        selectedBrands = selectedBrands,
                        openOnly = openOnly,
                        onToggleOpenOnly = { viewModel.toggleOpenOnly() },
                        onToggleFuelType = { viewModel.toggleFuelType(it) },
                        onToggleBrand = { viewModel.toggleBrand(it) },
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
                                },
                                onReportPrice = { stationId, fuelType, price ->
                                    viewModel.reportPrice(stationId, fuelType, price)
                                    selectedStation = selectedStation?.let { st ->
                                        if (st.id == stationId) {
                                            st.copy(
                                                fuelTypes = st.fuelTypes.map { f ->
                                                    if (f.type == fuelType) f.copy(price = price) else f
                                                }
                                            )
                                        } else st
                                    }
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
