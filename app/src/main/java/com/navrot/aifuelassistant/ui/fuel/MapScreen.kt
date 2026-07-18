package com.navrot.aifuelassistant.ui.fuel

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.navrot.aifuelassistant.data.GasStationData
import com.navrot.aifuelassistant.data.GasStationRepository
import com.navrot.aifuelassistant.data.model.GasStation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    vehicleId: Long,
    vehicleName: String,
    onBack: () -> Unit,
    onVehiclesClick: () -> Unit = {},
    onStationClick: (GasStation) -> Unit = {},
    viewModel: MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val repository = GasStationRepository

    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFuelTypes by viewModel.selectedFuelTypes.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val cheapestStation by viewModel.cheapestStation.collectAsStateWithLifecycle()

    // Используем кэш из репозитория для избежания повторной инициализации
    val allStations by repository.cachedStations.collectAsStateWithLifecycle(initialValue = emptyList())
    val fuelTypes = listOf("АИ-92", "АИ-95", "АИ-98", "АИ-100", "ДТ", "Газ")

    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var locationStatus by remember { mutableStateOf("Определение местоположения...") }
    var currentCity by remember { mutableStateOf("Рядом с вами") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().osmdroidBasePath = File(context.cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")

        when {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation(context) { location ->
                    userLocation = GeoPoint(location.latitude, location.longitude)
                    locationStatus = "📍 Вы здесь"
                    currentCity = detectCity(location.latitude, location.longitude)
                    // Сохраняем координаты в ViewModel для использования актуальных данных
                    viewModel.setCurrentLocation(location.latitude, location.longitude)
                    viewModel.loadNearbyStations(location.latitude, location.longitude, 50.0)
                }
            }
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation(context) { location ->
                    userLocation = GeoPoint(location.latitude, location.longitude)
                    locationStatus = "📍 Вы здесь"
                    currentCity = detectCity(location.latitude, location.longitude)
                    viewModel.setCurrentLocation(location.latitude, location.longitude)
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
                    Text(
                        if (vehicleId == 0L) "Где бензин?"
                        else "Карта: $vehicleName"
                    )
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
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Сортировка")
                    }
                    IconButton(onClick = onVehiclesClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Мои автомобили")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSearch) {
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            val centerPoint = userLocation ?: GeoPoint(55.1644, 61.4368)
                            controller.setZoom(14.0)
                            controller.setCenter(centerPoint)
                            val userMarker = Marker(this)
                            userMarker.position = centerPoint
                            userMarker.title = locationStatus
                            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            overlays.add(userMarker)
                            // Используем key для оптимизации перерисовки маркеров
                            tag = "map_initialized"
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mapView ->
                        userLocation?.let { location ->
                            mapView.controller.setCenter(location)
                            
                            // Удаляем только маркер пользователя
                            mapView.overlays.removeAll {
                                it is Marker && it.title?.contains("Вы здесь") == true
                            }
                            val userMarker = Marker(mapView)
                            userMarker.position = location
                            userMarker.title = "📍 Вы здесь"
                            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mapView.overlays.add(userMarker)
                            
                            // Обновляем маркеры АЗС только при изменении списка
                            if (mapView.tag != stations.hashCode()) {
                                mapView.overlays.removeAll { it is Marker && it.title != "📍 Вы здесь" }
                                stations.forEach { station ->
                                    val marker = Marker(mapView)
                                    marker.position = GeoPoint(station.latitude, station.longitude)
                                    marker.title = station.name
                                    marker.snippet = "${station.brand} | ${station.address}"
                                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    marker.id = station.id.toString() // key для отслеживания
                                    mapView.overlays.add(marker)
                                }
                                mapView.tag = stations.hashCode()
                            }
                            
                            mapView.invalidate()
                        }
                    }
                )

                if (userLocation == null) {
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopCenter),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = locationStatus,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text(
                text = "Топливо в $currentCity ▼",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )

            // ===== МНОЖЕСТВЕННЫЙ ВЫБОР ТОПЛИВА (чипсы с галочками) =====
            MultiFuelTypeFilter(
                fuelTypes = fuelTypes,
                selectedFuelTypes = selectedFuelTypes,
                onFuelTypeToggled = { viewModel.toggleFuelType(it) }
            )

            // Показать выбранные типы
            if (selectedFuelTypes.size > 1) {
                Text(
                    text = "Выбрано: ${selectedFuelTypes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (sortMode != MapViewModel.SortMode.BEST) {
                FilterChip(
                    selected = true,
                    onClick = { showSortMenu = true },
                    label = {
                        Text(
                            when (sortMode) {
                                MapViewModel.SortMode.PRICE_ASC -> "Сначала дешёвые"
                                MapViewModel.SortMode.PRICE_DESC -> "Сначала дорогие"
                                MapViewModel.SortMode.NEARBY -> "Ближайшие"
                                MapViewModel.SortMode.QUEUE -> "Меньше очередь"
                                else -> ""
                            }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            cheapestStation?.let { station ->
                val fuel = station.fuelTypes.find { it.type == selectedFuelTypes.first() }
                if (fuel != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Самая дешевая ${selectedFuelTypes.first()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "${station.brand}, ${station.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Text(
                                text = "${String.format("%.2f", fuel.price)} ₽",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val displayStations = if (stations.isNotEmpty()) stations else allStations
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayStations) { station ->
                    GasStationCard(
                        station = station,
                        selectedFuelTypes = selectedFuelTypes,
                        onRouteClick = { },
                        onClick = { onStationClick(station) }
                    )
                }
            }
        }
    }

    if (showSortMenu) {
        AlertDialog(
            onDismissRequest = { showSortMenu = false },
            title = { Text("Сортировка") },
            text = {
                Column {
                    SortOption("Лучшее соотношение", MapViewModel.SortMode.BEST, sortMode) {
                        viewModel.setSortMode(it, userLocation?.latitude, userLocation?.longitude)
                        showSortMenu = false
                    }
                    SortOption("Сначала дешёвые", MapViewModel.SortMode.PRICE_ASC, sortMode) {
                        viewModel.setSortMode(it, userLocation?.latitude, userLocation?.longitude)
                        showSortMenu = false
                    }
                    SortOption("Сначала дорогие", MapViewModel.SortMode.PRICE_DESC, sortMode) {
                        viewModel.setSortMode(it, userLocation?.latitude, userLocation?.longitude)
                        showSortMenu = false
                    }
                    SortOption("Ближайшие", MapViewModel.SortMode.NEARBY, sortMode) {
                        viewModel.setSortMode(it, userLocation?.latitude, userLocation?.longitude)
                        showSortMenu = false
                    }
                    SortOption("Меньше очередь", MapViewModel.SortMode.QUEUE, sortMode) {
                        viewModel.setSortMode(it, userLocation?.latitude, userLocation?.longitude)
                        showSortMenu = false
                    }
                }
            },
            confirmButton = {}
        )
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

@Composable
fun MultiFuelTypeFilter(
    fuelTypes: List<String>,
    selectedFuelTypes: Set<String>,
    onFuelTypeToggled: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fuelTypes.forEach { type ->
            val isSelected = selectedFuelTypes.contains(type)
            FilterChip(
                selected = isSelected,
                onClick = { onFuelTypeToggled(type) },
                label = { Text(type) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun SortOption(
    label: String,
    mode: MapViewModel.SortMode,
    currentMode: MapViewModel.SortMode,
    onSelect: (MapViewModel.SortMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = currentMode == mode,
            onClick = { onSelect(mode) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
fun GasStationCard(
    station: GasStation,
    selectedFuelTypes: Set<String>,
    onRouteClick: () -> Unit,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = station.brand.first().toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${station.brand}, ${station.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = station.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRouteClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Маршрут",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                station.fuelTypes.forEach { fuel ->
                    // ===== ПОДСВЕТКА ВСЕХ ВЫБРАННЫХ ТИПОВ =====
                    val isSelected = selectedFuelTypes.contains(fuel.type)
                    val backgroundColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = backgroundColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fuel.type,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (fuel.price > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format("%.2f", fuel.price)} ₽",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (station.queueTime > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Очередь ≈${station.queueTime} мин",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun detectCity(lat: Double, lon: Double): String {
    return when {
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
}

private fun getCurrentLocation(context: android.content.Context, onLocation: (Location) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) onLocation(location)
            }
    } catch (e: SecurityException) {}
}