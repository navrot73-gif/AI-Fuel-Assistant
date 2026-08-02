package com.navrot.aifuelassistant.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.navrot.aifuelassistant.data.GasStationData
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.fuel.MapViewModel
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MapScreen(
    vehicleId: Long = 0L,
    vehicleName: String = "",
    onBack: () -> Unit = {},
    onVehiclesClick: () -> Unit = {},
    onStationClick: (GasStation) -> Unit = {},
    viewModel: MapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current

    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFuelTypes by viewModel.selectedFuelTypes.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()

    val allStations = remember { GasStationData.stations }
    val fuelTypes = listOf("АИ-92", "АИ-95", "АИ-98", "АИ-100", "ДТ", "Газ")

    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var locationStatus by remember { mutableStateOf("Определение местоположения...") }
    var currentCity by remember { mutableStateOf("Рядом с вами") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStationList by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<GasStation?>(null) }

    val displayStations = if (stations.isNotEmpty()) stations else allStations

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
                    viewModel.loadNearbyStations(location.latitude, location.longitude, 50.0)
                }
            }
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation(context) { location ->
                    userLocation = GeoPoint(location.latitude, location.longitude)
                    locationStatus = "📍 Вы здесь"
                    currentCity = detectCity(location.latitude, location.longitude)
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
            // ===== ПОИСК =====
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

            // ===== КАРТА (на весь экран, список поверх) =====
            Box(modifier = Modifier.fillMaxSize()) {
                // Плашка-открывалка списка (видна, когда панель свёрнута)
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
                            Text("АЗС рядом", color = FueldeckColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }

                // Карта на фоне
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            val centerPoint = userLocation ?: GeoPoint(55.1644, 61.4368)
                            controller.setZoom(13.0)
                            controller.setCenter(centerPoint)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mapView ->
                        // Очистка старых маркеров
                        mapView.overlays.removeAll { it is Marker }

                        // Маркер пользователя
                        userLocation?.let { location ->
                            mapView.controller.setCenter(location)
                            val userMarker = Marker(mapView)
                            userMarker.position = location
                            userMarker.title = "📍 Вы здесь"
                            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mapView.overlays.add(userMarker)
                        }

                        // Маркеры АЗС с цветовой кодировкой
                        displayStations.forEach { station ->
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(station.latitude, station.longitude)
                            marker.title = station.name
                            marker.snippet = buildStationSnippet(station, selectedFuelTypes)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                            // Цвет маркера по статусу
                            val markerColor = getMarkerColor(station, selectedFuelTypes)
                            marker.icon = createColoredMarker(context, markerColor)

                            marker.setOnMarkerClickListener { _, _ ->
                                selectedStation = station
                                true
                            }
                            mapView.overlays.add(marker)
                        }
                        mapView.invalidate()
                    }
                )

                // Статус геолокации
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

                // Кнопка "Моё местоположение"
                FloatingActionButton(
                    onClick = {
                        userLocation?.let { loc ->
                            // Центрировать карту на пользователе
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = if (showStationList) 320.dp else 16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Моё местоположение",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // ===== AI-РЕКОМЕНДАЦИЯ ближайшей АЗС с топливом (свёрнутое состояние) =====
                val recommendation = remember(displayStations, selectedFuelTypes, userLocation) {
                    displayStations
                        .mapNotNull { st ->
                            val fuel = st.fuelTypes.firstOrNull {
                                (selectedFuelTypes.isEmpty() || selectedFuelTypes.contains(it.type)) && it.available
                            } ?: return@mapNotNull null
                            val dist = userLocation?.let {
                                calculateDistance(it.latitude, it.longitude, st.latitude, st.longitude)
                            } ?: Double.MAX_VALUE
                            Triple(st, fuel, dist)
                        }
                        .sortedWith(compareBy<Triple<GasStation, *, Double>> { it.first.queueTime }.thenBy { it.third })
                        .firstOrNull()
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = !showStationList && recommendation != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    recommendation?.let { rec ->
                        val st = rec.first
                        val fuel = rec.second
                        val dist = rec.third
                        val brandColors = listOf(
                            FueldeckColors.Teal, FueldeckColors.Amber, FueldeckColors.Coral,
                            Color(0xFF2F7FD1), Color(0xFF7A6BD1), Color(0xFF2FAA55)
                        )
                        val brandColor = brandColors[Math.floorMod(st.brand.hashCode(), brandColors.size)]
                        val spark = rememberInfiniteTransition(label = "rec").animateFloat(
                            0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "rec"
                        )
                        val reason = buildString {
                            append("ближайшая с ${fuel.type} в наличии")
                            if (st.queueTime <= 0) append(", без очереди")
                            else append(", очередь ${st.queueTime} мин")
                        }

                        Surface(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = FueldeckColors.Surface,
                            border = BorderStroke(1.dp, FueldeckColors.Teal.copy(alpha = 0.35f)),
                            shadowElevation = 10.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { showStationList = true }
                                    .drawBehind { // ambient-блик в цвете бренда
                                        drawRect(
                                            brush = Brush.radialGradient(
                                                listOf(brandColor.copy(alpha = 0.16f), Color.Transparent),
                                                center = Offset(size.width * 0.85f, size.height * 0.2f),
                                                radius = size.width * 0.6f
                                            )
                                        )
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // логотип сети с бегущим бликом
                                val logoSheen = rememberInfiniteTransition(label = "rlogo").animateFloat(
                                    0f, 1f, infiniteRepeatable(tween(4200), RepeatMode.Restart), label = "rlogo"
                                ).value
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(brandColor, RoundedCornerShape(13.dp))
                                        .drawBehind {
                                            val w = size.width; val band = w * 0.5f
                                            val x = -band + logoSheen * (w + band)
                                            drawRect(
                                                brush = Brush.linearGradient(
                                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                                                    start = Offset(x, 0f), end = Offset(x + band, 0f)
                                                ),
                                                topLeft = Offset(x, 0f), size = Size(band, size.height)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(st.brand.first().toString().uppercase(), color = Color.White,
                                        fontWeight = FontWeight.Bold, fontSize = 19.sp)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("✨", color = FueldeckColors.Teal.copy(alpha = 0.5f + 0.5f * spark.value), fontSize = 13.sp)
                                        Text("AI‑подбор", color = FueldeckColors.Teal, fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp, letterSpacing = 0.6.sp)
                                    }
                                    Text(st.brand, color = FueldeckColors.Ink, fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp, maxLines = 1)
                                    Text(reason, color = FueldeckColors.InkFaint, fontSize = 11.sp, maxLines = 1)
                                }

                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${String.format("%.0f", fuel.price)} ₽", color = FueldeckColors.Amber,
                                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(if (dist == Double.MAX_VALUE) "—" else "${String.format("%.1f", dist)} км",
                                        color = FueldeckColors.InkDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }

                                // кнопка маршрута с press-feedback
                                var pressed by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            FueldeckColors.TealSoft.copy(alpha = if (pressed) 0.4f else 1f),
                                            CircleShape
                                        )
                                        .clickable {
                                            openMapsRoute(context, st.latitude, st.longitude, st.brand)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = "Маршрут",
                                        tint = FueldeckColors.Teal, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }

                // ===== НИЖНЯЯ ПАНЕЛЬ =====
                androidx.compose.animation.AnimatedVisibility(
                    visible = showStationList,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        // Ручка для свайпа
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                )
                                .clickable { showStationList = !showStationList }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }

                        // Фильтры топлива
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp
                        ) {
                            Column {
                                MultiFuelTypeFilter(
                                    fuelTypes = fuelTypes,
                                    selectedFuelTypes = selectedFuelTypes,
                                    onFuelTypeToggled = { viewModel.toggleFuelType(it) }
                                )

                                // Сортировка
                                SortBar(
                                    currentSort = sortMode,
                                    onSortChange = { mode ->
                                        viewModel.setSortMode(mode, userLocation?.latitude, userLocation?.longitude)
                                    }
                                )
                            }
                        }

                        // Список АЗС
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(displayStations.take(10)) { station ->
                                        StationListItem(
                                            station = station,
                                            selectedFuelTypes = selectedFuelTypes,
                                            userLocation = userLocation,
                                            onClick = {
                                                selectedStation = station
                                                onStationClick(station)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ===== КАРТОЧКА ВЫБРАННОЙ АЗС =====
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedStation != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    selectedStation?.let { station ->
                        StationDetailCard(
                            station = station,
                            selectedFuelTypes = selectedFuelTypes,
                            onClose = { selectedStation = null }
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

// ===== ФИЛЬТРЫ ТОПЛИВА (единый янтарь вместо синего хардкода по маркам) =====
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fuelTypes.forEach { type ->
            val isSelected = selectedFuelTypes.contains(type)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) FueldeckColors.Amber else FueldeckColors.Surface,
                border = if (isSelected) null else BorderStroke(1.dp, FueldeckColors.Line),
                modifier = Modifier.clickable { onFuelTypeToggled(type) }
            ) {
                Text(
                    text = type,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isSelected) Color(0xFF1A1205) else FueldeckColors.InkDim,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ===== ПАНЕЛЬ СОРТИРОВКИ (активная = янтарь) =====
@Composable
fun SortBar(
    currentSort: MapViewModel.SortMode,
    onSortChange: (MapViewModel.SortMode) -> Unit
) {
    val sorts = listOf(
        MapViewModel.SortMode.BEST to "Лучшее",
        MapViewModel.SortMode.PRICE_ASC to "Дешевле",
        MapViewModel.SortMode.NEARBY to "Ближе",
        MapViewModel.SortMode.QUEUE to "Без очереди"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sorts.forEach { (mode, label) ->
            val isSelected = currentSort == mode
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) FueldeckColors.Amber else FueldeckColors.Surface,
                border = if (isSelected) null else BorderStroke(1.dp, FueldeckColors.Line),
                modifier = Modifier.clickable { onSortChange(mode) }
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (isSelected) Color(0xFF1A1205) else FueldeckColors.InkDim,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ===== ЭЛЕМЕНТ СПИСКА АЗС (цветной логотип с бликом + чипы/очередь в палитре) =====
@Composable
fun StationListItem(
    station: GasStation,
    selectedFuelTypes: Set<String>,
    userLocation: GeoPoint?,
    onClick: () -> Unit
) {
    val primaryFuel = station.fuelTypes.find { selectedFuelTypes.contains(it.type) && it.available }
    val distance = userLocation?.let {
        calculateDistance(it.latitude, it.longitude, station.latitude, station.longitude)
    }

    // Стабильный цвет бренда из палитры Fueldeck (детерминированно по имени)
    val brandColors = listOf(
        FueldeckColors.Teal, FueldeckColors.Amber, FueldeckColors.Coral,
        Color(0xFF2F7FD1), Color(0xFF7A6BD1), Color(0xFF2FAA55)
    )
    val brandColor = brandColors[Math.floorMod(station.brand.hashCode(), brandColors.size)]
    // Блик читаем в compose-фазе, иначе внутри drawBehind он не анимируется
    val logoShimmer = rememberInfiniteTransition(label = "logo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200), RepeatMode.Restart), label = "logo"
    )
    val logoSheen = logoShimmer.value

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = FueldeckColors.Surface
        ),
        border = BorderStroke(1.dp, FueldeckColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Логотип сети: цветной фон + белая буква + бегущий блик
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(brandColor, RoundedCornerShape(12.dp))
                    .drawBehind {
                        val w = size.width
                        val band = w * 0.5f
                        val x = -band + logoSheen * (w + band)
                        drawRect(
                            brush = Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                                start = Offset(x, 0f),
                                end = Offset(x + band, 0f)
                            ),
                            topLeft = Offset(x, 0f),
                            size = Size(band, size.height)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = station.brand.first().toString().uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.brand,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = FueldeckColors.Ink
                )
                Text(
                    text = station.address,
                    fontSize = 12.sp,
                    color = FueldeckColors.InkDim,
                    maxLines = 1
                )

                // Чипы цен по маркам: в наличии = бирюза, нет = серый
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    station.fuelTypes.filter { selectedFuelTypes.contains(it.type) }.forEach { fuel ->
                        val hot = fuel.available
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (hot) FueldeckColors.TealSoft else Color(0x0AFFFFFF),
                            border = BorderStroke(
                                1.dp,
                                if (hot) FueldeckColors.Teal.copy(alpha = 0.35f) else FueldeckColors.Line
                            )
                        ) {
                            Text(
                                text = if (hot) "${fuel.type} ${String.format("%.0f", fuel.price)}"
                                else "${fuel.type} нет",
                                fontSize = 10.sp,
                                color = if (hot) FueldeckColors.Teal else FueldeckColors.InkFaint,
                                fontWeight = FontWeight.Medium,
                                fontFamily = if (hot) FontFamily.Monospace else FontFamily.SansSerif,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Цена / расстояние / очередь
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                primaryFuel?.let { fuel ->
                    Text(
                        text = "${String.format("%.0f", fuel.price)} ₽",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = FueldeckColors.Amber
                    )
                } ?: Text(
                    text = "Нет топлива",
                    fontSize = 12.sp,
                    color = FueldeckColors.Coral
                )

                distance?.let { dist ->
                    Text(
                        text = "${String.format("%.1f", dist)} км",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = FueldeckColors.InkDim
                    )
                }

                if (station.queueTime > 0) {
                    val queueColor = when {
                        station.queueTime <= 5 -> FueldeckColors.Teal
                        station.queueTime <= 15 -> FueldeckColors.Amber
                        else -> FueldeckColors.Coral
                    }
                    Text(
                        text = "${station.queueTime} мин",
                        fontSize = 11.sp,
                        color = queueColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ===== КАРТОЧКА ДЕТАЛЕЙ АЗС (пока как была — оденем в палитру следующим шагом) =====
@Composable
fun StationDetailCard(
    station: GasStation,
    selectedFuelTypes: Set<String>,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.brand,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = station.address,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Text("✕", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Цены на топливо
            station.fuelTypes.forEach { fuel ->
                val isSelected = selectedFuelTypes.contains(fuel.type)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (fuel.available) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = fuel.type,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (fuel.available) "${String.format("%.2f", fuel.price)} ₽" else "Нет",
                        fontWeight = FontWeight.SemiBold,
                        color = if (fuel.available) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Очередь и рейтинг
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val queueColor = when {
                    station.queueTime <= 5 -> Color(0xFF4CAF50)
                    station.queueTime <= 15 -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = queueColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Очередь: ${station.queueTime} мин",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = queueColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Надёжность: ${station.reliability}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Кнопка маршрута
            Button(
                onClick = { openMapsRoute(context, station.latitude, station.longitude, station.brand) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Построить маршрут")
            }
        }
    }
}

// ===== ФУНКЦИЯ ОТКРЫТИЯ МАРШРУТА =====
private fun openMapsRoute(context: android.content.Context, lat: Double, lon: Double, label: String) {
    val uri = android.net.Uri.parse("https://maps.google.com/maps?daddr=$lat,$lon($label)")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        val browserUri = android.net.Uri.parse("https://maps.google.com/maps?daddr=$lat,$lon")
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, browserUri))
    }
}

// ===== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ (хвост, дописать в конец файла) =====

private fun getMarkerColor(station: GasStation, selectedFuelTypes: Set<String>): Color {
    val relevant = station.fuelTypes.filter { selectedFuelTypes.contains(it.type) }
    val available = relevant.filter { it.available }
    return when {
        relevant.isEmpty() -> FueldeckColors.InkFaint
        available.isEmpty() -> FueldeckColors.Coral
        station.queueTime > 15 -> FueldeckColors.Amber
        else -> FueldeckColors.Teal
    }
}

private fun buildStationSnippet(station: GasStation, selectedFuelTypes: Set<String>): String {
    val fuels = station.fuelTypes
        .filter { selectedFuelTypes.contains(it.type) && it.available }
        .joinToString(" | ") { "${it.type}: ${String.format("%.0f", it.price)}₽" }
    return if (fuels.isNotEmpty()) {
        "$fuels | Очередь: ${station.queueTime} мин"
    } else {
        "Нет выбранного топлива | Очередь: ${station.queueTime} мин"
    }
}

private fun createColoredMarker(context: android.content.Context, color: Color): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (28 * density).toInt()
    val strokePx = (3 * density).toInt()
    val drawable = android.graphics.drawable.GradientDrawable()
    drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
    drawable.setColor(color.toArgb())
    drawable.setStroke(strokePx, android.graphics.Color.WHITE)
    drawable.setSize(sizePx, sizePx)
    return drawable
}

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return R * c
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