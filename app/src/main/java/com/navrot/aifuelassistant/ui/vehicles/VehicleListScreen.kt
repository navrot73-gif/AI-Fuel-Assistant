package com.navrot.aifuelassistant.ui.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navrot.aifuelassistant.FuelApplication
import com.navrot.aifuelassistant.data.VehicleRepositoryImpl
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    onAddClick: () -> Unit,
    onVehicleClick: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // ИСПРАВЛЕНО: Правильная инициализация ViewModel через фабрику с репозиторием
    val viewModel: VehicleViewModel = viewModel(
        factory = VehicleViewModelFactory(
            repository = VehicleRepositoryImpl(FuelApplication.instance.database.vehicleDao())
        )
    )

    // ИСПРАВЛЕНО: Вызываем vehiclesState вместо несуществующего vehicles
    val vehicles by viewModel.vehiclesState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Мои автомобили") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Text("+")
            }
        }
    ) { padding ->
        if (vehicles.isEmpty()) {
            Box(
                modifier = modifier.padding(padding).fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("Список автомобилей пуст")
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vehicles) { vehicle ->
                    VehicleItem(
                        vehicle = vehicle,
                        onClick = {
                            // ИСПРАВЛЕНО: передаем id и name из сущности VehicleEntity
                            onVehicleClick(vehicle.id, vehicle.name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VehicleItem(
    vehicle: VehicleEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = vehicle.name, style = MaterialTheme.typography.titleLarge)
            Text(text = "${vehicle.brand} ${vehicle.model} (${vehicle.year})", style = MaterialTheme.typography.bodyMedium)
        }
    }
}