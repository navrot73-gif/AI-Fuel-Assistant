package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

/**
 * Карточка AI-подбора: закреплённая над списком станций.
 * Показывает лучшую станцию по эвристике (цена + очередь + надёжность + расстояние).
 */
@Composable
fun AiPickCard(
    bestStation: GasStation?,
    avgPrice: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (bestStation == null) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = FueldeckColors.Surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FueldeckColors.Amber)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = FueldeckColors.Amber,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp)
            ) {
                Text(
                    "✨ AI-подбор",
                    color = FueldeckColors.Amber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Text(
                    bestStation.brand,
                    color = FueldeckColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    buildAiPickReasonText(bestStation, avgPrice),
                    color = FueldeckColors.InkDim,
                    fontSize = 13.sp
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = FueldeckColors.InkDim
            )
        }
    }
}

/**
 * Локальная эвристика для текста причины подбора (БЕЗ вызова AI-провайдера).
 */
fun buildAiPickReasonText(best: GasStation, avgPrice: Double): String {
    val primaryFuel = best.fuelTypes.firstOrNull { it.available }
    val bestPrice = primaryFuel?.price ?: 0.0
    val priceDiff = avgPrice - bestPrice

    val queueNote = if (best.queueTime <= 2) "очереди нет"
    else "очередь ${best.queueTime} мин"

    val priceNote = if (priceDiff > 0) "цена ниже средней на ${"%.0f".format(priceDiff)}₽"
    else "цена в рынке"

    return "$queueNote, $priceNote"
}