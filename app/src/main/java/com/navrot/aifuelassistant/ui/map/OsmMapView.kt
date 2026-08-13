package com.navrot.aifuelassistant.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.navrot.aifuelassistant.data.model.GasStation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

private const val MAP_BACKGROUND = "#17222B"

/** ColorMatrix tuned for Google Maps dark palette over CARTO Dark Matter (no labels) tiles. */
private val GOOGLE_DARK_COLOR_MATRIX = ColorMatrix(
    floatArrayOf(
        0.88f, 0.04f, 0.12f, 0f, 14f,
        0.03f, 0.90f, 0.10f, 0f, 18f,
        0.06f, 0.08f, 1.02f, 0f, 22f,
        0f, 0f, 0f, 1f, 0f
    )
)

/** Lightening ColorMatrix for labels overlay — makes labels pale blue-gray (#AEC1CF). */
private val LABELS_LIGHTEN_MATRIX = ColorMatrix(
    floatArrayOf(
        2.0f, 0f, 0f, 0f, 50f,
        0f, 2.0f, 0f, 0f, 50f,
        0f, 0f, 2.0f, 0f, 55f,
        0f, 0f, 0f, 1f, 0f
    )
)

/** Creates a Google Maps–style red finish pin (drop shape #EA4335 with dark center dot). */
private fun createRedPinIcon(context: android.content.Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (32 * density).toInt()
    val height = (40 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pinPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#EA4335")
        style = Paint.Style.FILL
    }
    val dotPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#7F1D1D")
        style = Paint.Style.FILL
    }

    val path = Path().apply {
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

    return BitmapDrawable(context.resources, bitmap)
}

@Composable
fun OsmMapView(
    userLocation: UserLocationState?,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    route: MapViewModel.RouteUiState? = null,
    recenterRequest: Int = 0,
    zoomInRequest: Int = 0,
    zoomOutRequest: Int = 0,
    onStationClick: (GasStation) -> Unit
) {
    val context = LocalContext.current
    val mapViewRef = remember { arrayOfNulls<MapView>(1) }
    val locationDotRef = remember { arrayOfNulls<MyLocationDot>(1) }
    val lastFittedRoute = remember { arrayOfNulls<MapViewModel.RouteUiState>(1) }

    LaunchedEffect(zoomInRequest) {
        if (zoomInRequest > 0) mapViewRef[0]?.controller?.zoomIn()
    }
    LaunchedEffect(zoomOutRequest) {
        if (zoomOutRequest > 0) mapViewRef[0]?.controller?.zoomOut()
    }

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
                setTileSource(cartoDarkNoLabelsTileSource())
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                overlayManager.tilesOverlay.setColorFilter(
                    ColorMatrixColorFilter(GOOGLE_DARK_COLOR_MATRIX)
                )

                val labelsSource = XYTileSource(
                    "CartoDB_Dark_Labels",
                    1, 20, 256, ".png",
                    arrayOf(
                        "https://a.basemaps.cartocdn.com/dark_only_labels/",
                        "https://b.basemaps.cartocdn.com/dark_only_labels/",
                        "https://c.basemaps.cartocdn.com/dark_only_labels/",
                        "https://d.basemaps.cartocdn.com/dark_only_labels/"
                    ),
                    "© OpenStreetMap contributors © CARTO"
                )
                val labelsProvider = MapTileProviderBasic(ctx, labelsSource)
                val labelsOverlay = TilesOverlay(labelsProvider, ctx)
                labelsOverlay.setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)
                labelsOverlay.setColorFilter(ColorMatrixColorFilter(LABELS_LIGHTEN_MATRIX))
                overlayManager.add(0, labelsOverlay)

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
                        polyline.paint.color = android.graphics.Color.parseColor("#8AB4F8")
                        polyline.outlinePaint.color = android.graphics.Color.parseColor("#1B4F9C")
                        polyline.outlinePaint.strokeWidth = 12f
                        mapView.overlays.add(polyline)

                        val finishPoint = r.points.last()
                        val finishMarker = Marker(mapView)
                        finishMarker.position = GeoPoint(finishPoint.latitude, finishPoint.longitude)
                        finishMarker.icon = createRedPinIcon(context)
                        finishMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        finishMarker.title = "Финиш"
                        mapView.overlays.add(finishMarker)

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
 * CARTO Dark Matter (no labels) — Google Maps–like dark base; falls back to MAPNIK if unavailable.
 */
private fun cartoDarkNoLabelsTileSource(): org.osmdroid.tileprovider.tilesource.ITileSource =
    try {
        org.osmdroid.tileprovider.tilesource.XYTileSource(
            "CartoDB_Dark_NoLabels",
            1, 20, 256, ".png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/dark_nolabels/",
                "https://b.basemaps.cartocdn.com/dark_nolabels/",
                "https://c.basemaps.cartocdn.com/dark_nolabels/",
                "https://d.basemaps.cartocdn.com/dark_nolabels/"
            ),
            "© OpenStreetMap contributors © CARTO"
        )
    } catch (_: Exception) {
        TileSourceFactory.MAPNIK
    }
