package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * Stateless TopAppBar карты: заголовок "Где бензин?" / "Карта: $vehicleName",
 * подзаголовок города, кнопки "Назад", "Поиск" и "Мои автомобили".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTopBar(
    vehicleId: Long,
    vehicleName: String,
    currentCity: String,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onVehiclesClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    if (vehicleId == 0L) "Где бензин?" else "Карта: $vehicleName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Топливо в $currentCity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            if (vehicleId != 0L) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Поиск")
            }
            IconButton(onClick = onVehiclesClick) {
                Icon(Icons.Default.Menu, contentDescription = "Мои автомобили")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}