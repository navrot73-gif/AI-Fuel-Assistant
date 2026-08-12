package com.navrot.aifuelassistant.ui.map.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.navrot.aifuelassistant.location.LocationSmoother
import com.navrot.aifuelassistant.ui.map.getCurrentLocation
import org.osmdroid.util.GeoPoint

/**
 * Stateless обработчик разрешений геолокации.
 * Запрашивает разрешения и при получении координат вызывает [onLocationReady]
 * (сглаживание координат выполняется здесь, вспомогательной функцией LocationSmoother).
 */
@Composable
fun LocationPermissionHandler(
    onLocationReady: (GeoPoint) -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current

    fun resolveLocation() {
        getCurrentLocation(context) { location ->
            val (smoothLat, smoothLon) = LocationSmoother.smooth(location.latitude, location.longitude)
            onLocationReady(GeoPoint(smoothLat, smoothLon))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        if (granted) resolveLocation() else onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            resolveLocation()
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