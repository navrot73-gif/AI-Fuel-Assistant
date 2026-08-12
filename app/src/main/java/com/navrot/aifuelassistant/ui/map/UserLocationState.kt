package com.navrot.aifuelassistant.ui.map

import org.osmdroid.util.GeoPoint

data class UserLocationState(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val hasBearing: Boolean
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}
