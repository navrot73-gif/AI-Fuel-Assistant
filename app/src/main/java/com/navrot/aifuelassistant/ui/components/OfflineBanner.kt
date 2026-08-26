package com.navrot.aifuelassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Индикатор офлайн-статуса приложения.
 * Отображается, когда отсутствует подключение к сети, и показывает,
 * что данные АЗС загружаются из кэша и когда они были обновлены.
 */
@Composable
fun OfflineBanner(
    isOnline: Boolean,
    lastCacheUpdateMs: Long?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        val cacheText = if (lastCacheUpdateMs != null && lastCacheUpdateMs > 0) {
            val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT)
            "Данные из кэша (обновлено: ${formatter.format(Date(lastCacheUpdateMs))})"
        } else {
            "Данные из кэша"
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF382C1E),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "Офлайн",
                    tint = FueldeckColors.Amber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Офлайн-режим • $cacheText",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = FueldeckColors.Amber
                )
            }
        }
    }
}
