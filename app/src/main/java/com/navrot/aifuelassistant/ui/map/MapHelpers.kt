package com.navrot.aifuelassistant.ui.map

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.location.Location
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.location.LocationServices
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import org.osmdroid.util.GeoPoint

fun openMapsRoute(context: Context, lat: Double, lon: Double, label: String) {
    val uri = android.net.Uri.parse("https://maps.google.com/maps?daddr=$lat,$lon($label)")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        val browserUri = android.net.Uri.parse("https://maps.google.com/maps?daddr=$lat,$lon")
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, browserUri))
    }
}

fun getMarkerColor(station: GasStation, selectedFuelTypes: Set<String>): Color {
    val relevant = station.fuelTypes.filter { selectedFuelTypes.contains(it.type) }
    val available = relevant.filter { it.available }
    return when {
        relevant.isEmpty() -> FueldeckColors.InkFaint
        available.isEmpty() -> FueldeckColors.Coral
        station.queueTime > 15 -> FueldeckColors.Amber
        else -> FueldeckColors.Teal
    }
}

fun buildStationSnippet(station: GasStation, selectedFuelTypes: Set<String>): String {
    val fuels = station.fuelTypes
        .filter { selectedFuelTypes.contains(it.type) && it.available }
        .joinToString(" | ") { "${it.type}: ${String.format("%.0f", it.price)}₽" }
    return if (fuels.isNotEmpty()) {
        "$fuels | Очередь: ${station.queueTime} мин"
    } else {
        "Нет выбранного топлива | Очередь: ${station.queueTime} мин"
    }
}

fun createColoredMarker(context: Context, color: Color): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (28 * density).toInt()
    val strokePx = (3 * density).toInt()
    val drawable = GradientDrawable()
    drawable.shape = GradientDrawable.OVAL
    drawable.setColor(color.toArgb())
    drawable.setStroke(strokePx, android.graphics.Color.WHITE)
    drawable.setSize(sizePx, sizePx)
    return drawable
}

fun detectCity(lat: Double, lon: Double): String {
    return when {
        lat in 55.1..55.3 && lon in 61.2..61.6 -> "Челябинске"
        lat in 54.0..54.2 && lon in 61.4..61.7 -> "Троицке"
        lat in 55.0..55.1 && lon in 60.0..60.2 -> "Миассе"
        lat in 55.1..55.2 && lon in 59.5..59.8 -> "Златоусте"
        lat in 53.3..53.5 && lon in 58.9..59.2 -> "Магнитогорске"
        lat in 55.0..55.1 && lon in 61.5..61.7 -> "Копейске"
        lat in 56.0..56.1 && lon in 60.6..60.8 -> "Снежинске"
        lat in 55.7..55.8 && lon in 60.6..60.8 -> "Озёрске"
        lat in 54.4..54.5 && lon in 61.1..61.3 -> "Южноуральске"
        lat in 54.9..55.0 && lon in 57.2..57.4 -> "Аше"
        lat in 55.7..55.8 && lon in 37.5..37.7 -> "Москве"
        else -> "вашем районе"
    }
}

fun getCurrentLocation(context: Context, onLocation: (Location) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) onLocation(location)
            }
    } catch (_: SecurityException) {}
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return R * c
}