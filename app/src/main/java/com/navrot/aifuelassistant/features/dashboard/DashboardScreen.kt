package com.navrot.aifuelassistant.features.dashboard

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.navrot.aifuelassistant.ui.components.ConsumptionGauge
import com.navrot.aifuelassistant.ui.components.Sparkline
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.theme.FueldeckShapes

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FueldeckColors.Bg1)
            .statusBarsPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---------- topbar ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AI-ассистент",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FueldeckColors.Ink,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    "телеметрия расхода",
                    fontSize = 12.sp,
                    color = FueldeckColors.InkFaint,
                    letterSpacing = 0.4.sp,
                )
            }
            Surface(
                shape = FueldeckShapes.Pill,
                color = FueldeckColors.Surface,
                border = BorderStroke(1.dp, FueldeckColors.Line),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.size(7.dp).background(FueldeckColors.Amber, CircleShape))
                    Text(
                        if (metrics.hasData) "${metrics.fillCount} заправок"
                        else "нет данных",
                        fontSize = 13.sp, color = FueldeckColors.Ink
                    )
                }
            }
        }

        // ---------- приборы: расход + эффективность + руб/км ----------
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel(modifier = Modifier.weight(1.15f)) {
                ConsumptionGauge(
                    value = if (metrics.hasData) metrics.consumption else 0f
                )
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
                    MiniRing(value = metrics.efficiency)
                }
                Panel(modifier = Modifier.weight(1f)) {
                    Text("расход руб/км", fontSize = 11.sp, color = FueldeckColors.InkFaint,
                        letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (metrics.hasData) String.format("%.2f", metrics.rubPerKm) else "—",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = FueldeckColors.Ink,
                        )
                        Text(" руб", fontSize = 11.sp, color = FueldeckColors.InkFaint)
                    }
                }
            }
        }

        // ---------- спарклайн ----------
        Panel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("расход по заправкам", fontSize = 11.sp, color = FueldeckColors.InkFaint,
                    letterSpacing = 1.2.sp)
                Text("${metrics.sparkline.size} значений", fontSize = 11.sp, color = FueldeckColors.InkDim)
            }
            Spacer(Modifier.height(10.dp))
            if (metrics.sparkline.isNotEmpty()) {
                Sparkline(data = metrics.sparkline)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("мин ${String.format("%.1f", metrics.sparkline.min())}",
                        fontSize = 11.5.sp, color = FueldeckColors.InkFaint)
                    Text("средн ${String.format("%.1f", metrics.sparkline.average().toFloat())}",
                        fontSize = 11.5.sp, color = FueldeckColors.InkFaint)
                    Text("макс ${String.format("%.1f", metrics.sparkline.max())}",
                        fontSize = 11.5.sp, color = FueldeckColors.InkFaint)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Добавьте 2+ заправок для расчёта расхода",
                        fontSize = 13.sp, color = FueldeckColors.InkDim,
                        textAlign = TextAlign.Center)
                }
            }
        }

        // ---------- пустое состояние ИЛИ ответ ИИ ----------
        if (analysis == null && !isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FueldeckColors.Line2, FueldeckShapes.Md)
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Нажмите ниже — проанализирую последние заправки и стиль вождения за месяц.",
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
                            .background(FueldeckColors.Teal)
                    )
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("AI-анализ", fontSize = 11.sp, color = FueldeckColors.Teal,
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

        // ---------- кнопка анализа ----------
        Button(
            onClick = { viewModel.askAi() },
            enabled = !isAnalyzing,
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
                Text("Получить AI-анализ", fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp)
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
        Canvas(modifier = Modifier.size(84.dp)) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = FueldeckColors.Line, startAngle = 135f, sweepAngle = 270f,
                useCenter = false, style = stroke)
            drawArc(color = FueldeckColors.Teal, startAngle = 135f,
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