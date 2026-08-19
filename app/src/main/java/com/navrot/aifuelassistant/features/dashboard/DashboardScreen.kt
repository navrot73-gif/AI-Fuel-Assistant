package com.navrot.aifuelassistant.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.ui.AddFuelRecordRoute
import com.navrot.aifuelassistant.ui.MapBuildRouteRoute
import com.navrot.aifuelassistant.ui.MapShowStationsRoute
import com.navrot.aifuelassistant.ui.map.components.LocationPermissionHandler
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.theme.FueldeckShapes
import com.navrot.aifuelassistant.util.Format
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    navController: NavController = rememberNavController()
) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val userQuestion by viewModel.userQuestion.collectAsStateWithLifecycle()
    val userAnswer by viewModel.userAnswer.collectAsStateWithLifecycle()
    val pendingRouteStationId by viewModel.pendingRouteStationId.collectAsStateWithLifecycle()
    val pendingRouteMode by viewModel.pendingRouteMode.collectAsStateWithLifecycle()
    val pendingOpenStationId by viewModel.pendingOpenStationId.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle(initialValue = emptyList())

    val consumption = metrics.consumption
    val efficiency = metrics.efficiency
    val rubPerKm = metrics.rubPerKm
    val isEmpty = metrics.fillCount == 0

    val chatListState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Location permission handler for location-aware AI
    LocationPermissionHandler(
        onLocationUpdate = { loc ->
            viewModel.updateUserLocation(loc.latitude, loc.longitude)
        },
        onPermissionDenied = { /* Location not available, AI will work without it */ }
    )

    // NEW LaunchedEffect for handling navigation based on pendingRouteMode
    LaunchedEffect(pendingRouteStationId, pendingRouteMode) {
        if (pendingRouteMode == DashboardViewModel.PendingRouteMode.NONE) {
            return@LaunchedEffect
        }
        delay(300) // дать MapScreen смонтироваться

        when (pendingRouteMode) {
            DashboardViewModel.PendingRouteMode.ROUTE -> {
                pendingRouteStationId?.let { id ->
                    navController.navigate(MapBuildRouteRoute(id))
                }
                viewModel.onRouteHandoffConsumed()
            }
            DashboardViewModel.PendingRouteMode.CARD -> {
                navController.navigate(MapShowStationsRoute)
                viewModel.onCardHandoffConsumed()
            }
            DashboardViewModel.PendingRouteMode.NONE -> {}
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            delay(100)
            if (chatListState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                chatListState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ===== 1. ШАПКА =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "AI‑помощник",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FueldeckColors.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "анализ стиля вождения и топлива",
                    fontSize = 12.sp,
                    color = FueldeckColors.InkDim,
                    letterSpacing = 0.4.sp,
                )
            }
            if (chatMessages.isNotEmpty()) {
                IconButton(onClick = {
                    if (vehicles.size == 1) {
                        navController.navigate(AddFuelRecordRoute(vehicles[0].id, vehicles[0].name))
                    } else {
                        navController.navigate("garage")
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить заправку",
                         tint = Color(0xFFE8A750))
                }
                IconButton(onClick = { viewModel.clearChatHistory() }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Очистить историю",
                        tint = FueldeckColors.Coral,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ===== 2. ЧИПЫ АВТО =====
        VehicleChipsRow(
            vehicles = vehicles,
            selectedId = selectedVehicleId,
            onSelect = { viewModel.selectVehicle(it) }
        )

        // ===== 3. МЕТРИКИ =====
        MetricsSection(
            fillCount = metrics.fillCount,
            consumption = consumption,
            rubPerKm = rubPerKm,
            efficiency = efficiency,
            onAddFuelRecord = {
                if (vehicles.size == 1) {
                    navController.navigate(AddFuelRecordRoute(vehicles[0].id, vehicles[0].name))
                } else {
                    navController.navigate("garage")
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Show route button if there's a pending route station
        pendingRouteStationId?.let { stationId ->
            Button(
                onClick = {
                    navController.navigate(MapBuildRouteRoute(stationId))
                    viewModel.onRouteHandoffConsumed()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8A750),
                    contentColor = Color(0xFF1A1205),
                ),
                shape = FueldeckShapes.Lg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("🗺️ Показать маршрут на карте", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }

        error?.let {
            Text(
                it,
                color = FueldeckColors.Coral,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // ===== 4. ЧАТ =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            if (chatMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Спросите про топливо или ваш автомобиль",
                        color = FueldeckColors.InkDim,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatMessages) { message ->
                        ChatBubble(message = message)
                    }
                }
            }
        }

        // ===== 5. ВВОД СНИЗУ =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = userQuestion,
                onValueChange = { viewModel.setUserQuestion(it) },
                placeholder = {
                    Text(
                        "Ваш вопрос…",
                        color = FueldeckColors.InkDim,
                        fontSize = 14.sp
                    )
                },
                enabled = !isAnalyzing,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (!isAnalyzing && userQuestion.isNotBlank()) {
                        keyboardController?.hide()
                        viewModel.askUserQuestion()
                        viewModel.setUserQuestion("")
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE8A750),
                    unfocusedBorderColor = Color(0xFF3A4650),
                    focusedContainerColor = FueldeckColors.Surface,
                    unfocusedContainerColor = FueldeckColors.Surface,
                    focusedTextColor = FueldeckColors.Ink,
                    unfocusedTextColor = FueldeckColors.Ink,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    if (userQuestion.isBlank() || isAnalyzing) return@Button
                    keyboardController?.hide()
                    viewModel.askUserQuestion()
                    viewModel.setUserQuestion("")
                },
                enabled = true,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp,
                    hoveredElevation = 6.dp,
                    focusedElevation = 4.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8A750),
                    contentColor = Color(0xFF1A1205),
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF1A1205),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Думаю…",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Спросить",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Спросить",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleChipsRow(
    vehicles: List<VehicleEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp), // Original height was 40dp, new chip height will be 36dp inside
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(vehicles) { v ->
            val isSelected = v.id == selectedId
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(v.id) },
                label = {
                    Text(
                        "${v.name} ${v.year}", // Changed label to include year
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFE8A750), // Selected background color
                    selectedLabelColor = Color(0xFF1A1205), // Selected text color (dark)
                    containerColor = FueldeckColors.Surface, // Unselected background color
                    labelColor = FueldeckColors.Ink // Unselected text color
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) Color(0xFFE8A750) else FueldeckColors.Line, // Border color
                    selectedBorderColor = Color(0xFFE8A750)
                ),
                shape = RoundedCornerShape(18.dp), // Changed corner radius
                modifier = Modifier.height(36.dp) // Changed height
            )
        }
    }
}

