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

/** Создаёт СИНЮЮ метку адреса (drop shape #4285F4 с белой точкой в центре). */
private fun createBlueAddressPinIcon(context: android.content.Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (32 * density).toInt()
    val height = (40 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pinPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#4285F4") // Google Blue
        style = Paint.Style.FILL
    }
    val dotPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
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
    focusPoint: GeoPoint? = null,
    onStationClick: (GasStation) -> Unit
) {
    val context = LocalContext.current
    val mapViewRef = remember { arrayOfNulls<MapView>(1) }
    val locationDotRef = remember { arrayOfNulls<MyLocationDot>(1) }
    val lastFittedRoute = remember { arrayOfNulls<MapViewModel.RouteUiState>(1) }
    val focusMarkerRef = remember { arrayOfNulls<Marker>(1) }
    val lastFocusPoint = remember { arrayOfNulls<GeoPoint>(1) }

    LaunchedEffect(zoomInRequest) {
        if (zoomInRequest > 0) mapViewRef[0]?.controller?.zoomIn()
    }
    LaunchedEffect(zoomOutRequest) {
        if (zoomOutRequest > 0) mapViewRef[0]?.controller?.zoomOut()
    }

    // Обработка focusPoint: добавляем/обновляем/удаляем синюю метку и анимируем камеру
    LaunchedEffect(focusPoint) {
        val mapView = mapViewRef[0] ?: return@LaunchedEffect
        
        // Удаляем старую метку если была
        lastFocusPoint[0]?.let { oldPoint ->
            focusMarkerRef[0]?.let { marker ->
                mapView.overlays.remove(marker)
            }
            focusMarkerRef[0] = null
        }
        
        focusPoint?.let { newPoint ->
            // Создаём синюю метку адреса
            val marker = Marker(mapView).apply {
                position = GeoPoint(newPoint.latitude, newPoint.longitude)
                icon = createBlueAddressPinIcon(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Найденный адрес"
            }
            mapView.overlays.add(marker)
            focusMarkerRef[0] = marker
            lastFocusPoint[0] = newPoint
            
            // Плавно перемещаем камеру к точке
            mapView.controller.animateTo(GeoPoint(newPoint.latitude, newPoint.longitude))
            // Устанавливаем зум подходящий для адреса (16-17)
            mapView.postDelayed({
                if (mapView.zoomLevelDouble < 16.0) {
                    mapView.controller.setZoom(16.0)
                }
            }, 500)
        }
    }

    AndroidView(
        factory = { ctx ->
            val config = Configuration.getInstance()
            // Use load(context, prefsName) instead of deprecated load(context, prefs)
            config.load(ctx, ctx.getSharedPreferences("osmdroid", 0))
            config.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            config.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
            config.tileFileSystemCacheMaxBytes = 512 * 1024 * 1024L
            config.tileFileSystemThreads = 12  // Tile cache: 12 threads
            config.tileDownloadThreads = 12    // Tile download: 12 threads
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
                controller.setZoom(if (userLocation != null) 16.0 else 13.0)
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

            // Добавляем синюю метку фокуса обратно (после очистки overlays)
            focusMarkerRef[0]?.let { marker ->
                mapView.overlays.add(marker)
            }

            if (route == null) {
                lastFittedRoute[0] = null
            } else {
                val r = route
                try {
                    val osmPoints = r.points.map { GeoPoint(it.latitude, it.longitude) }
                    if (osmPoints.size >= 2) {
                        val density = context.resources.displayMetrics.density

                        // Glow Polyline (под ней, width 12dp, color #40C4FF with alpha 0.18 -> #2E40C4FF)
                        val glowPolyline = Polyline().apply {
                            setPoints(osmPoints)
                            outlinePaint.color = android.graphics.Color.TRANSPARENT
                            outlinePaint.strokeWidth = 0f
                            paint.color = android.graphics.Color.parseColor("#2E40C4FF")
                            paint.strokeWidth = 12f * density
                            paint.strokeCap = android.graphics.Paint.Cap.ROUND
                            paint.strokeJoin = android.graphics.Paint.Join.ROUND
                            paint.isAntiAlias = true
                        }
                        mapView.overlays.add(glowPolyline)

                        // Main Polyline (width 6dp, color #40C4FF, cap ROUND, join ROUND)
                        val mainPolyline = Polyline().apply {
                            setPoints(osmPoints)
                            outlinePaint.color = android.graphics.Color.TRANSPARENT
                            outlinePaint.strokeWidth = 0f
                            paint.color = android.graphics.Color.parseColor("#40C4FF")
                            paint.strokeWidth = 6f * density
                            paint.strokeCap = android.graphics.Paint.Cap.ROUND
                            paint.strokeJoin = android.graphics.Paint.Join.ROUND
                            paint.isAntiAlias = true
                        }
                        mapView.overlays.add(mainPolyline)

                        val finishPoint = r.points.last()
                        val finishMarker = Marker(mapView).apply {
                            position = GeoPoint(finishPoint.latitude, finishPoint.longitude)
                            icon = createRedPinIcon(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Финиш"
                        }
                        mapView.overlays.add(finishMarker)

                        if (lastFittedRoute[0] !== r) {
                            lastFittedRoute[0] = r
                            mapView.post {
                                try {
                                    val bounds = BoundingBox.fromGeoPoints(osmPoints)
                                    val leftPx = 48f * density
                                    val topPx = 96f * density
                                    val rightPx = 104f * density
                                    val bottomPx = 150f * density

                                    val mapWidth = mapView.width.toFloat()
                                    val mapHeight = mapView.height.toFloat()

                                    val freeWidth = mapWidth - leftPx - rightPx
                                    val freeHeight = mapHeight - topPx - bottomPx

                                    if (freeWidth > 0f && freeHeight > 0f && mapWidth > 0f && mapHeight > 0f) {
                                        val latSpan = bounds.latNorth - bounds.latSouth
                                        val lonSpan = bounds.lonEast - bounds.lonWest

                                        val safeLatSpan = maxOf(latSpan, 0.001)
                                        val safeLonSpan = maxOf(lonSpan, 0.001)

                                        val extended = BoundingBox(
                                            bounds.latNorth + safeLatSpan * (topPx / freeHeight),
                                            bounds.lonEast + safeLonSpan * (rightPx / freeWidth),
                                            bounds.latSouth - safeLatSpan * (bottomPx / freeHeight),
                                            bounds.lonWest - safeLonSpan * (leftPx / freeWidth)
                                        )
                                        mapView.zoomToBoundingBox(extended, false)
                                    } else {
                                        mapView.zoomToBoundingBox(bounds, false)
                                    }
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
                mapViewRef[0]?.controller?.setZoom(16.0)
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
