package com.navrot.aifuelassistant.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.fuel.MapViewModel
import org.osmdroid.util.GeoPoint

@Composable
fun StationBottomSheet(
    visible: Boolean,
    isLoading: Boolean,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    sortMode: MapViewModel.SortMode,
    userLocation: GeoPoint?,
    fuelTypes: List<String>,
    onToggleFuelType: (String) -> Unit,
    onSortChange: (MapViewModel.SortMode) -> Unit,
    onStationClick: (GasStation) -> Unit,
    onToggleVisibility: () -> Unit
) {
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
                    SortBar(
                        currentSort = sortMode,
                        onSortChange = onSortChange
                    )
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
                        items(stations.take(10)) { station ->
                            StationListItem(
                                station = station,
                                selectedFuelTypes = selectedFuelTypes,
                                userLocation = userLocation,
                                onClick = { onStationClick(station) }
                            )
                        }
                    }
                }
            }
        }
    }
}