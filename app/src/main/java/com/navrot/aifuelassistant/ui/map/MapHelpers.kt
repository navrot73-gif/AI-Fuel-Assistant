package com.navrot.aifuelassistant.ui.map

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.util.Format

fun buildYandexMapsIntent(user: Pair<Double, Double>, dest: Pair<Double, Double>): Intent {
    val yandexUrl = "https://yandex.ru/maps/?rtext=" +
            "${user.first},${user.second}~${dest.first},${dest.second}" +
            "&rtt=auto"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(yandexUrl))
    intent.setPackage("ru.yandex.yandexmaps")
    return intent
}

fun launchYandexMapsRoute(context: Context, user: Pair<Double, Double>, dest: Pair<Double, Double>) {
    val yandexIntent = buildYandexMapsIntent(user, dest)
    try {
        context.startActivity(yandexIntent)
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, yandexIntent.data))
    }
}

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
        timber.log.Timber.tag("MapRoute").e(e, "openMapsRoute failed")
        Toast.makeText(context, "Не удалось открыть навигатор: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun getMarkerColor(station: GasStation, selectedFuelTypes: Set<String>): Color {
    val selectedFuelType = selectedFuelTypes.firstOrNull()
    return when (PriceReliabilityCalculator.calculateFuelAvailability(station, selectedFuelType)) {
        FuelAvailabilityStatus.AVAILABLE -> FueldeckColors.Mint // 🟢 зелёный
        FuelAvailabilityStatus.NO_FUEL -> FueldeckColors.Coral  // 🔴 красный
        FuelAvailabilityStatus.UNKNOWN -> FueldeckColors.InkFaint // ⚪ серый
    }
}

fun buildStationSnippet(station: GasStation, selectedFuelTypes: Set<String>): String {
    val fuels = station.fuelTypes
        .filter { selectedFuelTypes.contains(it.type) }
        .joinToString(" | ") {
            val tilde = if (it.isMedianFromNetwork) "~" else ""
            val statusStr = when (PriceReliabilityCalculator.calculateFuelAvailability(station, it.type)) {
                FuelAvailabilityStatus.AVAILABLE -> "🟢"
                FuelAvailabilityStatus.NO_FUEL -> "🔴"
                FuelAvailabilityStatus.UNKNOWN -> "⚪"
            }
            "${it.type}: $statusStr $tilde${Format.price(it.price)}₽"
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

/** Creates a Google Maps–style red finish pin (drop shape #EA4335 with dark center dot). */
fun createRedPinIcon(context: Context): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (22 * density).toInt()
    val height = (30 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val pinPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#EA4335")
        style = android.graphics.Paint.Style.FILL
    }
    val dotPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#7F1D1D")
        style = android.graphics.Paint.Style.FILL
    }

    val path = android.graphics.Path().apply {
        moveTo(width / 2f, height.toFloat())
        cubicTo(width / 2f, height.toFloat(), 0f, height * 0.55f, 0f, height * 0.35f)
        cubicTo(0f, height * 0.15f, width * 0.35f, 0f, width / 2f, 0f)
        cubicTo(width * 0.65f, 0f, width.toFloat(), height * 0.15f, width.toFloat(), height * 0.35f)
        cubicTo(width.toFloat(), height * 0.55f, width / 2f, height.toFloat(), width / 2f, height.toFloat())
        close()
    }
    canvas.drawPath(path, pinPaint)

    val dotRadius = (3 * density).toInt()
    canvas.drawCircle(width / 2f, height * 0.28f, dotRadius.toFloat(), dotPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

/** Creates a Google Maps–style user position dot icon (blue dot with white ring and accuracy halo). */
fun createUserLocationIcon(context: Context): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val haloRadius = (18 * density).toInt()
    val size = haloRadius * 2
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    val haloPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = 0x334285F4.toInt()
        style = android.graphics.Paint.Style.FILL
    }
    val ringPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    val dotPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#4285F4")
        style = android.graphics.Paint.Style.FILL
    }

    canvas.drawCircle(cx, cy, haloRadius.toFloat(), haloPaint)
    val dotRadius = 9f * density
    val ringStroke = 3f * density
    canvas.drawCircle(cx, cy, dotRadius + ringStroke / 2f, ringPaint)
    canvas.drawCircle(cx, cy, dotRadius, dotPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

/** Creates a BLUE address pin (#4285F4 drop shape with white center dot). */
fun createBlueAddressPinIcon(context: Context): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (32 * density).toInt()
    val height = (40 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val pinPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#4285F4")
        style = android.graphics.Paint.Style.FILL
    }
    val dotPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }

    val path = android.graphics.Path().apply {
        moveTo(width / 2f, height.toFloat())
        cubicTo(width / 2f, height.toFloat(), 0f, height * 0.55f, 0f, height * 0.35f)
        cubicTo(0f, height * 0.15f, width * 0.35f, 0f, width / 2f, 0f)
        cubicTo(width * 0.65f, 0f, width.toFloat(), height * 0.15f, width.toFloat(), height * 0.35f)
        cubicTo(width.toFloat(), height * 0.55f, width / 2f, height.toFloat(), width / 2f, height.toFloat())
        close()
    }
    canvas.drawPath(path, pinPaint)

    val dotRadius = (4 * density).toInt()
    canvas.drawCircle(width / 2f, height * 0.28f, dotRadius.toFloat(), dotPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
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
