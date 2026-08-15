package com.navrot.aifuelassistant.features.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.navrot.aifuelassistant.ui.components.ConsumptionGauge
import com.navrot.aifuelassistant.ui.components.Sparkline
import com.navrot.aifuelassistant.ui.map.UserLocationState
import com.navrot.aifuelassistant.ui.map.components.LocationPermissionHandler
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.theme.FueldeckShapes
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
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
    val navController = rememberNavController()
    var expanded by remember { mutableStateOf(false) }
    var badgesExpanded by remember { mutableStateOf(true) }

    val consumption = metrics.consumption
    val efficiency = metrics.efficiency
    val rubPerKm = metrics.rubPerKm
    val spark = metrics.sparklineData
    val isEmpty = metrics.fillCount == 0
    val hasSparkline = spark.size >= 2

    // Chat scroll state for auto-scroll
    val chatListState = rememberLazyListState()
    val chatMessagesList = chatMessages.toList() // already collected as list

    // Location permission handler for location-aware AI
    LocationPermissionHandler(
        onLocationUpdate = { loc ->
            viewModel.updateUserLocation(loc.latitude, loc.longitude)
        },
        onPermissionDenied = { /* Location not available, AI will work without it */ }
    )

    // Auto-navigate to map after AI answer based on mode
    LaunchedEffect(pendingRouteMode, pendingRouteStationId, pendingOpenStationId, userAnswer) {
        when (pendingRouteMode) {
            DashboardViewModel.PendingRouteMode.ROUTE -> {
                pendingRouteStationId?.let { stationId ->
                    navController.navigate("map/build_route_station_id/$stationId")
                    userAnswer?.let { answer ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("ai_answer_text", answer)
                    }
                    viewModel.onRouteHandoffConsumed()
                }
            }
            DashboardViewModel.PendingRouteMode.CARD -> {
                pendingOpenStationId?.let { stationId ->
                    navController.navigate("map")
                    navController.previousBackStackEntry?.savedStateHandle?.set("open_station_id", stationId)
                    userAnswer?.let { answer ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("ai_answer_text", answer)
                    }
                    viewModel.onCardHandoffConsumed()
                }
            }
            else -> { /* stay on AI screen */ }
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size) {
        delay(100) // Wait for layout
        if (chatListState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ===== Шапка с кнопкой очистки истории =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                Spacer(Modifier.height(4.dp))
                Text(
                    "анализ стиля вождения и топлива",
                    fontSize = 12.sp,
                    color = FueldeckColors.InkDim,
                    letterSpacing = 0.4.sp,
                )
            }
            // Clear history button
            if (chatMessages.isNotEmpty()) {
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

        // ===== Селектор автомобиля =====
        Surface(
            shape = FueldeckShapes.Pill,
            color = FueldeckColors.Surface,
            border = BorderStroke(1.dp, FueldeckColors.Line),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { expanded = !expanded }
        ) {
            Box {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(7.dp).background(FueldeckColors.Amber, CircleShape))
                    Text(
                        vehicles.find { it.id == selectedVehicleId }?.name ?: "Выберите авто",
                        fontSize = 13.sp,
                        color = FueldeckColors.Ink
                    )
                    Text(
                        if (expanded) "▴" else "▾",
                        fontSize = 11.sp,
                        color = FueldeckColors.InkDim
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = { Text(vehicle.name) },
                            onClick = {
                                viewModel.selectVehicle(vehicle.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // ===== Гейджи расхода (сворачиваемый блок) =====
        if (badgesExpanded) {
            Surface(
                color = FueldeckColors.Surface,
                shape = FueldeckShapes.Lg,
                border = BorderStroke(1.dp, FueldeckColors.Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Метрики", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FueldeckColors.Ink)
                        IconButton(onClick = { badgesExpanded = false }) {
                            Icon(Icons.Default.ExpandLess, contentDescription = "Свернуть", tint = FueldeckColors.InkDim, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Consumption gauge
                        Panel(modifier = Modifier.weight(1f)) {
                            ConsumptionGauge(value = consumption)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Panel(modifier = Modifier.weight(1f)) {
                                Text("эффективность", fontSize = 11.sp, color = FueldeckColors.InkFaint,
                                    letterSpacing = 1.2.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                MiniRing(value = efficiency)
                            }
                            Panel(modifier = Modifier.weight(1f)) {
                                Text("расход ₽/км", fontSize = 11.sp, color = FueldeckColors.InkFaint,
                                    letterSpacing = 1.2.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        if (isEmpty) "—" else String.format("%.2f", rubPerKm),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        color = FueldeckColors.Ink,
                                    )
                                    Text(" ₽", fontSize = 11.sp, color = FueldeckColors.InkFaint)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Collapsed badge bar
            Surface(
                color = FueldeckColors.Surface,
                shape = FueldeckShapes.Lg,
                border = BorderStroke(1.dp, FueldeckColors.Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { badgesExpanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📊 ", fontSize = 12.sp)
                        Text("расход: ${if (isEmpty) "—" else "%.1f".format(consumption)} л/100км", fontSize = 12.sp, color = FueldeckColors.Ink)
                        Text("•", fontSize = 12.sp, color = FueldeckColors.InkDim)
                        Text("эфф.: $efficiency%", fontSize = 12.sp, color = FueldeckColors.Ink)
                        Text("•", fontSize = 12.sp, color = FueldeckColors.InkDim)
                        Text("₽/км: ${if (isEmpty) "—" else "%.2f".format(rubPerKm)}", fontSize = 12.sp, color = FueldeckColors.Ink)
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = "Развернуть", tint = FueldeckColors.InkDim, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ===== Чат история (LazyColumn) =====
        if (chatMessages.isNotEmpty()) {
            Surface(
                color = FueldeckColors.Surface,
                shape = FueldeckShapes.Lg,
                border = BorderStroke(1.dp, FueldeckColors.Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f, fill = false)
                    .height(300.dp) // Fixed height for chat area
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = chatListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = false
                ) {
                    items(chatMessages) { message ->
                        ChatBubble(message = message)
                    }
                }
            }
        }

        // ===== График расхода =====
        Panel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("расход · 30 дней", fontSize = 11.sp, color = FueldeckColors.InkFaint,
                    letterSpacing = 1.2.sp)
                Text(
                    if (isEmpty) "нет данных" else "обновлено 4 мин назад",
                    fontSize = 11.sp, color = FueldeckColors.InkDim
                )
            }
            Spacer(Modifier.height(10.dp))
            if (isEmpty || !hasSparkline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isEmpty) "Добавьте заправки для отображения"
                        else "Недостаточно данных для графика",
                        fontSize = 12.sp,
                        color = FueldeckColors.InkDim
                    )
                }
            } else {
                Sparkline(data = spark)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("мин ${"%.1f".format(spark.min())}", fontSize = 11.5.sp, color = FueldeckColors.InkFaint)
                    Text("средн ${"%.1f".format(spark.average())}",
                        fontSize = 11.5.sp, color = FueldeckColors.InkFaint)
                    Text("макс ${"%.1f".format(spark.max())}", fontSize = 11.5.sp, color = FueldeckColors.InkFaint)
                }
            }
        }

        // ===== AI-анализ =====
        if (analysis == null && !isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, FueldeckColors.Line2, FueldeckShapes.Md)
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isEmpty) "Сначала добавьте автомобиль и заправки, затем я смогу проанализировать стиль вождения."
                    else "Нажмите ниже — проанализирую последние заправки и стиль вождения за месяц.",
                    fontSize = 13.5.sp,
                    color = FueldeckColors.InkDim,
                    textAlign = TextAlign.Center,
                )
            }
        }

        analysis?.let { text ->
            Surface(
                shape = FueldeckShapes.Md,
                color = FueldeckColors.Surface,
                border = BorderStroke(1.dp, FueldeckColors.Line),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(3.dp)
                            .background(FueldeckColors.Mint)
                    )
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("AI‑анализ", fontSize = 11.sp, color = FueldeckColors.Mint,
                            letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(text, fontSize = 13.5.sp, color = FueldeckColors.Ink,
                            lineHeight = 19.sp)
                    }
                }
            }
        }

        error?.let {
            Text(it, color = FueldeckColors.Coral, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // ===== Поле ввода + кнопка "Спросить" (фиксированно внизу) =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = userQuestion,
                onValueChange = { viewModel.setUserQuestion(it) },
                placeholder = { Text("Например: какая АЗС дешевле рядом?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 4,
                singleLine = false,
            )
            Button(
                onClick = { viewModel.askUserQuestion() },
                enabled = !isAnalyzing && userQuestion.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FueldeckColors.Mint,
                    contentColor = Color.White,
                    disabledContainerColor = FueldeckColors.Mint.copy(alpha = 0.4f),
                ),
                shape = FueldeckShapes.Md,
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Думаю...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Спросить", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ===== Кнопка AI-анализа =====
        Button(
            onClick = { viewModel.askAi() },
            enabled = !isAnalyzing && !isEmpty,
            colors = ButtonDefaults.buttonColors(
                containerColor = FueldeckColors.Amber,
                contentColor = Color(0xFF1A1205),
                disabledContainerColor = FueldeckColors.Amber.copy(alpha = 0.6f),
                disabledContentColor = Color(0xFF1A1205),
            ),
            shape = FueldeckShapes.Md,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF1A1205),
                )
                Spacer(Modifier.width(8.dp))
                Text("Анализирую...", fontWeight = FontWeight.SemiBold)
            } else {
                Text(
                    if (isEmpty) "Добавьте заправки для анализа"
                    else "Получить AI‑анализ",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp
                )
            }
        }

        // Show route button if there's a pending route station
        if (pendingRouteStationId != null) {
            Button(
                onClick = {
                    navController.navigate("map/build_route_station_id/$pendingRouteStationId")
                    viewModel.onRouteHandoffConsumed()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FueldeckColors.Amber,
                    contentColor = Color(0xFF1A1205),
                ),
                shape = FueldeckShapes.Lg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("🗺️ Показать маршрут на карте", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }

        // Bottom padding for navigation bar
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        // AI has amber accent bar on left
        if (!isUser) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(IntrinsicSize.Min)
                    .background(FueldeckColors.Amber)
                    .padding(top = 4.dp, bottom = 4.dp)
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .widthIn(max = 280.dp)
                .align(Alignment.CenterVertically)
        ) {
            // Bubble
            Surface(
                color = if (isUser) FueldeckColors.Amber else FueldeckColors.Surface2,
                shape = FueldeckShapes.Md,
                border = if (!isUser) BorderStroke(1.dp, FueldeckColors.Line) else BorderStroke(0.dp, Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        message.text,
                        fontSize = 13.5.sp,
                        color = if (isUser) Color(0xFF1A1205) else FueldeckColors.Ink,
                        lineHeight = 19.sp,
                    )
                }
            }
            // Timestamp
            Text(
                formatTimestamp(message.ts),
                fontSize = 10.sp,
                color = FueldeckColors.InkFaint,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
        // User has amber bubble on right (no accent bar needed since bubble is amber)
        if (isUser) {
            Spacer(Modifier.width(4.dp))
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    val date = java.util.Date(ts)
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(date)
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = FueldeckColors.Surface,
        shape = FueldeckShapes.Lg,
        border = BorderStroke(1.dp, FueldeckColors.Line),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun MiniRing(value: Int) {
    val progress by animateFloatAsState(
        targetValue = value / 100f,
        animationSpec = tween(1200),
        label = "mini",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = FueldeckColors.Line, startAngle = 135f, sweepAngle = 270f,
                useCenter = false, style = stroke)
            drawArc(color = FueldeckColors.Mint, startAngle = 135f,
                sweepAngle = 270f * progress, useCenter = false, style = stroke)
        }
        Text(
            "$value",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            color = FueldeckColors.Ink,
        )
    }
}