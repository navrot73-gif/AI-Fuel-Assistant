package com.navrot.aifuelassistant.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.theme.FueldeckShapes

data class VehicleCardUiState(
    val name: String,
    val modelLine: String,
    val fuelGrade: String,
    val tankLiters: Int,
    val fillPercent: Int,
    val rangeKm: Int,
    val mileageText: String,
    val consumptionText: String,
    val fillCount: Int,
    val bars: List<Float>,
    val toKmLeft: Int,
    val toPercent: Int,
    val lastFillDate: String,
    val lastFillLiters: String,
    val lastFillBrand: String,
    val lastFillPrice: String,
)

@Composable
fun VehicleCard(state: VehicleCardUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {

        Surface(
            color = FueldeckColors.Surface,
            shape = FueldeckShapes.Lg,
            border = BorderStroke(1.dp, FueldeckColors.Line),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    FueldeckColors.Amber.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * 0.82f, 0f),
                                radius = size.width * 0.7f,
                            ),
                        )
                    },
            ) {
                Column {
                    Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column {
                                Text(state.name, fontSize = 23.sp, fontWeight = FontWeight.SemiBold,
                                    color = FueldeckColors.Ink, letterSpacing = (-0.3).sp)
                                Text(state.modelLine, fontSize = 13.sp, color = FueldeckColors.InkDim)
                            }
                            Surface(
                                shape = FueldeckShapes.Pill,
                                color = FueldeckColors.AmberSoft,
                                border = BorderStroke(1.dp, FueldeckColors.Amber.copy(alpha = 0.25f)),
                            ) {
                                Text(state.fuelGrade, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp, color = FueldeckColors.Amber)
                            }
                        }

                        CarSilhouette(modifier = Modifier.fillMaxWidth().height(120.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("бак ${state.tankLiters} л", fontSize = 11.sp, color = FueldeckColors.InkFaint,
                                letterSpacing = 1.2.sp)
                            Text("${state.fillPercent}% · ≈ ${state.rangeKm} км",
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, color = FueldeckColors.Ink)
                        }
                        Spacer(Modifier.height(8.dp))
                        FuelBar(percent = state.fillPercent)
                        Spacer(Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().background(FueldeckColors.Line),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Tile("пробег", state.mileageText, " тыс км", Modifier.weight(1f))
                        Tile("расход", state.consumptionText, " л", Modifier.weight(1f))
                        Tile("заправок", state.fillCount.toString(), null, Modifier.weight(1f))
                    }
                }
            }
        }

        Surface(
            color = FueldeckColors.Surface,
            shape = FueldeckShapes.Lg,
            border = BorderStroke(1.dp, FueldeckColors.Line),
        ) {
            Column(modifier = Modifier.padding(15.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("расход по заправкам", fontSize = 11.sp, color = FueldeckColors.InkFaint, letterSpacing = 1.2.sp)
                    Text("л / 100 км", fontSize = 11.sp, color = FueldeckColors.InkFaint, letterSpacing = 1.2.sp)
                }
                Spacer(Modifier.height(14.dp))
                ConsumptionBars(state.bars)
                Spacer(Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FueldeckColors.Line))
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(34.dp).background(FueldeckColors.AmberSoft, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center) {
                        Text("🔧", fontSize = 16.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("следующее ТО через ${state.toKmLeft} км", fontSize = 12.5.sp, color = FueldeckColors.InkDim)
                        Spacer(Modifier.height(7.dp))
                        ToBar(percent = state.toPercent)
                    }
                    Text("${state.toPercent}%", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = FueldeckColors.Amber)
                }
            }
        }

        Surface(
            color = FueldeckColors.Surface,
            shape = FueldeckShapes.Md,
            border = BorderStroke(1.dp, FueldeckColors.Line),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).background(FueldeckColors.TealSoft, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) { Text("⛽", fontSize = 16.sp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("последняя заправка", fontSize = 13.sp, color = FueldeckColors.InkDim)
                    Text("${state.lastFillDate} · ${state.lastFillLiters} л · ${state.lastFillBrand}",
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = FueldeckColors.Ink)
                }
                Text("${state.lastFillPrice} ₽", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = FueldeckColors.Ink)
            }
        }
    }
}

@Composable
private fun Tile(key: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(FueldeckColors.Surface)
            .padding(vertical = 14.dp, horizontal = 12.dp),
    ) {
        Text(key, fontSize = 10.5.sp, color = FueldeckColors.InkFaint, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = FueldeckColors.Ink)
            if (unit != null) Text(unit, fontSize = 10.sp, color = FueldeckColors.InkFaint)
        }
    }
}

@Composable
private fun FuelBar(percent: Int) {
    val p by animateFloatAsState(percent / 100f, tween(1200), label = "fuel")
    Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0x12FFFFFF), FueldeckShapes.Pill)) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(8.dp)
                .background(Brush.horizontalGradient(listOf(FueldeckColors.Teal, FueldeckColors.Amber)), FueldeckShapes.Pill),
        )
    }
}

@Composable
private fun ToBar(percent: Int) {
    val p by animateFloatAsState(percent / 100f, tween(1200), label = "to")
    Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0x12FFFFFF), FueldeckShapes.Pill)) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(5.dp)
                .background(Brush.horizontalGradient(listOf(FueldeckColors.Amber, FueldeckColors.Coral)), FueldeckShapes.Pill),
        )
    }
}

