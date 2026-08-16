package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.map.MapViewModel
import com.navrot.aifuelassistant.ui.map.StationBottomSheet
import org.osmdroid.util.GeoPoint

/**
 * Stateless нижняя панель со списком АЗС. Все данные и события — через параметры.
 */
@Composable
fun BoxScope.StationListBottomSheet(
    visible: Boolean,
    isLoading: Boolean,
    stations: List<GasStation>,
    aiRecommendation: GasStation?,
    bestStation: GasStation?,
    avgPrice: Double,
    selectedFuelTypes: Set<String>,
    sortMode: MapViewModel.SortMode,
    userLocation: GeoPoint?,
    fuelTypes: List<String>,
    brands: List<String>,
    selectedBrands: Set<String>,
    openOnly: Boolean,
    onToggleOpenOnly: () -> Unit,
    onToggleFuelType: (String) -> Unit,
    onToggleBrand: (String) -> Unit,
    onSortChange: (MapViewModel.SortMode) -> Unit,
    onStationClick: (GasStation) -> Unit,
    onToggleVisibility: () -> Unit
) {
    StationBottomSheet(
        visible = visible,
        isLoading = isLoading,
        stations = stations,
        aiRecommendation = aiRecommendation,
        bestStation = bestStation,
        avgPrice = avgPrice,
        selectedFuelTypes = selectedFuelTypes,
        sortMode = sortMode,
        userLocation = userLocation,
        fuelTypes = fuelTypes,
        brands = brands,
        selectedBrands = selectedBrands,
        openOnly = openOnly,
        onToggleOpenOnly = onToggleOpenOnly,
        onToggleFuelType = onToggleFuelType,
        onToggleBrand = onToggleBrand,
        onSortChange = onSortChange,
        onStationClick = onStationClick,
        onToggleVisibility = onToggleVisibility,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .align(Alignment.BottomCenter)
    )
}