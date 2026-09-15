package com.navrot.aifuelassistant.ui.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.database.entity.toPersonalFuelEvent
import com.navrot.aifuelassistant.domain.personal.CalculatePersonalFuelStatisticsUseCase
import com.navrot.aifuelassistant.domain.personal.Period
import com.navrot.aifuelassistant.domain.predictive.ConsumptionPrediction
import com.navrot.aifuelassistant.domain.predictive.NextRefuelPrediction
import com.navrot.aifuelassistant.ui.components.VehicleCard
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicleId: Long,
    vehicleName: String,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel()
) {
    val vehicles by viewModel.vehiclesWithStats.collectAsStateWithLifecycle()
    val activeVehicleId by viewModel.activeVehicleId.collectAsStateWithLifecycle()
    val vehicleRecords by viewModel.getVehicleRecords(vehicleId).collectAsStateWithLifecycle(initialValue = emptyList())

    val vehicle = vehicles.find { it.id == vehicleId }
    val personalEvents = remember(vehicleRecords) { vehicleRecords.map { it.toPersonalFuelEvent() } }
    val stats = remember(personalEvents) {
        CalculatePersonalFuelStatisticsUseCase().execute(vehicleId, personalEvents, Period.THIRTY_DAYS)
    }

    val consumptionPrediction by produceState<ConsumptionPrediction?>(initialValue = null, vehicle, vehicleRecords) {
        val entity = viewModel.getVehicleEntity(vehicleId)
        if (entity != null) {
            value = viewModel.getConsumptionPrediction(entity, vehicleRecords)
        }
    }

    val nextRefuelPrediction by produceState<NextRefuelPrediction?>(initialValue = null, vehicle, vehicleRecords) {
        val entity = viewModel.getVehicleEntity(vehicleId)
        if (entity != null) {
            value = viewModel.getNextRefuelPrediction(entity, vehicleRecords)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "ВАШ ПАРК",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = FueldeckColors.Mint,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Start,
                            )
                            Text(
                                text = vehicleName,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = FueldeckColors.Ink,
                                textAlign = TextAlign.Start,
                            )
                        }
                        Text(
                            text = "${vehicles.size} авто",
                            fontSize = 14.sp,
                            color = Color(0xFF8A949E),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditClick(vehicleId) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Редактировать автомобиль",
                            tint = FueldeckColors.Ink
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = FueldeckColors.Bg1,
                ),
            )
        },
        containerColor = FueldeckColors.Bg1,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = FueldeckColors.Amber,
                contentColor = Color(0xFF1A1205),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить заправку")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (vehicle != null) {
                val isActive = activeVehicleId == vehicle.id
                VehicleCard(
                    state = vehicle,
                    isActive = isActive,
                    modifier = Modifier.fillMaxWidth()
                )

                // Phase G Prediction & Learning Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = FueldeckColors.Surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ПЕРСОНАЛЬНЫЙ ПРОГНОЗ",
                            style = MaterialTheme.typography.titleMedium,
                            color = FueldeckColors.Mint
                        )

                        val pred = consumptionPrediction
                        if (pred?.predictedConsumption != null) {
                            Text(
                                text = "Ваш прогноз: ${Format.number(pred.predictedConsumption, 1)} л/100 км",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FueldeckColors.Ink
                            )
                            Text(
                                text = "Точность прогноза: ${pred.confidence.toUserLabel()}",
                                fontSize = 13.sp,
                                color = FueldeckColors.Mint
                            )
                            Text(
                                text = pred.basedOn,
                                fontSize = 12.sp,
                                color = Color(0xFF8A949E)
                            )
                            nextRefuelPrediction?.explanation?.let { refuelText ->
                                Text(
                                    text = refuelText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FueldeckColors.Amber
                                )
                            }
                        } else {
                            Text(
                                text = "Прогноз пока недоступен. Добавьте ещё несколько заправок.",
                                fontSize = 13.sp,
                                color = FueldeckColors.InkDim
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.resetPersonalLearning(vehicleId) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6F61)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3DFF6F61)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Сбросить персональное обучение", fontSize = 12.sp)
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = FueldeckColors.Surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ЗА 30 ДНЕЙ", style = MaterialTheme.typography.titleMedium, color = FueldeckColors.Mint)
                        if (stats.refuelCount == 0) {
                            Text("Пока недостаточно данных", color = FueldeckColors.InkDim)
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Расход:", color = FueldeckColors.Ink)
                                Text(
                                    text = if (stats.averageConsumption != null) "${Format.number(stats.averageConsumption, 1)} л/100 км" else "Недостаточно данных",
                                    fontWeight = FontWeight.Bold,
                                    color = FueldeckColors.Ink
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Топливо:", color = FueldeckColors.Ink)
                                Text(
                                    text = if (stats.totalLiters != null) "${Format.number(stats.totalLiters, 1)} л" else "—",
                                    color = FueldeckColors.Ink
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Потрачено:", color = FueldeckColors.Ink)
                                Text(
                                    text = if (stats.totalFuelCost != null) Format.price(stats.totalFuelCost) else "—",
                                    color = FueldeckColors.Ink
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Заправок:", color = FueldeckColors.Ink)
                                Text(text = "${stats.refuelCount}", color = FueldeckColors.Ink)
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = modifier.fillMaxSize().background(FueldeckColors.Bg1),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Автомобиль не найден", color = FueldeckColors.InkDim)
                }
            }
        }
    }
}
