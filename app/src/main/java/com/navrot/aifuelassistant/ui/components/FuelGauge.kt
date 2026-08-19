package com.navrot.aifuelassistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.util.Format

/** Кольцевой прибор расхода (л/100 км) — перенос .gwrap из HTML на Canvas. */
@Composable
fun ConsumptionGauge(
    value: Float,
    max: Float = 12f,
    unit: String = "л / 100 км",
    caption: String = "средний за 30 дней",
) {
    val progress by animateFloatAsState(
        targetValue = (value / max).coerceIn(0f, 1f),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "gauge",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(172.dp)) {
            val stroke = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
            drawArc( // трек
                color = FueldeckColors.Line,
                startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke,
            )
            drawArc( // значение — градиент amber→coral
                brush = Brush.linearGradient(listOf(FueldeckColors.Amber, FueldeckColors.Coral)),
                startAngle = 135f, sweepAngle = 270f * progress, useCenter = false, style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Format.number(value, 1),
                color = FueldeckColors.Ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
            )
            Text(
                text = unit,
                color = FueldeckColors.InkDim,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp,
            )
        }
    }
    Text(
        text = caption,
        color = FueldeckColors.InkDim,
        fontSize = 12.5.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Спарклайн расхода за 30 дней — перенос .sparkline. Линия + площадь + точки мин/макс. */
@Composable
fun Sparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
) {
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "spark",
    )
    Canvas(modifier = modifier.height(54.dp).fillMaxWidth()) {
        if (data.size < 2) return@Canvas
        val mn = data.min(); val mx = data.max()
        val range = (mx - mn).takeIf { it > 0f } ?: 1f
        val w = size.width; val h = size.height
        fun px(i: Int) = (i.toFloat() / (data.size - 1)) * w
        fun py(v: Float) = h - ((v - mn) / range) * h * 0.9f - h * 0.05f

        val line = Path(); val area = Path()
        data.forEachIndexed { i, v ->
            val x = px(i); val y = py(v)
            if (i == 0) { line.moveTo(x, y); area.moveTo(x, h); area.lineTo(x, y) }
            else { line.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo(px(data.size - 1), h); area.close()

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(FueldeckColors.Amber.copy(alpha = 0.34f * reveal), FueldeckColors.Amber.copy(alpha = 0f)),
            ),
            style = Fill,
        )
        drawPath(
            path = line,
            color = FueldeckColors.Amber,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // точки макс (amber) и мин (teal)
        val iMax = data.indexOf(mx); val iMin = data.indexOf(mn)
        drawCircle(FueldeckColors.Amber, radius = 2.4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(px(iMax), py(mx)))
        drawCircle(FueldeckColors.Mint, radius = 2.4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(px(iMin), py(mn)))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1418)
@Composable
private fun FuelGaugePreview() {
    Column(
        modifier = Modifier
            .background(FueldeckColors.Bg1)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConsumptionGauge(value = 7.4f)
        Sparkline(
            data = listOf(7.1f, 7.4f, 6.9f, 7.8f, 8.2f, 7.6f, 8.9f, 9.1f, 8.4f, 7.9f, 7.2f, 6.8f, 7.0f, 7.4f),
        )
    }
}