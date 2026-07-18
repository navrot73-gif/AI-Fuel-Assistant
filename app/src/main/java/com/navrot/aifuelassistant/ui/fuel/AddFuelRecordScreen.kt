package com.navrot.aifuelassistant.ui.fuel

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.navrot.aifuelassistant.data.VehicleCatalog
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFuelRecordScreen(
    vehicleId: Long,
    defaultFuelType: String,
    onBack: () -> Unit,
    viewModel: FuelRecordViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FuelRecordViewModelFactory(
            vehicleId,
            com.navrot.aifuelassistant.app.FuelApplication.instance.database.fuelRecordDao()
        )
    )
) {
    val context = LocalContext.current

    var mileage by remember { mutableStateOf("") }
    var fuelAmount by remember { mutableStateOf("") }
    var pricePerLiter by remember { mutableStateOf("") }
    var selectedFuelType by remember { mutableStateOf(defaultFuelType) }
    var stationName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var gpsStatus by remember { mutableStateOf("Нажмите для определения местоположения") }

    var fuelTypeExpanded by remember { mutableStateOf(false) }

    val totalCost = remember(fuelAmount, pricePerLiter) {
        val amount = fuelAmount.toDoubleOrNull() ?: 0.0
        val price = pricePerLiter.toDoubleOrNull() ?: 0.0
        amount * price
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getLocation(context) { loc ->
                    latitude = loc.latitude
                    longitude = loc.longitude
                    gpsStatus = "📍 GPS: ${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}"
                }
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
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
            OutlinedTextField(
                value = mileage,
                onValueChange = { mileage = it },
                label = { Text("Пробег (км)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = fuelAmount,
                onValueChange = { fuelAmount = it },
                label = { Text("Количество литров") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = pricePerLiter,
                onValueChange = { pricePerLiter = it },
                label = { Text("Цена за литр (₽)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = String.format("%.2f", totalCost),
                onValueChange = {},
                readOnly = true,
                label = { Text("Итого (₽)") },
                modifier = Modifier.fillMaxWidth()
            )

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

            OutlinedTextField(
                value = stationName,
                onValueChange = { stationName = it },
                label = { Text("Название АЗС (необязательно)") },
                placeholder = { Text("Например: Лукойл") },
                modifier = Modifier.fillMaxWidth()
            )

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
                    verticalAlignment = Alignment.CenterVertically
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

private fun getLocation(context: android.content.Context, onLocation: (Location) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    onLocation(location)
                } else {
                    requestCurrentLocation(context, fusedLocationClient, onLocation)
                }
            }
            .addOnFailureListener {
                requestCurrentLocation(context, fusedLocationClient, onLocation)
            }
    } catch (e: SecurityException) {
        // Разрешение не предоставлено
    }
}

private fun requestCurrentLocation(
    context: android.content.Context,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocation: (Location) -> Unit
) {
    try {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocation(it) }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    } catch (e: SecurityException) {
        // Разрешение не предоставлено
    }
}