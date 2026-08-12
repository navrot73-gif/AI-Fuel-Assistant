package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stateless FAB-кнопки в правом нижнем углу карты.
 *
 * Основная — "Моё местоположение" ([onRecenter]). Дополнительные FAB
 * (слои/поиск/список АЗС) отображаются только при переданном колбэке —
 * так экран сохраняет исходную визуальную структуру.
 */
@Composable
fun BoxScope.MapFloatingActions(
    bottomPadding: androidx.compose.ui.unit.Dp,
    onRecenter: () -> Unit,
    onLayersClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onStationListClick: (() -> Unit)? = null
) {
    val extraButtons = listOfNotNull(onLayersClick, onSearchClick, onStationListClick)

    if (extraButtons.isNotEmpty()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomPadding + 72.dp),
            horizontalAlignment = Alignment.End
        ) {
            onLayersClick?.let { onClick ->
                SmallFloatingActionButton(
                    onClick = onClick,
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Слои")
                }
                Spacer(Modifier.height(8.dp))
            }
            onSearchClick?.let { onClick ->
                SmallFloatingActionButton(
                    onClick = onClick,
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                }
                Spacer(Modifier.height(8.dp))
            }
            onStationListClick?.let { onClick ->
                SmallFloatingActionButton(
                    onClick = onClick,
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.List, contentDescription = "Список АЗС")
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.width(1.dp))
        }
    }

    FloatingActionButton(
        onClick = onRecenter,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = bottomPadding),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = CircleShape
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = "Моё местоположение",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}