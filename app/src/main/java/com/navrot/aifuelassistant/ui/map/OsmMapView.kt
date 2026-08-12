package com.navrot.aifuelassistant.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.preference.PreferenceManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun OsmMapView(
    userLocation: GeoPoint?,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    route: MapViewModel.RouteUiState? = null,
    recenterRequest: Int = 0,
    onStationClick: (GasStation) -> Unit
) {
    val context = LocalContext.current
    val mapViewRef = remember { arrayOfNulls<MapView>(1) }
    // Чтобы подгонять масштаб один раз на каждый новый маршрут
    val lastFittedRoute = remember { arrayOfNulls<MapViewModel.RouteUiState>(1) }

    AndroidView(
        factory = { ctx ->
            val config = Configuration.getInstance()
            config.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            config.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            config.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
            config.tileFileSystemCacheMaxBytes = 512 * 1024 * 1024L
            config.userAgentValue = ctx.packageName

            MapView(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
                setTileSource(darkTileSource())
                setMultiTouchControls(true)
                // Ночной тёмно-синий тон в стиле Google Maps:
                // приглушаем светлый Voyager цветофильтром через сеттер
                overlayManager.tilesOverlay.setColorFilter(
                    ColorMatrixColorFilter(
                        ColorMatrix(
                                floatArrayOf(
                                3.2f, 0f, 0f, 0f, 30f,
                                0f, 3.2f, 0f, 0f, 36f,
                                0f, 0f, 3.6f, 0f, 60f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                )
                val centerPoint = userLocation ?: GeoPoint(55.1644, 61.4368)
                controller.setZoom(13.0)
                controller.setCenter(centerPoint)
                mapViewRef[0] = this
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { mapView ->
            mapView.overlays.removeAll { it is Marker || it is Polyline }

                        userLocation?.let { location ->
                val userMarker = Marker(mapView)
                userMarker.position = location
                userMarker.title = "📍 Вы здесь"
                userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                userMarker.icon = createColoredMarker(
                    context,
                                    androidx.compose.ui.graphics.Color(0xFF1E88E5)
                )
                mapView.overlays.add(userMarker)
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

            // Рисуем маршрут
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

                        // Один раз на маршрут: вписываем старт и финиш в экран
                        if (lastFittedRoute[0] !== r) {
                            lastFittedRoute[0] = r
                            mapView.post {
                                try {
                                    val bounds = BoundingBox.fromGeoPoints(osmPoints)
                                    val latSpan = bounds.latNorth - bounds.latSouth
                                    // Асимметричная рамка: сверху запас 15% (маркер не
                                    // прилипает к шапке), снизу 45% (кнопка "Маршрут"
                                    // и панели) — маршрут и точки в комфортной зоне.
                                    val extended = BoundingBox(
                                        bounds.latNorth + maxOf(latSpan * 0.15, 0.001),
                                        bounds.lonEast,
                                        bounds.latSouth - maxOf(latSpan * 0.45, 0.003),
                                        bounds.lonWest
                                    )
                                    mapView.zoomToBoundingBox(extended, false, 100)
                                    // Не приближать слишком сильно короткий маршрут
                                    if (mapView.zoomLevelDouble > 16.0) {
                                        mapView.controller.setZoom(16.0)
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    } else {
                        // Точек меньше двух — линию не рисуем
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("RouteDebug", "route draw failed", t)
                }
            }

            mapView.invalidate()
        }
    )

    // FAB 📍: плавный возврат камеры к пользователю
    LaunchedEffect(recenterRequest) {
        if (recenterRequest > 0) {
            userLocation?.let { loc ->
                mapViewRef[0]?.controller?.animateTo(loc)
                mapViewRef[0]?.controller?.setZoom(15.0)
            }
        }
    }
}

/**
 * Тёмные тайлы CartoDB Dark Matter — единый стиль с тёмной темой приложения
 * и с Google Maps в тёмном режиме.
 */
private fun darkTileSource(): org.osmdroid.tileprovider.tilesource.XYTileSource =
    org.osmdroid.tileprovider.tilesource.XYTileSource(
        "CartoDB_Dark", 1, 20, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/"
        ),
        "© OpenStreetMap contributors, © CARTO"
    )
