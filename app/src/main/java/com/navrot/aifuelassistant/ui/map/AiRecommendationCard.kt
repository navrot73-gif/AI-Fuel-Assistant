package com.navrot.aifuelassistant.ui.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

@Composable
fun AiRecommendationCard(
    recommendation: Triple<GasStation, com.navrot.aifuelassistant.data.model.FuelPrice, Double>?,
    onExpandList: () -> Unit
) {
    val context = LocalContext.current
    recommendation ?: return

    val (station, fuel, dist) = recommendation
    val brandColors = listOf(
        FueldeckColors.Teal, FueldeckColors.Amber, FueldeckColors.Coral,
        Color(0xFF2F7FD1), Color(0xFF7A6BD1), Color(0xFF2FAA55)
    )
    val brandColor = brandColors[Math.floorMod(station.brand.hashCode(), brandColors.size)]
    val spark = rememberInfiniteTransition(label = "rec").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "rec"
    )

    val reason = buildString {
        append("ближайшая с ${fuel.type} в наличии")
        if (station.queueTime <= 0) append(", без очереди")
        else append(", очередь ${station.queueTime} мин")
    }

    Surface(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = FueldeckColors.Surface,
        border = BorderStroke(1.dp, FueldeckColors.Teal.copy(alpha = 0.35f)),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onExpandList)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            listOf(brandColor.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.2f),
                            radius = size.width * 0.6f
                        )
                    )
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(brandColor, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    station.brand.first().toString().uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✨", color = FueldeckColors.Teal.copy(alpha = 0.5f + 0.5f * spark.value), fontSize = 13.sp)
                    Text("AI‑подбор", color = FueldeckColors.Teal, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, letterSpacing = 0.6.sp)
                }
                Text(station.brand, color = FueldeckColors.Ink, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1)
                Text(reason, color = FueldeckColors.InkFaint, fontSize = 11.sp, maxLines = 1)
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${String.format("%.0f", fuel.price)} ₽", color = FueldeckColors.Amber,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (dist == Double.MAX_VALUE) "—" else "${String.format("%.1f", dist)} км",
                    color = FueldeckColors.InkDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        FueldeckColors.TealSoft,
                        CircleShape
                    )
                    .clickable {
                        openMapsRoute(context, station.latitude, station.longitude, station.brand)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Маршрут",
                    tint = FueldeckColors.Teal, modifier = Modifier.size(22.dp))
            }
        }
    }
}