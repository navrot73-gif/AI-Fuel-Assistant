package com.navrot.aifuelassistant.ui.map

import org.osmdroid.util.BoundingBox
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
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
    routePoints: List<GeoPoint> = emptyList(),
    recenterTick: Int = 0,
    onStationClick: (GasStation) -> Unit
) {
    val context = LocalContext.current
    // Держатели, чтобы не дёргать камеру карты на каждой перекомпоновке
    val lastRouteHash = remember { intArrayOf(0) }
    val lastRecenter = remember { intArrayOf(recenterTick) }
    val centeredOnce = remember { intArrayOf(0) }

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
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { mapView ->
            mapView.overlays.removeAll { it is Marker || it is Polyline }

            // Один раз центрируем, когда пришла геопозиция
            if (centeredOnce[0] == 0 && userLocation != null) {
                centeredOnce[0] = 1
                mapView.controller.setZoom(13.0)
                mapView.controller.setCenter(userLocation)
            }

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

            // ===== Линия маршрута =====
            if (routePoints.size > 1) {
                val line = Polyline().apply {
                    setPoints(routePoints)
                    outlinePaint.color = FueldeckColors.Teal.toArgb()
                    outlinePaint.strokeWidth = 14f
                    outlinePaint.isAntiAlias = true
                }
                mapView.overlays.add(line)

                val hash = routePoints.hashCode()
                if (hash != lastRouteHash[0]) {
                    lastRouteHash[0] = hash
                    mapView.zoomToBoundingBox(
                        BoundingBox.fromGeoPoints(routePoints).increaseByScale(1.3f),
                        true
                    )
                }
            } else {
                lastRouteHash[0] = 0
            }

            // ===== Кнопка "Моё местоположение" =====
            if (recenterTick != lastRecenter[0]) {
                lastRecenter[0] = recenterTick
                userLocation?.let { mapView.controller.animateTo(it) }
            }

            mapView.invalidate()
        }
    )
}