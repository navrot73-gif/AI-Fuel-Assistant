package com.navrot.aifuelassistant.ui.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.ui.components.VehicleCard
import com.navrot.aifuelassistant.ui.components.VehicleCardUiState
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    onAddClick: () -> Unit,
    onVehicleClick: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel()
) {
    val vehicles by viewModel.vehiclesState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Мои автомобили") }) },
        containerColor = FueldeckColors.Bg1,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = FueldeckColors.Amber,
                contentColor = Color(0xFF1A1205),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить автомобиль")
            }
        },
    ) { padding ->
        if (vehicles.isEmpty()) {
            Box(
                modifier = modifier.padding(padding).fillMaxSize().background(FueldeckColors.Bg1),
                contentAlignment = Alignment.Center,
            ) {
                Text("Список автомобилей пуст", color = FueldeckColors.InkDim)
            }
        } else {
            LazyColumn(
                modifier = modifier.padding(padding).fillMaxSize().background(FueldeckColors.Bg1),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(vehicles) { vehicle ->
                    VehicleCard(
                        state = vehicle.toUiState(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVehicleClick(vehicle.id, vehicle.name) },
                    )
                }
            }
        }
    }
}

private fun VehicleEntity.toUiState(): VehicleCardUiState {
    val modelLine = listOf(brand, model)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .let { if (it.isBlank()) "—" else "$it · $year" }

    return VehicleCardUiState(
        name = name.ifBlank { "Без названия" },
        modelLine = modelLine,
        fuelGrade = fuelType.ifBlank { "—" },
        tankLiters = tankCapacity.toInt(),
        mileageText = String.format("%.1f", currentMileage / 1000.0),
        fillPercent = 0,
        rangeKm = 0,
        consumptionText = "—",
        fillCount = 0,
        bars = emptyList(),
        toKmLeft = 0,
        toPercent = 0,
        lastFillDate = "—",
        lastFillLiters = "—",
        lastFillBrand = "—",
        lastFillPrice = "—",
    )
}