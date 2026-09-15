package com.navrot.aifuelassistant.ui.map.renderer

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.map.buildStationSnippet
import com.navrot.aifuelassistant.ui.map.getMarkerColor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps domain [GasStation] entities to engine-agnostic [StationMapItem] presentation objects.
 */
@Singleton
class StationMapMapper @Inject constructor() {

    fun mapToMapItem(
        station: GasStation,
        selectedFuelTypes: Set<String>,
        isSelected: Boolean = false
    ): StationMapItem {
        return StationMapItem(
            stationId = station.id,
            latitude = station.latitude,
            longitude = station.longitude,
            title = station.name,
            snippet = buildStationSnippet(station, selectedFuelTypes),
            markerColor = getMarkerColor(station, selectedFuelTypes),
            isSelected = isSelected
        )
    }

    fun mapToMapItems(
        stations: List<GasStation>,
        selectedFuelTypes: Set<String>,
        selectedStationId: Int? = null
    ): List<StationMapItem> {
        return stations.map { station ->
            mapToMapItem(
                station = station,
                selectedFuelTypes = selectedFuelTypes,
                isSelected = (station.id == selectedStationId)
            )
        }
    }
}
