package com.navrot.aifuelassistant.ui.fuel

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.navrot.aifuelassistant.data.VehicleCatalog
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFuelRecordScreen(
    vehicleId: Long,
    defaultFuelType: String,
    onBack: () -> Unit,
    viewModel: FuelRecordViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FuelRecordViewModelFactory(vehicleId)
    )
) {
    val context = LocalContext.current

    var mileage by remember { mutableStateOf("") }
    var fuelAmount by remember { mutableStateOf("") }
    var pricePerLiter by remember { mutableStateOf("") }
    var selectedFuelType by remember { mutableStateOf(defaultFuelType) }
    var stationName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // GPS координаты
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var gpsStatus by remember { mutableStateOf("Нажмите для определения местоположения") }

    var fuelTypeExpanded by remember { mutableStateOf(false) }

    // Launcher для запроса разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Точное местоположение разрешено
                getLocation(context) { loc ->
                    latitude = loc.latitude
                    longitude = loc.longitude
                    gpsStatus = "📍 GPS: ${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}"
                }
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Приблизительное местоположение разрешено
                getLocation(context) { loc ->
                    latitude = loc.latitude
                    longitude = loc.longitude
                    gpsStatus = "📍 GPS (приблизительно): ${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}"
                }
            }
            else -> {
                gpsStatus = "❌ Разрешение на геолокацию отклонено"
            }
        }
    }

    // Автоматический расчёт totalCost
    val totalCost = remember(fuelAmount, pricePerLiter) {
        val amount = fuelAmount.toDoubleOrNull() ?: 0.0
        val price = pricePerLiter.toDoubleOrNull() ?: 0.0
        amount * price
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить заправку") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Пробег
            OutlinedTextField(
                value = mileage,
                onValueChange = { mileage = it },
                label = { Text("Пробег (км)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Количество литров
            OutlinedTextField(
                value = fuelAmount,
                onValueChange = { fuelAmount = it },
                label = { Text("Количество литров") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Цена за литр
            OutlinedTextField(
                value = pricePerLiter,
                onValueChange = { pricePerLiter = it },
                label = { Text("Цена за литр (₽)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Итоговая стоимость (автоматически)
            OutlinedTextField(
                value = String.format("%.2f", totalCost),
                onValueChange = {},
                readOnly = true,
                label = { Text("Итого (₽)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Тип топлива
            ExposedDropdownMenuBox(
                expanded = fuelTypeExpanded,
                onExpandedChange = { fuelTypeExpanded = !fuelTypeExpanded }
            ) {
                OutlinedTextField(
                    value = selectedFuelType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип топлива") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelTypeExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = fuelTypeExpanded,
                    onDismissRequest = { fuelTypeExpanded = false }
                ) {
                    VehicleCatalog.fuelTypes.forEach { fuelType ->
                        DropdownMenuItem(
                            text = { Text(fuelType) },
                            onClick = {
                                selectedFuelType = fuelType
                                fuelTypeExpanded = false
                            }
                        )
                    }
                }
            }

            // Название АЗС
            OutlinedTextField(
                value = stationName,
                onValueChange = { stationName = it },
                label = { Text("Название АЗС (необязательно)") },
                placeholder = { Text("Например: Лукойл") },
                modifier = Modifier.fillMaxWidth()
            )

            // Кнопка определения GPS
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when {
                        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                            getLocation(context) { loc ->
                                latitude = loc.latitude
                                longitude = loc.longitude
                                gpsStatus = "📍 GPS: ${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}"
                            }
                        }
                        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                            getLocation(context) { loc ->
                                latitude = loc.latitude
                                longitude = loc.longitude
                                gpsStatus = "📍 GPS (приблизительно): ${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}"
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
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "GPS",
                        tint = if (latitude != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = gpsStatus,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Примечания
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Примечания (необязательно)") },
                placeholder = { Text("Например: Полный бак") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (mileage.isNotBlank() && fuelAmount.isNotBlank() && pricePerLiter.isNotBlank()) {
                        viewModel.addRecord(
                            FuelRecordEntity(
                                vehicleId = vehicleId,
                                date = System.currentTimeMillis(),
                                mileage = mileage.toDoubleOrNull() ?: 0.0,
                                fuelAmount = fuelAmount.toDoubleOrNull() ?: 0.0,
                                pricePerLiter = pricePerLiter.toDoubleOrNull() ?: 0.0,
                                totalCost = totalCost,
                                fuelType = selectedFuelType,
                                stationName = stationName,
                                notes = notes,
                                latitude = latitude,
                                longitude = longitude
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = mileage.isNotBlank() && fuelAmount.isNotBlank() && pricePerLiter.isNotBlank()
            ) {
                Text("Сохранить")
            }
        }
    }
}

// Функция получения местоположения
private fun getLocation(context: android.content.Context, onLocation: (Location) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    onLocation(location)
                }
            }
            .addOnFailureListener {
                // Можно показать Toast с ошибкой
            }
    } catch (e: SecurityException) {
        // Разрешение не предоставлено
    }
}