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
    val width = (22 * density).toInt()
    val height = (30 * density).toInt()
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

    val dotRadius = (3 * density).toInt()
    canvas.drawCircle(width / 2f, height * 0.28f, dotRadius.toFloat(), dotPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private val ROUTE_ACCENT_COLORS = listOf(
    "#2196F3", // 0: Быстрый (Синий)
    "#4CAF50", // 1: Без пробок (Зелёный)
    "#9C27B0"  // 2: Альтернативный (Фиолетовый)
)

private val ROUTE_OUTLINE_COLORS = listOf(
    "#0D47A1", // 0: Тёмно-синий
    "#1B5E20", // 1: Тёмно-зелёный
    "#4A148C"  // 2: Тёмно-фиолетовый
)

private fun processRoutePoints(points: List<GeoPoint>, userLocationPt: GeoPoint?): List<GeoPoint> {
    if (points.isEmpty()) return points
    var pts = if (userLocationPt != null) {
        listOf(userLocationPt) + points.drop(1)
    } else {
        points
    }
    if (pts.size >= 3) {
        val p0 = pts[0]
        val p1 = pts[1]
        val p2 = pts[2]
        val v1x = p1.longitude - p0.longitude
        val v1y = p1.latitude - p0.latitude
        val v2x = p2.longitude - p1.longitude
        val v2y = p2.latitude - p1.latitude
        val dot = v1x * v2x + v1y * v2y
        val len1 = kotlin.math.hypot(v1x, v1y)
        val len2 = kotlin.math.hypot(v2x, v2y)
        if (len1 > 0 && len2 > 0 && dot < -0.5 * len1 * len2) {
            pts = listOf(p0) + pts.drop(2)
        }
    }
    return pts
}

@Composable
fun OsmMapView(
    userLocation: UserLocationState?,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    route: MapViewModel.RouteOptionUiState? = null,
    routeOptions: List<MapViewModel.RouteOptionUiState> = emptyList(),
    activeRouteIndex: Int = 0,
    recenterRequest: Int = 0,
    zoomInRequest: Int = 0,
    zoomOutRequest: Int = 0,
    focusPoint: GeoPoint? = null,
    onStationClick: (GasStation) -> Unit
) {
    val context = LocalContext.current
    val mapViewRef = remember { arrayOfNulls<MapView>(1) }
    val locationDotRef = remember { arrayOfNulls<MyLocationDot>(1) }
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

            val activeRoute = route ?: routeOptions.getOrNull(activeRouteIndex)
            val routeGeoPoints = activeRoute?.points?.map { GeoPoint(it.latitude, it.longitude) }?.let { pts ->
                processRoutePoints(pts, userLocation?.toGeoPoint())
            }

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

            val effectiveOptions = if (routeOptions.isNotEmpty()) routeOptions
                else if (route != null) listOf(route) else emptyList()

            if (effectiveOptions.isNotEmpty()) {
                try {
                    val density = context.resources.displayMetrics.density

                    // Draw inactive routes first (dimmed)
                    effectiveOptions.forEachIndexed { idx, r ->
                        if (idx != activeRouteIndex && routeOptions.size > 1) {
                            var rawOsmPoints = r.points.map { GeoPoint(it.latitude, it.longitude) }
                            rawOsmPoints = processRoutePoints(rawOsmPoints, userLocation?.toGeoPoint())
                            val filteredPoints = mutableListOf<GeoPoint>()
                            for (pt in rawOsmPoints) {
                                if (filteredPoints.isEmpty() || filteredPoints.last().distanceToAsDouble(pt) >= 1.0) {
                                    filteredPoints.add(pt)
                                }
                            }
                            if (filteredPoints.size >= 2) {
                                val polyline = Polyline().apply {
                                    setPoints(filteredPoints)
                                    outlinePaint.color = android.graphics.Color.parseColor("#33FFFFFF")
                                    outlinePaint.strokeWidth = 6f * density
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                                    outlinePaint.isAntiAlias = true
                                    paint.color = android.graphics.Color.parseColor("#8078909C")
                                    paint.strokeWidth = 4f * density
                                    paint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    paint.strokeJoin = android.graphics.Paint.Join.ROUND
                                    paint.isAntiAlias = true
                                }
                                mapView.overlays.add(polyline)
                            }
                        }
                    }

                    // Draw active route on top
                    val activeOption = effectiveOptions.getOrNull(activeRouteIndex) ?: effectiveOptions.first()
                    var rawOsmPoints = activeOption.points.map { GeoPoint(it.latitude, it.longitude) }
                    rawOsmPoints = processRoutePoints(rawOsmPoints, userLocation?.toGeoPoint())
                    val filteredPoints = mutableListOf<GeoPoint>()
                    for (pt in rawOsmPoints) {
                        if (filteredPoints.isEmpty() || filteredPoints.last().distanceToAsDouble(pt) >= 1.0) {
                            filteredPoints.add(pt)
                        }
                    }
                    val osmPoints = filteredPoints
                    if (osmPoints.size >= 2) {
                        val accentColorStr = ROUTE_ACCENT_COLORS.getOrElse(activeRouteIndex) { "#2196F3" }
                        val outlineColorStr = ROUTE_OUTLINE_COLORS.getOrElse(activeRouteIndex) { "#0D47A1" }

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

                        val finishPoint = activeOption.points.last()
                        val finishMarker = Marker(mapView).apply {
                            position = GeoPoint(finishPoint.latitude, finishPoint.longitude)
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
                            } catch (_: Exception) { }
                        }
                        mapView.post { fitRoute() }
                        mapView.postDelayed({ fitRoute() }, 300)
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
