package com.navrot.aifuelassistant.ui.map.renderer

import androidx.compose.ui.graphics.Color

/**
 * Visual presentation model for a gas station map marker,
 * completely decoupled from domain business models.
 */
data class StationMapItem(
    val stationId: Int,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String,
    val markerColor: Color,
    val isSelected: Boolean = false
)