@Composable
private fun ConsumptionBars(values: List<Float>) {
    val max = values.maxOrNull() ?: 1f
    val min = values.minOrNull() ?: 0f
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { v ->
            val h by animateFloatAsState(
                targetValue = 0.25f + ((v - min) / ((max - min).takeIf { it > 0f } ?: 1f)) * 0.75f,
                animationSpec = tween(900), label = "bar",
            )
            val color = when {
                v <= 7.0f -> FueldeckColors.Teal
                v <= 7.6f -> FueldeckColors.Amber
                else -> FueldeckColors.Coral
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(40.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(h)
                            .background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                    )
                }
                Text(String.format("%.1f", v), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp,
                    color = FueldeckColors.InkFaint)
            }
        }
    }
}

@Composable
private fun CarSilhouette(modifier: Modifier = Modifier) {
    val shimmer = rememberInfiniteTransition(label = "sheen").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(5500), repeatMode = RepeatMode.Restart),
        label = "sheen",
    )
    val sh = shimmer.value
    val ring = Color(0xFF9FB3B1)

    Box(
        modifier = modifier
            .drawBehind {
                val us = minOf(size.width / 240f, size.height / 96f)
                val dx = (size.width - 240f * us) / 2f
                val dy = (size.height - 96f * us) / 2f
                translate(left = dx, top = dy) {
                    scale(scaleX = us, scaleY = us, pivot = Offset.Zero) {
                        drawOval(Color(0x66000000), topLeft = Offset(70f, 80f), size = Size(108f, 12f))
                        val grad = Brush.linearGradient(
                            listOf(FueldeckColors.Teal, FueldeckColors.Amber),
                            start = Offset.Zero, end = Offset(240f, 0f),
                        )
                        drawPath(bodyPath(), grad, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(winFront(), FueldeckColors.Teal.copy(alpha = 0.10f))
                        drawPath(winFront(), FueldeckColors.Teal.copy(alpha = 0.5f), style = Stroke(1.5f))
                        drawPath(winRear(), FueldeckColors.Amber.copy(alpha = 0.08f))
                        drawPath(winRear(), FueldeckColors.Amber.copy(alpha = 0.4f), style = Stroke(1.5f))
                        wheel(62f, 72f, ring)
                        wheel(172f, 72f, ring)
                    }
                }
            }
            .drawBehind {
                val w = size.width
                val band = w * 0.4f
                val x = -band + sh * (w + band)
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + band, 0f),
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(band, size.height),
                )
            },
    )
}

private fun DrawScope.wheel(cx: Float, cy: Float, ring: Color) {
    drawCircle(Color(0xFF0A0E11), 15f, Offset(cx, cy))
    drawCircle(ring, 15f, Offset(cx, cy), style = Stroke(3f))
    drawCircle(ring, 6f, Offset(cx, cy))
}

private fun bodyPath(): Path {
    return Path().apply {
        moveTo(14f, 70f)
        cubicTo(14f, 64f, 18f, 61f, 26f, 60f)
        lineTo(48f, 57f)
        lineTo(66f, 41f)
        cubicTo(71f, 37f, 77f, 35f, 85f, 35f)
        lineTo(119f, 35f)
        cubicTo(128f, 35f, 135f, 38f, 142f, 44f)
        lineTo(158f, 57f)
        lineTo(188f, 61f)
        cubicTo(200f, 63f, 208f, 68f, 210f, 77f)
        cubicTo(211f, 82f, 208f, 86f, 202f, 86f)
        lineTo(190f, 86f)
        moveTo(14f, 70f)
        lineTo(42f, 70f)
        moveTo(86f, 70f)
        lineTo(148f, 70f)
        moveTo(196f, 70f)
        lineTo(202f, 70f)
        lineTo(202f, 78f)
    }
}

private fun winFront(): Path {
    return Path().apply {
        moveTo(70f, 55f)
        lineTo(84f, 40f)
        lineTo(113f, 40f)
        lineTo(113f, 55f)
        close()
    }
}

private fun winRear(): Path {
    return Path().apply {
        moveTo(117f, 55f)
        lineTo(117f, 40f)
        lineTo(133f, 41f)
        lineTo(152f, 54f)
        close()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1418)
@Composable
private fun VehicleCardPreview() {
    Box(Modifier.background(FueldeckColors.Bg1).padding(20.dp)) {
        VehicleCard(
            state = VehicleCardUiState(
                name = "Пенс",
                modelLine = "Datsun ON‑DO · 2015",
                fuelGrade = "АИ‑92",
                tankLiters = 50, fillPercent = 64, rangeKm = 320,
                mileageText = "142.3", consumptionText = "7.4", fillCount = 38,
                bars = listOf(7.9f, 7.2f, 8.1f, 7.4f, 6.9f, 7.6f, 7.4f),
                toKmLeft = 3200, toPercent = 68,
                lastFillDate = "24.07", lastFillLiters = "31.2",
                lastFillBrand = "Лукойл", lastFillPrice = "1 619",
            ),
        )
    }
}