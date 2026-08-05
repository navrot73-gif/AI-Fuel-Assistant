package com.navrot.aifuelassistant.ui.map

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
    // Не Compose-state, чтобы не устраивать циклы рекомпозиции
    val mapViewRef = remember { arrayOfNulls<MapView>(1) }

    AndroidView(
        factory = { ctx ->
            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))
            Configuration.getInstance().osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
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
                userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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
            route?.let { r ->
                try {
                    val osmPoints = r.points.map { GeoPoint(it.latitude, it.longitude) }
                    if (osmPoints.size >= 2) {
                        val polyline = Polyline()
                        polyline.setPoints(osmPoints)
                        polyline.outlinePaint.color = android.graphics.Color.parseColor("#4DB6AC")
                        polyline.outlinePaint.strokeWidth = 10f
                        mapView.overlays.add(polyline)

                        val first = osmPoints.first()
                        val last = osmPoints.last()
                        mapView.controller.setCenter(
                            GeoPoint(
                                (first.latitude + last.latitude) / 2.0,
                                (first.longitude + last.longitude) / 2.0
                            )
                        )
                        mapView.controller.setZoom(14.0)
                    } else {
                        // Точек меньше двух — линию не рисуем
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("RouteDebug", "route draw failed", t)
                }
                // Явно возвращаем Unit, чтобы try/catch не был "выражением"
                Unit
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