@Composable
private fun MetricsSection(
    fillCount: Int,
    consumption: Float,
    rubPerKm: Float,
    efficiency: Int,
    onAddFuelRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (fillCount == 0) {
        Card(
            onClick = onAddFuelRecord,
            colors = CardDefaults.cardColors(containerColor = FueldeckColors.Surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, FueldeckColors.Line),
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Добавьте заправки для анализа",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE8A750)
                )
            }
        }
    } else {
        val consumptionStr = if (fillCount < 2) "—" else Format.number(consumption, 1)
        val rubPerKmStr = if (fillCount < 2) "—" else Format.number(rubPerKm, 2)
        val efficiencyStr = if (fillCount < 2) "—" else "$efficiency%"

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                value = consumptionStr,
                subtitle = "Расход л/100км",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                value = rubPerKmStr,
                subtitle = "Стоимость ₽/км",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                value = efficiencyStr,
                subtitle = "Эффективность",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FueldeckColors.Surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FueldeckColors.Line),
        modifier = modifier.height(72.dp) // Changed height
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 16.sp, // Changed font size
                fontWeight = FontWeight.Bold,
                color = FueldeckColors.Ink,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp, // Changed font size
                color = FueldeckColors.InkDim, // Grey color
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFFE8A750) else FueldeckColors.Surface,
            shape = RoundedCornerShape(16.dp),
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, FueldeckColors.Line) else null,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = if (isUser) Color(0xFF1A1205) else FueldeckColors.Ink,
                    lineHeight = 19.6.sp // fontSize 14sp * 1.4
                )
            }
        }
    }
}
