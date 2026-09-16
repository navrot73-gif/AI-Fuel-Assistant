package com.navrot.aifuelassistant.ui.map.renderer

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.ui.map.MapViewModel
import com.navrot.aifuelassistant.ui.map.MyLocationDot
import com.navrot.aifuelassistant.ui.map.UserLocationState
import com.navrot.aifuelassistant.ui.map.createBlueAddressPinIcon
import com.navrot.aifuelassistant.ui.map.createColoredMarker
import com.navrot.aifuelassistant.ui.map.createRedPinIcon
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import org.osmdroid.util.GeoPoint as OsmGeoPoint

private const val MAP_BACKGROUND = "#17222B"

/** ColorMatrix for dark mode palette over OSM tiles. */
private val OSM_DARK_COLOR_MATRIX = ColorMatrix(
    floatArrayOf(
        0.88f, 0.04f, 0.12f, 0f, 14f,
        0.03f, 0.90f, 0.10f, 0f, 18f,
        0.06f, 0.08f, 1.02f, 0f, 22f,
        0f, 0f, 0f, 1f, 0f
    )
)

private fun processRoutePoints(points: List<OsmGeoPoint>, userLocationPt: OsmGeoPoint?): List<OsmGeoPoint> {
    if (points.isEmpty()) return points
    val pts = if (userLocationPt != null) {
        (listOf(userLocationPt) + points.drop(1)).toMutableList()
    } else {
        points.toMutableList()
    }

    while (pts.size > 2) {
        val p0 = pts[0]
        val p1 = pts[1]
        val p2 = pts[2]
        val v1x = p1.longitude - p0.longitude
        val v1y = p1.latitude - p0.latitude
        val v2x = p2.longitude - p1.longitude
        val v2y = p2.latitude - p1.latitude
        val dot = v1x * v2x + v1y * v2y
        if (dot < 0) {
            pts.removeAt(1)
        } else {
            break
        }
    }
    return pts
}

/**
 * Concrete [MapRenderer] implementation using osmdroid engine.
 */
class OsmMapRenderer : MapRenderer {

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
        val mapViewRef = remember { arrayOfNulls<MapView>(1) }
        val locationDotRef = remember { arrayOfNulls<MyLocationDot>(1) }
        val focusMarkerRef = remember { arrayOfNulls<Marker>(1) }
        val lastFocusPoint = remember { arrayOfNulls<OsmGeoPoint>(1) }

        LaunchedEffect(zoomInRequest) {
            if (zoomInRequest > 0) mapViewRef[0]?.controller?.zoomIn()
        }
        LaunchedEffect(zoomOutRequest) {
            if (zoomOutRequest > 0) mapViewRef[0]?.controller?.zoomOut()
        }

