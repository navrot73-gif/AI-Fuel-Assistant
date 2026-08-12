package com.navrot.aifuelassistant.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.navrot.aifuelassistant.data.model.GasStation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

private const val MAP_BACKGROUND = "#17222B"

/** ColorMatrix tuned for Google Maps dark palette over CARTO Dark Matter tiles. */
private val GOOGLE_DARK_COLOR_MATRIX = ColorMatrix(
    floatArrayOf(
        0.88f, 0.04f, 0.12f, 0f, 14f,
        0.03f, 0.90f, 0.10f, 0f, 18f,
        0.06f, 0.08f, 1.02f, 0f, 22f,
        0f, 0f, 0f, 1f, 0f
    )
)

@Composable
fun OsmMapView(
    userLocation: UserLocationState?,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    route: MapViewModel.RouteUiState? = null,
    recenterRequest: Int = 0,
    onStationClick: (GasStation) -> Unit
) {
    val context = LocalContext.current
    val mapViewRef = remember { arrayOfNulls<MapView>(1) }
    val locationDotRef = remember { arrayOfNulls<MyLocationDot>(1) }
    val lastFittedRoute = remember { arrayOfNulls<MapViewModel.RouteUiState>(1) }

    AndroidView(
        factory = { ctx ->
            val config = Configuration.getInstance()
            config.load(ctx, ctx.getSharedPreferences("osmdroid", 0))
            config.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            config.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
            config.tileFileSystemCacheMaxBytes = 512 * 1024 * 1024L
            config.userAgentValue = ctx.packageName

            MapView(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor(MAP_BACKGROUND))
                setTileSource(cartoDarkTileSource())
                setMultiTouchControls(true)
                overlayManager.tilesOverlay.setColorFilter(
                    ColorMatrixColorFilter(GOOGLE_DARK_COLOR_MATRIX)
                )
                val centerPoint = userLocation?.toGeoPoint() ?: GeoPoint(55.1644, 61.4368)
                controller.setZoom(13.0)
                controller.setCenter(centerPoint)
                mapViewRef[0] = this
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { mapView ->
            mapView.overlays.removeAll { it is Marker || it is Polyline }

            val routeGeoPoints = route?.points?.map { GeoPoint(it.latitude, it.longitude) }

            userLocation?.let { location ->
                val dot = locationDotRef[0] ?: MyLocationDot().also { overlay ->
                    locationDotRef[0] = overlay
                    mapView.overlays.add(0, overlay)
                }
                dot.update(mapView, location, routeGeoPoints)
            }

            stations.forEach { station ->
                val marker = Marker(mapView)
                marker.position = GeoPoint(station.latitude, station.longitude)
                marker.title = station.name
                marker.snippet = buildStationSnippet(station, selectedFuelTypes)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val markerColor = getMarkerColor(station, selectedFuelTypes)
                marker.icon = createColoredMarker(context, markerColor)
                marker.setOnMarkerClickListener { _, _ ->
                    onStationClick(station)
                    true
                }
                mapView.overlays.add(marker)
            }

            if (route == null) {
                lastFittedRoute[0] = null
            } else {
                val r = route
                try {
                    val osmPoints = r.points.map { GeoPoint(it.latitude, it.longitude) }
                    if (osmPoints.size >= 2) {
                        val polyline = Polyline()
                        polyline.setPoints(osmPoints)
                        polyline.outlinePaint.color = android.graphics.Color.parseColor("#4DB6AC")
                        polyline.outlinePaint.strokeWidth = 12f
                        mapView.overlays.add(polyline)

                        if (lastFittedRoute[0] !== r) {
                            lastFittedRoute[0] = r
                            mapView.post {
                                try {
                                    val bounds = BoundingBox.fromGeoPoints(osmPoints)
                                    val latSpan = bounds.latNorth - bounds.latSouth
                                    val extended = BoundingBox(
                                        bounds.latNorth + maxOf(latSpan * 0.15, 0.001),
                                        bounds.lonEast,
                                        bounds.latSouth - maxOf(latSpan * 0.45, 0.003),
                                        bounds.lonWest
                                    )
                                    mapView.zoomToBoundingBox(extended, false, 100)
                                    if (mapView.zoomLevelDouble > 16.0) {
                                        mapView.controller.setZoom(16.0)
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("RouteDebug", "route draw failed", t)
                }
            }

            mapView.invalidate()
        }
    )

    LaunchedEffect(recenterRequest) {
        if (recenterRequest > 0) {
            userLocation?.toGeoPoint()?.let { loc ->
                mapViewRef[0]?.controller?.animateTo(loc)
                mapViewRef[0]?.controller?.setZoom(15.0)
            }
        }
    }
}

/**
 * CARTO Dark Matter — Google Maps–like dark base; falls back to MAPNIK if unavailable.
 */
private fun cartoDarkTileSource(): org.osmdroid.tileprovider.tilesource.ITileSource =
    try {
        org.osmdroid.tileprovider.tilesource.XYTileSource(
            "CartoDB_Dark",
            1, 20, 256, ".png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/dark_all/",
                "https://b.basemaps.cartocdn.com/dark_all/",
                "https://c.basemaps.cartocdn.com/dark_all/",
                "https://d.basemaps.cartocdn.com/dark_all/"
            ),
            "© OpenStreetMap contributors © CARTO"
        )
    } catch (_: Exception) {
        TileSourceFactory.MAPNIK
    }
