package com.navrot.aifuelassistant.ui.map

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.location.LocationServices
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

fun openMapsRoute(context: Context, lat: Double, lon: Double, label: String) {
    val uri = android.net.Uri.parse("https://maps.google.com/maps?daddr=$lat,$lon($label)")
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    try {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val browserUri = android.net.Uri.parse("https://maps.google.com/maps?daddr=$lat,$lon")
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, browserUri)
            context.startActivity(browserIntent)
        }
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Не найдено приложение для навигации", Toast.LENGTH_SHORT).show()
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

fun getCurrentLocation(context: Context, onLocation: (Location) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) onLocation(location)
            }
    } catch (_: SecurityException) {}
}
