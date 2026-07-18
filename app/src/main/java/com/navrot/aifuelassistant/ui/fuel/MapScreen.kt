package com.navrot.aifuelassistant.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp  // <-- ЭТОТ ИМПОРТ
import androidx.compose.ui.viewinterop.AndroidView
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
    viewModel: FuelRecordViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FuelRecordViewModelFactory(vehicleId)
    )
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()

    // Инициализация osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().osmdroidBasePath = File(context.cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карта заправок: $vehicleName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Карта
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)

                        // Центрируем на первой заправке с координатами или на Москве
                        val firstValidRecord = records.find { it.latitude != null && it.longitude != null }
                        val startPoint = if (firstValidRecord != null) {
                            GeoPoint(firstValidRecord.latitude!!, firstValidRecord.longitude!!)
                        } else {
                            GeoPoint(55.7558, 37.6173) // Москва по умолчанию
                        }

                        controller.setZoom(10.0)
                        controller.setCenter(startPoint)

                        // Добавляем маркеры для всех заправок с координатами
                        records.forEach { record ->
                            if (record.latitude != null && record.longitude != null) {
                                val marker = Marker(this)
                                marker.position = GeoPoint(record.latitude, record.longitude)
                                marker.title = "${record.stationName.ifBlank { "АЗС" }} — ${record.fuelAmount} л"
                                marker.snippet = "${record.pricePerLiter} ₽/л | ${record.totalCost} ₽"
                                overlays.add(marker)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Показываем сообщение, если нет заправок с координатами
            if (records.none { it.latitude != null && it.longitude != null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "Нет заправок с координатами.\nДобавьте заправку с включённым GPS.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}