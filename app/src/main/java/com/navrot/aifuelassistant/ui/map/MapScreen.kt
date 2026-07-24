package com.navrot.aifuelassistant.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.navrot.aifuelassistant.FuelApplication
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

// Центр по умолчанию, пока GPS не определился
private val DEFAULT_CENTER = GeoPoint(55.7558, 37.6173)

@Composable
fun MapScreen() {
    val context = LocalContext.current

    // --- Данные заправок из Room (переиспользуем тот же путь, что в экранах гаража) ---
    val fuelDao = remember { FuelApplication.instance.database.fuelRecordDao() }
    val allRecords by fuelDao.getAll().collectAsState(initial = emptyList())
    // читаем в теле, чтобы recomposition срабатывал и карта перерисовывалась
    val locatedRecords = allRecords.filter { it.latitude != null && it.longitude != null }

    // --- Состояние GPS ---
    var myLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var accuracy by remember { mutableStateOf(0f) }
    var gpsStatus by remember { mutableStateOf("Определяем местоположение…") }
    var centered by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FuelRecordEntity?>(null) }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    myLocation = GeoPoint(loc.latitude, loc.longitude)
                    accuracy = loc.accuracy
                    gpsStatus = "GPS ✓ ±${loc.accuracy.toInt()} м"
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) startTracking(fused, locationCallback) { lat, lon, acc ->
            myLocation = GeoPoint(lat, lon); accuracy = acc
            gpsStatus = "GPS ✓ ±${acc.toInt()} м"
        } else gpsStatus = "Геолокация недоступна"
    }

    LaunchedEffect(Unit) {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            startTracking(fused, locationCallback) { lat, lon, acc ->
                myLocation = GeoPoint(lat, lon); accuracy = acc
                gpsStatus = "GPS ✓ ±${acc.toInt()} м"
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { fused.removeLocationUpdates(locationCallback) }
    }

    // --- Сама карта (OSMDroid внутри Compose) ---
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)            // зум двумя пальцами
            controller.setZoom(13.0)
            controller.setCenter(DEFAULT_CENTER)
        }
    }

    // центрируем на себе при первом определении
    LaunchedEffect(myLocation) {
        if (myLocation != null && !centered) {
            mapView.controller.setZoom(15.0)
            mapView.controller.animateTo(myLocation)
            centered = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                // чистим динамические оверлеи и ставим заново
                mv.overlays.removeAll { it is Marker || it is Polygon }

                // круг точности GPS
                val loc = myLocation
                if (loc != null) {
                    val ring = Polygon().apply {
                        points = circlePoints(loc, accuracy)
                        fillColor = 0x334A6BD8.toInt()
                        strokeColor = 0xFF4A6BD8.toInt()
                        strokeWidth = 2f
                    }
                    mv.overlays.add(ring)

                    val me = Marker(mv).apply {
                        position = loc
                        title = "Вы здесь"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mv.overlays.add(me)
                }

                // маркеры сохранённых заправок
                locatedRecords.forEach { rec ->
                    val gp = GeoPoint(rec.latitude!!, rec.longitude!!)
                    val m = Marker(mv).apply {
                        position = gp
                        title = rec.stationName.ifBlank { "Заправка" }
                        snippet = "${rec.fuelType} · ${rec.pricePerLiter} ₽/л"
                        setOnMarkerClickListener { _, _ ->
                            selected = rec
                            true
                        }
                    }
                    mv.overlays.add(m)
                }

                mv.invalidate()
            }
        )

        // --- Чип статуса GPS (живой фидбек сверху) ---
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (myLocation != null)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = gpsStatus, fontSize = 13.sp)
            }
        }

        // --- FAB: центрирование на мне ---
        FloatingActionButton(
            onClick = {
                myLocation?.let {
                    mapView.controller.setZoom(16.0)
                    mapView.controller.animateTo(it)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Моё местоположение")
        }

        // --- Подсказка, если заправок пока нет ---
        if (locatedRecords.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "Пока нет сохранённых заправок.\nДобавьте первую во вкладке «Гараж» — " +
                            "и она появится на карте.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // --- Карточка выбранной заправки (анимация снизу) ---
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            val rec = selected
            if (rec != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rec.stationName.ifBlank { "Заправка" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { selected = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        }
                        Text(
                            text = "${rec.fuelType} · ${rec.pricePerLiter} ₽/л · ${rec.fuelAmount} л",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Итого ${rec.totalCost} ₽",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --- GPS: последнийknown + подписка на обновления ---
private fun startTracking(
    fused: com.google.android.gms.location.FusedLocationProviderClient,
    callback: LocationCallback,
    onFix: (lat: Double, lon: Double, acc: Float) -> Unit
) {
    try {
        fused.lastLocation.addOnSuccessListener { loc ->
            loc?.let { onFix(it.latitude, it.longitude, it.accuracy) }
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()
        fused.requestLocationUpdates(req, callback, android.os.Looper.getMainLooper())
    } catch (_: SecurityException) {
        // разрешение не дано
    }
}

// --- Точки окружности для круга точности ---
private fun circlePoints(center: GeoPoint, radiusM: Float): List<GeoPoint> {
    val r = radiusM.coerceIn(40f, 4000f).toDouble()
    return (0 until 360 step 15).map { deg ->
        val rad = Math.toRadians(deg.toDouble())
        val dLat = r * Math.cos(rad) / 111_320.0
        val dLon = r * Math.sin(rad) / (111_320.0 * Math.cos(Math.toRadians(center.latitude)))
        GeoPoint(center.latitude + dLat, center.longitude + dLon)
    }
}