        // Focus point address marker handling and smooth camera animation
        LaunchedEffect(focusPoint) {
            val mapView = mapViewRef[0] ?: return@LaunchedEffect

            lastFocusPoint[0]?.let { _ ->
                focusMarkerRef[0]?.let { marker ->
                    mapView.overlays.remove(marker)
                }
                focusMarkerRef[0] = null
            }

            focusPoint?.let { newPoint ->
                val osmPt = OsmGeoPoint(newPoint.latitude, newPoint.longitude)
                val marker = Marker(mapView).apply {
                    position = osmPt
                    icon = createBlueAddressPinIcon(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Найденный адрес"
                }
                mapView.overlays.add(marker)
                focusMarkerRef[0] = marker
                lastFocusPoint[0] = osmPt

                mapView.controller.animateTo(osmPt)
                mapView.postDelayed({
                    if (mapView.zoomLevelDouble < 16.0) {
                        mapView.controller.setZoom(16.0)
                    }
                }, 500)
            }
        }

        var lastRenderedStationIds by remember { mutableStateOf<List<Int>>(emptyList()) }
        var lastRenderedUserLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        var lastRenderedRoutePoints by remember { mutableStateOf<List<GeoPoint>?>(null) }
        var lastDarkMode by remember { mutableStateOf<Boolean?>(null) }

        AndroidView(
            factory = { ctx ->
                val config = Configuration.getInstance()
                config.load(ctx, ctx.getSharedPreferences("osmdroid", 0))
                config.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
                config.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
                config.tileFileSystemCacheMaxBytes = 512 * 1024 * 1024L
                config.tileFileSystemThreads = 8
                config.tileDownloadThreads = 8
                config.userAgentValue = ctx.packageName

                val tileProvider = MapTileProviderBasic(ctx, TileSourceFactory.MAPNIK)

                MapView(ctx, tileProvider).apply {
                    setTilesScaledToDpi(true)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)

                    val userPt = userLocation?.toGeoPoint()
                    val centerPoint = if (userPt != null && userPt.latitude != 0.0 && userPt.longitude != 0.0) {
                        userPt
                    } else {
                        OsmGeoPoint(55.1644, 61.4368)
                    }
                    controller.setZoom(if (userPt != null && userPt.latitude != 0.0) 16.0 else 13.0)
                    controller.setCenter(centerPoint)
                    timber.log.Timber.tag("OsmMapView").d("Camera center set to: %s, zoom: %.1f", centerPoint, if (userPt != null && userPt.latitude != 0.0) 16.0 else 13.0)
                    mapViewRef[0] = this
                }
            },
            modifier = modifier,
            update = { mapView ->
                if (lastDarkMode != isDarkMode) {
                    lastDarkMode = isDarkMode
                    if (isDarkMode) {
                        mapView.setBackgroundColor(android.graphics.Color.parseColor(MAP_BACKGROUND))
                        mapView.setTileSource(TileSourceFactory.MAPNIK)
                        mapView.overlayManager.tilesOverlay.setColorFilter(
                            ColorMatrixColorFilter(OSM_DARK_COLOR_MATRIX)
                        )
                    } else {
                        mapView.setBackgroundColor(android.graphics.Color.WHITE)
                        mapView.setTileSource(TileSourceFactory.MAPNIK)
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    }
                }

                val currentStationIds = stationItems.map { it.stationId }
                val currentLocPair = userLocation?.let { Pair(it.latitude, it.longitude) }
                val currentRoutePoints = route?.points

                val isStationChange = currentStationIds != lastRenderedStationIds
                val isLocChange = currentLocPair != lastRenderedUserLoc
                val isRouteChange = currentRoutePoints != lastRenderedRoutePoints

                if (!isStationChange && !isLocChange && !isRouteChange && mapView.overlays.size > 0) {
                    return@AndroidView
                }

                lastRenderedStationIds = currentStationIds
                lastRenderedUserLoc = currentLocPair
                lastRenderedRoutePoints = currentRoutePoints

                mapView.overlays.removeAll { it is Marker || it is Polyline }

                val routeGeoPoints = route?.points?.map { OsmGeoPoint(it.latitude, it.longitude) }?.let { pts ->
                    processRoutePoints(pts, userLocation?.toGeoPoint())
                }

                userLocation?.let { location ->
                    val dot = locationDotRef[0] ?: MyLocationDot().also { overlay ->
                        locationDotRef[0] = overlay
                        mapView.overlays.add(0, overlay)
                    }
                    dot.update(mapView, location, routeGeoPoints)
                }

                var renderedMarkers = 0
                var invalidMarkers = 0

                stationItems.forEach { item ->
                    if (item.latitude == 0.0 || item.longitude == 0.0 ||
                        item.latitude !in 54.0..56.0 || item.longitude !in 60.0..63.0) {
                        invalidMarkers++
                    } else {
                        val marker = Marker(mapView)
                        marker.position = OsmGeoPoint(item.latitude, item.longitude)
                        marker.title = item.title
                        marker.snippet = item.snippet
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.icon = createColoredMarker(context, item.markerColor)
                        marker.setOnMarkerClickListener { _, _ ->
                            onStationClick(item.stationId)
                            true
                        }
                        mapView.overlays.add(marker)
                        renderedMarkers++
                    }
                }

                MapDiagnosticsTracker.pinsFeaturesCount = renderedMarkers
                timber.log.Timber.tag("OsmMapView").i(
                    "MAP_DIAGNOSTIC registry=%d markers=%d invalid=%d",
                    stationItems.size,
                    renderedMarkers,
                    invalidMarkers
                )

                focusMarkerRef[0]?.let { marker ->
                    mapView.overlays.add(marker)
                }

                route?.let { activeRoute ->
                    try {
                        val density = context.resources.displayMetrics.density

                        var rawOsmPoints = activeRoute.points.map { OsmGeoPoint(it.latitude, it.longitude) }
                        rawOsmPoints = processRoutePoints(rawOsmPoints, userLocation?.toGeoPoint())
                        val filteredPoints = mutableListOf<OsmGeoPoint>()
                        for (pt in rawOsmPoints) {
                            if (filteredPoints.isEmpty() || filteredPoints.last().distanceToAsDouble(pt) >= 1.0) {
                                filteredPoints.add(pt)
                            }
                        }
                        val osmPoints = filteredPoints
                        if (osmPoints.size >= 2) {
                            val accentColorStr = "#2196F3"
                            val outlineColorStr = "#0D47A1"

                            val mainPolyline = Polyline().apply {
                                setPoints(osmPoints)
                                outlinePaint.color = android.graphics.Color.parseColor(outlineColorStr)
                                outlinePaint.strokeWidth = 9f * density
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                                outlinePaint.isAntiAlias = true
                                paint.color = android.graphics.Color.parseColor(accentColorStr)
                                paint.strokeWidth = 5f * density
                                paint.strokeCap = android.graphics.Paint.Cap.ROUND
                                paint.strokeJoin = android.graphics.Paint.Join.ROUND
                                paint.isAntiAlias = true
                            }
                            mapView.overlays.add(mainPolyline)

                            val finishPoint = activeRoute.points.last()
                            val finishMarker = Marker(mapView).apply {
                                position = OsmGeoPoint(finishPoint.latitude, finishPoint.longitude)
                                icon = createRedPinIcon(context)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Финиш"
                            }
                            mapView.overlays.add(finishMarker)

                            fun fitRoute() {
                                try {
                                    val bounds = BoundingBox.fromGeoPoints(osmPoints)
                                    val dLat = bounds.latitudeSpan
                                    val dLon = bounds.longitudeSpan
                                    val expanded = BoundingBox(
                                        bounds.latNorth + dLat * 0.3,
                                        bounds.lonEast + dLon * 0.8,
                                        bounds.latSouth - dLat * 0.8,
                                        bounds.lonWest - dLon * 0.3
                                    )
                                    mapView.zoomToBoundingBox(expanded, true)
                                    mapView.invalidate()
                                } catch (e: Exception) {
                                    timber.log.Timber.tag("OsmMapView").w("Failed to zoom to route bounding box: %s", e.message)
                                }
                            }
                            mapView.post { fitRoute() }
                            mapView.postDelayed({ fitRoute() }, 300)
                        }
                    } catch (t: Throwable) {
                        timber.log.Timber.tag("RouteDebug").e(t, "route draw failed")
                    }
                }

                mapView.invalidate()
            }
        )

        LaunchedEffect(recenterRequest) {
            if (recenterRequest > 0) {
                userLocation?.toGeoPoint()?.let { loc ->
                    mapViewRef[0]?.controller?.animateTo(loc)
                    mapViewRef[0]?.controller?.setZoom(16.0)
                }
            }
        }
    }
}
