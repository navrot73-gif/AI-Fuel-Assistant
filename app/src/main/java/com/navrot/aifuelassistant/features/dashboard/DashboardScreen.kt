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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.navrot.aifuelassistant.ui.components.ConsumptionGauge
import com.navrot.aifuelassistant.ui.components.Sparkline
import com.navrot.aifuelassistant.ui.map.UserLocationState
import com.navrot.aifuelassistant.ui.map.components.LocationPermissionHandler
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.theme.FueldeckShapes

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
    var expanded by remember { mutableStateOf(false) }

    val consumption = metrics.consumption
    val efficiency = metrics.efficiency
    val rubPerKm = metrics.rubPerKm
    val spark = metrics.sparklineData
    val isEmpty = metrics.fillCount == 0
    val hasSparkline = spark.size >= 2

    // Location permission handler for location-aware AI
    LocationPermissionHandler(
        onLocationUpdate = { loc ->
            viewModel.updateUserLocation(loc.latitude, loc.longitude)
        },
        onPermissionDenied = { /* Location not available, AI will work without it */ }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ===== Шапка =====
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

        // ===== Селектор автомобиля =====
        Surface(
            shape = FueldeckShapes.Pill,
            color = FueldeckColors.Surface,
            border = BorderStroke(1.dp, FueldeckColors.Line),
            modifier = Modifier.clickable { expanded = !expanded }
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

        // ===== Свободный вопрос (сразу виден без скролла) =====
        Panel(modifier = Modifier.fillMaxWidth()) {
            Text("Задать AI любой вопрос", fontSize = 11.sp, color = FueldeckColors.InkFaint,
                letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = userQuestion,
                onValueChange = { viewModel.setUserQuestion(it) },
                placeholder = { Text("Например: какая АЗС дешевле рядом?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 4,
            )
            Spacer(Modifier.height(10.dp))
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
                Icon(Icons.Default.Send, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Спросить", fontWeight = FontWeight.SemiBold)
            }
        }

        // ===== Ответ на свободный вопрос =====
        userAnswer?.let { answer ->
            Surface(
                shape = FueldeckShapes.Md,
                color = FueldeckColors.Surface,
                border = BorderStroke(1.dp, FueldeckColors.Line),
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(3.dp)
                            .background(FueldeckColors.Amber)
                    )
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("AI ответил", fontSize = 11.sp, color = FueldeckColors.Amber,
                            letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(answer, fontSize = 13.5.sp, color = FueldeckColors.Ink,
                            lineHeight = 19.sp)
                    }
                }
            }
        }

        // ===== Метрики =====
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel(modifier = Modifier.weight(1.15f)) {
                ConsumptionGauge(value = consumption)
            }
            Column(
                modifier = Modifier.weight(0.85f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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

        // ===== График расхода =====
        Panel(modifier = Modifier.fillMaxWidth()) {
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
            Text(it, color = FueldeckColors.Coral, fontSize = 13.sp)
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
            modifier = Modifier.fillMaxWidth().height(54.dp),
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
    }
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
