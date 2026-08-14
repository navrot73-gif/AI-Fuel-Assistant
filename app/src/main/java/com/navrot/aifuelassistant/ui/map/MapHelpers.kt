package com.navrot.aifuelassistant.ui.map

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Looper
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.isMedianFromNetwork
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

fun openMapsRoute(context: Context, lat: Double, lon: Double, label: String) {
    try {
        // 1. Пробуем открыть маршрут через Яндекс.Навигатор (если установлен)
        val yandexUri = android.net.Uri.parse(
            "yandexnavi://build_route_on_map?lat_to=$lat&lon_to=$lon"
        )
        val yandexIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, yandexUri).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (yandexIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(yandexIntent)
            return
        }

        // 2. Fallback: Google Maps (с URL-encoding label)
        val encodedLabel = java.net.URLEncoder.encode(label, "UTF-8")
        val googleUri = android.net.Uri.parse(
            "google.navigation:q=$lat,$lon"
        )
        val googleIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, googleUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (googleIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(googleIntent)
            return
        }

        // 3. Fallback: веб Google Maps
        val webUri = android.net.Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon"
        )
        val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    } catch (e: Exception) {
        android.util.Log.e("MapRoute", "openMapsRoute failed", e)
        Toast.makeText(context, "Не удалось открыть навигатор: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun getMarkerColor(station: GasStation, selectedFuelTypes: Set<String>): Color {
    val relevant = station.fuelTypes.filter { selectedFuelTypes.contains(it.type) }
    val available = relevant.filter { it.available }
    return when {
        relevant.isEmpty() -> FueldeckColors.InkFaint
        available.isEmpty() -> FueldeckColors.Coral
        station.queueTime > 15 -> FueldeckColors.Amber
        else -> FueldeckColors.Mint
    }
}

fun buildStationSnippet(station: GasStation, selectedFuelTypes: Set<String>): String {
    val fuels = station.fuelTypes
        .filter { selectedFuelTypes.contains(it.type) && it.available }
        .joinToString(" | ") {
            val tilde = if (it.isMedianFromNetwork) "~" else ""
            "${it.type}: $tilde${String.format("%.0f", it.price)}₽"
        }
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
                else requestCurrentLocation(fusedLocationClient, onLocation)
            }
            .addOnFailureListener {
                requestCurrentLocation(fusedLocationClient, onLocation)
            }
    } catch (_: SecurityException) {}
}

private fun requestCurrentLocation(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocation: (Location) -> Unit
) {
    try {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocation(it) }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    } catch (e: SecurityException) { }
}
