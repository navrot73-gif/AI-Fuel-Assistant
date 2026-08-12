package com.navrot.aifuelassistant.ui.map.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.navrot.aifuelassistant.location.LocationSmoother
import com.navrot.aifuelassistant.ui.map.UserLocationState
import com.navrot.aifuelassistant.ui.map.getCurrentLocation

/**
 * Stateless обработчик разрешений геолокации.
 * Запрашивает разрешения и при получении координат вызывает [onLocationUpdate]
 * (сглаживание координат выполняется здесь, вспомогательной функцией LocationSmoother).
 */
@Composable
fun LocationPermissionHandler(
    onLocationUpdate: (UserLocationState) -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val onLocationUpdateState = rememberUpdatedState(onLocationUpdate)

    fun emitLocation(location: android.location.Location) {
        val (smoothLat, smoothLon) = LocationSmoother.smooth(
            location.latitude,
            location.longitude
        )
        onLocationUpdateState.value(
            UserLocationState(
                latitude = smoothLat,
                longitude = smoothLon,
                accuracy = location.accuracy,
                speed = location.speed,
                bearing = location.bearing,
                hasBearing = location.hasBearing()
            )
        )
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::emitLocation)
            }
        }
    }

    fun startLocationUpdates() {
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .build()
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) { }
    }

    fun beginTracking() {
        getCurrentLocation(context, ::emitLocation)
        startLocationUpdates()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        if (granted) beginTracking() else onPermissionDenied()
    }

    DisposableEffect(Unit) {
        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
        }
    }

    LaunchedEffect(Unit) {
        val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            beginTracking()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
