package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.navrot.aifuelassistant.data.model.GasStation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable
fun OsmMapView(
    userLocation: GeoPoint?,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    onStationClick: (GasStation) -> Unit
) {
    val context = LocalContext.current

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
            mapView.overlays.removeAll { it is Marker }

            userLocation?.let { location ->
                mapView.controller.setCenter(location)
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
            mapView.invalidate()
        }
    )
}