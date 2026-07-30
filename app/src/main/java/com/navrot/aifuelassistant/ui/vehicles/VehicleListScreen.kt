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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navrot.aifuelassistant.FuelApplication
import com.navrot.aifuelassistant.data.VehicleRepositoryImpl
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.ui.components.VehicleCard
import com.navrot.aifuelassistant.ui.components.VehicleCardUiState
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    onAddClick: () -> Unit,
    onVehicleClick: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: VehicleViewModel = viewModel(
        factory = VehicleViewModelFactory(
            repository = VehicleRepositoryImpl(FuelApplication.instance.database.vehicleDao())
        )
    )
    val vehicles by viewModel.vehiclesState.collectAsState()

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
                // bottom = 96.dp даёт запас под янтарный FAB, чтобы он не
                // перекрывал последнюю карточку («последняя заправка»).
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

/** Реальные данные авто — из VehicleEntity. Телеметрия расхода (помечена TODO)
 *  подключится из FuelRecordEntity следующим шагом. */
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

        // --- телеметрия расхода: подключим из FuelRecordEntity ---
        fillPercent = 0,            // TODO: уровень бака по последней заправке
        rangeKm = 0,                // TODO: запас хода (fillPercent × средний расход)
        consumptionText = "—",      // TODO: средний расход л/100км
        fillCount = 0,              // TODO: число заправок (count по vehicleId)
        bars = emptyList(),         // TODO: расход по последним заправкам
        toKmLeft = 0,               // TODO: пробег до ТО
        toPercent = 0,              // TODO: прогресс до ТО
        lastFillDate = "—",         // TODO: дата последней заправки
        lastFillLiters = "—",       // TODO: литры последней заправки
        lastFillBrand = "—",        // TODO: сеть последней заправки
        lastFillPrice = "—",        // TODO: сумма последней заправки
    )
}