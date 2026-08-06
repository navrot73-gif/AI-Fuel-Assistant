package com.navrot.aifuelassistant.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import org.osmdroid.util.GeoPoint

private const val PAGE_SIZE = 20

@Composable
fun StationBottomSheet(
    visible: Boolean,
    isLoading: Boolean,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    sortMode: MapViewModel.SortMode,
    userLocation: GeoPoint?,
    fuelTypes: List<String>,
    openOnly: Boolean,
    onToggleOpenOnly: () -> Unit,
    onToggleFuelType: (String) -> Unit,
    onSortChange: (MapViewModel.SortMode) -> Unit,
    onStationClick: (GasStation) -> Unit,
    onToggleVisibility: () -> Unit
) {
    val visibleCount = remember(stations.size) {
        if (stations.size <= PAGE_SIZE) stations.size else PAGE_SIZE
    }
    val hasMore = stations.size > PAGE_SIZE

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxWidth(),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .clickable { onToggleVisibility() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Column {
                    FuelTypeFilter(
                        fuelTypes = fuelTypes,
                        selectedFuelTypes = selectedFuelTypes,
                        onFuelTypeToggled = onToggleFuelType
                    )
                    // ===== Чип "Открытые" + сортировка в одну строку =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (openOnly) FueldeckColors.Amber else FueldeckColors.Surface,
                            border = if (openOnly) null else BorderStroke(1.dp, FueldeckColors.Line),
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable { onToggleOpenOnly() }
                        ) {
                            Text(
                                text = if (openOnly) "🕐 Открытые" else "🕐 Все",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = if (openOnly) Color(0xFF1A1205) else FueldeckColors.InkDim,
                                fontWeight = if (openOnly) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SortBar(
                                currentSort = sortMode,
                                onSortChange = onSortChange
                            )
                        }
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(stations.take(visibleCount), key = { it.id }) { station ->
                            StationListItem(
                                station = station,
                                selectedFuelTypes = selectedFuelTypes,
                                userLocation = userLocation,
                                onClick = { onStationClick(station) }
                            )
                        }

                        if (hasMore) {
                            item {
                                TextButton(
                                    onClick = { onToggleVisibility() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Показать все (${stations.size} АЗС)",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}