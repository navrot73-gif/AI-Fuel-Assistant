package com.navrot.aifuelassistant.ui.map.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.ui.map.MapViewModel
import com.navrot.aifuelassistant.ui.map.UserLocationState
import com.navrot.aifuelassistant.ui.map.createBlueAddressPinIcon
import com.navrot.aifuelassistant.ui.map.createColoredMarker
import com.navrot.aifuelassistant.ui.map.createRedPinIcon
import com.navrot.aifuelassistant.ui.map.createUserLocationIcon
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import timber.log.Timber


private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

/**
 * Concrete [MapRenderer] implementation using MapLibre engine.
 */
class MapLibreRenderer : MapRenderer {

    @Composable
    override fun Render(
        modifier: Modifier,
        stationItems: List<StationMapItem>,
        userLocation: UserLocationState?,
        route: MapViewModel.RouteOptionUiState?,
        isDarkMode: Boolean,
        recenterRequest: Int,
        zoomInRequest: Int,
        zoomOutRequest: Int,
        focusPoint: GeoPoint?,
        onStationClick: (Int) -> Unit
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val mapViewRef = remember { arrayOfNulls<MapView>(1) }
        var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
        val markerToStationIdMap = remember { mutableMapOf<Marker, Int>() }
        val activeOverlays = remember { mutableListOf<Any>() }

        val iconFactory = remember(context) { IconFactory.getInstance(context) }
        val iconCache = remember { mutableMapOf<Int, Icon>() }
        val userLocationIcon = remember(context) {
            iconFactory.fromBitmap(drawableToBitmap(createUserLocationIcon(context)))
        }
        val addressIcon = remember(context) {
            iconFactory.fromBitmap(drawableToBitmap(createBlueAddressPinIcon(context)))
        }
        val finishIcon = remember(context) {
            iconFactory.fromBitmap(drawableToBitmap(createRedPinIcon(context)))
        }

        fun getCachedMarkerIcon(color: Color): Icon {
            val colorArgb = color.toArgb()
            return iconCache.getOrPut(colorArgb) {
                val bitmap = drawableToBitmap(createColoredMarker(context, color))
                iconFactory.fromBitmap(bitmap)
            }
        }

        LaunchedEffect(zoomInRequest) {
            if (zoomInRequest > 0) {
                mapLibreMapInstance?.animateCamera(CameraUpdateFactory.zoomIn())
            }
        }

        LaunchedEffect(zoomOutRequest) {
            if (zoomOutRequest > 0) {
                mapLibreMapInstance?.animateCamera(CameraUpdateFactory.zoomOut())
            }
        }

        LaunchedEffect(recenterRequest) {
            if (recenterRequest > 0) {
                userLocation?.toGeoPoint()?.let { loc ->
                    mapLibreMapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16.0)
                    )
                }
            }
        }

        LaunchedEffect(focusPoint, mapLibreMapInstance) {
            val map = mapLibreMapInstance ?: return@LaunchedEffect
            if (focusPoint != null) {
                val target = LatLng(focusPoint.latitude, focusPoint.longitude)
                val focusMarker = map.addMarker(
                    MarkerOptions()
                        .position(target)
                        .title("Найденный адрес")
                        .icon(addressIcon)
                )
                activeOverlays.add(focusMarker)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16.0))
            }
        }

        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).also { mapView ->
                    mapViewRef[0] = mapView
                    mapView.onCreate(null)
                    mapView.getMapAsync { map ->
                        val styleJson = try {
                            ctx.assets.open("map_style_dark.json").bufferedReader().use { it.readText() }
                        } catch (e: Exception) {
                            Timber.tag("MapLibreView").e(e, "Failed to load dark map style from assets")
                            MapDiagnosticsTracker.tileStatus = "error: ${e.message}"
                            ""
                        }
                        map.setStyle(Style.Builder().fromJson(styleJson)) {
                            MapDiagnosticsTracker.activeTileSource = "openfreemap"
                            MapDiagnosticsTracker.tileStatus = "ok"
                            val userPt = userLocation?.toGeoPoint()
                            val centerPt = if (userPt != null && userPt.latitude != 0.0 && userPt.longitude != 0.0) {
                                LatLng(userPt.latitude, userPt.longitude)
                            } else {
                                LatLng(55.1644, 61.4368)
                            }
                            val initialZoom = if (userPt != null && userPt.latitude != 0.0) 16.0 else 13.0
                            map.cameraPosition = CameraPosition.Builder()
                                .target(centerPt)
                                .zoom(initialZoom)
                                .build()

                            map.setOnMarkerClickListener { marker ->
                                val stationId = markerToStationIdMap[marker]
                                if (stationId != null) {
                                    onStationClick(stationId)
                                    true
                                } else {
                                    false
                                }
                            }
                            mapLibreMapInstance = map
                        }
                    }
                }
            },
            modifier = modifier,
            update = { _ ->
                val map = mapLibreMapInstance ?: return@AndroidView

                // Clear previously drawn markers & polylines
                map.clear()
                markerToStationIdMap.clear()
                activeOverlays.clear()

                // 1. Draw User Location Dot
                userLocation?.let { location ->
                    if (location.latitude != 0.0 && location.longitude != 0.0) {
                        val locMarker = map.addMarker(
                            MarkerOptions()
                                .position(LatLng(location.latitude, location.longitude))
                                .title("Мое местоположение")
                                .icon(userLocationIcon)
                        )
                        activeOverlays.add(locMarker)
                    }
                }

                // 2. Draw Station Markers
                var renderedMarkers = 0
                var invalidMarkers = 0

                stationItems.forEach { item ->
                    if (item.latitude == 0.0 || item.longitude == 0.0 ||
                        item.latitude !in -90.0..90.0 || item.longitude !in -180.0..180.0) {
                        invalidMarkers++
                    } else {
                        val markerIcon = getCachedMarkerIcon(item.markerColor)
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(LatLng(item.latitude, item.longitude))
                                .title(item.title)
                                .snippet(item.snippet)
                                .icon(markerIcon)
                        )
                        markerToStationIdMap[marker] = item.stationId
                        renderedMarkers++
                    }
                }

                MapDiagnosticsTracker.pinsFeaturesCount = renderedMarkers
                Timber.tag("MapLibreView").i(
                    "MAP_DIAGNOSTIC renderer=maplibre registry=%d markers=%d invalid=%d",
                    stationItems.size,
                    renderedMarkers,
                    invalidMarkers
                )

                // 3. Draw Route
                route?.let { activeRoute ->
                    if (activeRoute.points.size >= 2) {
                        val routeLatLngs = activeRoute.points.map { LatLng(it.latitude, it.longitude) }
                        val polylineOptions = PolylineOptions()
                            .addAll(routeLatLngs)
                            .color(android.graphics.Color.parseColor("#2196F3"))
                            .width(5f)
                        val polyline = map.addPolyline(polylineOptions)
                        activeOverlays.add(polyline)

                        val finishPoint = activeRoute.points.last()
                        val finishMarker = map.addMarker(
                            MarkerOptions()
                                .position(LatLng(finishPoint.latitude, finishPoint.longitude))
                                .title("Финиш")
                                .icon(finishIcon)
                        )
                        activeOverlays.add(finishMarker)

                        try {
                            val boundsBuilder = LatLngBounds.Builder()
                            routeLatLngs.forEach { boundsBuilder.include(it) }
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100)
                            )
                        } catch (e: Exception) {
                            Timber.tag("MapLibreView").w("Failed to animate route bounds: %s", e.message)
                        }
                    }
                }
            }
        )

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                val mapView = mapViewRef[0] ?: return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapViewRef[0]?.onDestroy()
            }
        }
    }
}
