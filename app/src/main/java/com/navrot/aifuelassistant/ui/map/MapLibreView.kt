package com.navrot.aifuelassistant.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import com.navrot.aifuelassistant.data.model.GasStation
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import timber.log.Timber

private const val LIGHT_STYLE_URL = "https://demotiles.maplibre.org/style.json"
// CartoDB Dark Matter tile style fallback
private const val DARK_STYLE_URL = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

private const val ROUTE_SOURCE_ID = "osrm-route-source"
private const val ROUTE_CASING_LAYER_ID = "osrm-route-casing-layer"
private const val ROUTE_LINE_LAYER_ID = "osrm-route-line-layer"

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 32
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 32
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun MapLibreView(
    userLocation: UserLocationState?,
    stations: List<GasStation>,
    selectedFuelTypes: Set<String>,
    route: MapViewModel.RouteOptionUiState? = null,
    isDarkMode: Boolean = false,
    recenterRequest: Int = 0,
    zoomInRequest: Int = 0,
    zoomOutRequest: Int = 0,
    onStationClick: (GasStation) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapViewRef = remember { arrayOfNulls<MapView>(1) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val markerStationMap = remember { mutableMapOf<Long, GasStation>() }

    fun updateRouteLayer(style: Style) {
        val existingSource = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
        val existingCasing = style.getLayer(ROUTE_CASING_LAYER_ID)
        val existingLine = style.getLayer(ROUTE_LINE_LAYER_ID)

        val currentRoute = route
        if (currentRoute == null || currentRoute.points.size < 2) {
            existingLine?.let { style.removeLayer(it) }
            existingCasing?.let { style.removeLayer(it) }
            existingSource?.let { style.removeSource(it) }
            return
        }

        val points = currentRoute.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        val lineString = LineString.fromLngLats(points)
        val featureCollection = FeatureCollection.fromFeature(Feature.fromGeometry(lineString))

        if (existingSource != null) {
            existingSource.setGeoJson(featureCollection)
        } else {
            val geoJsonSource = GeoJsonSource(ROUTE_SOURCE_ID, featureCollection)
            style.addSource(geoJsonSource)

            val casingLayer = LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.lineColor(android.graphics.Color.parseColor("#0D47A1")),
                    PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
            val lineLayer = LineLayer(ROUTE_LINE_LAYER_ID, ROUTE_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.lineColor(android.graphics.Color.parseColor("#2196F3")),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
            style.addLayer(casingLayer)
            style.addLayerAbove(lineLayer, ROUTE_CASING_LAYER_ID)
        }
    }

    fun updateMarkers(map: MapLibreMap) {
        map.clear()
        markerStationMap.clear()

        val iconFactory = IconFactory.getInstance(context)

        stations.forEach { station ->
            val markerColor = getMarkerColor(station, selectedFuelTypes)
            val drawable = createColoredMarker(context, markerColor)
            val bitmap = drawableToBitmap(drawable)
            val icon = iconFactory.fromBitmap(bitmap)

            val markerOptions = MarkerOptions()
                .position(LatLng(station.latitude, station.longitude))
                .title(station.name)
                .snippet(buildStationSnippet(station, selectedFuelTypes))
                .icon(icon)

            val addedMarker: Marker = map.addMarker(markerOptions)
            markerStationMap[addedMarker.id] = station
        }

        route?.points?.lastOrNull()?.let { finishPt ->
            val redPinDrawable = createRedPinIcon(context)
            val redPinBitmap = drawableToBitmap(redPinDrawable)
            val finishIcon = iconFactory.fromBitmap(redPinBitmap)
            val finishMarkerOptions = MarkerOptions()
                .position(LatLng(finishPt.latitude, finishPt.longitude))
                .title("Финиш")
                .icon(finishIcon)
            map.addMarker(finishMarkerOptions)
        }

        map.style?.let { style ->
            updateRouteLayer(style)
        }
    }

    LaunchedEffect(isDarkMode) {
        mapLibreMap?.let { map ->
            val styleUri = if (isDarkMode) DARK_STYLE_URL else LIGHT_STYLE_URL
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                Timber.tag("MapLibreView").d("MapLibre style updated for dark mode=$isDarkMode")
                updateMarkers(map)
                updateRouteLayer(style)
            }
        }
    }

    LaunchedEffect(route) {
        mapLibreMap?.let { map ->
            map.style?.let { style ->
                updateRouteLayer(style)
            }
            updateMarkers(map)

            val pts = route?.points
            if (pts != null && pts.size >= 2) {
                try {
                    val builder = LatLngBounds.Builder()
                    pts.forEach { pt ->
                        builder.include(LatLng(pt.latitude, pt.longitude))
                    }
                    val bounds = builder.build()
                    val density = context.resources.displayMetrics.density
                    val paddingPx = (64 * density).toInt()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
                } catch (e: Exception) {
                    Timber.tag("MapLibreView").w("Failed to fit bounds for route: %s", e.message)
                }
            }
        }
    }

    LaunchedEffect(zoomInRequest) {
        if (zoomInRequest > 0) {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
    }

    LaunchedEffect(zoomOutRequest) {
        if (zoomOutRequest > 0) {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }
    }

    LaunchedEffect(recenterRequest) {
        if (recenterRequest > 0) {
            userLocation?.let { loc ->
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0)
                )
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).apply {
                mapViewRef[0] = this
                onCreate(null)
                getMapAsync { map ->
                    val styleUri = if (isDarkMode) DARK_STYLE_URL else LIGHT_STYLE_URL
                    map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                        Timber.tag("MapLibreView").d("MapLibre initial style loaded")
                        updateMarkers(map)
                        updateRouteLayer(style)
                    }

                    val initialCenter = userLocation?.let { LatLng(it.latitude, it.longitude) }
                        ?: LatLng(55.1644, 61.4368)
                    val initialZoom = if (userLocation != null) 15.0 else 12.0
                    map.cameraPosition = CameraPosition.Builder()
                        .target(initialCenter)
                        .zoom(initialZoom)
                        .build()

                    map.setOnMarkerClickListener { marker ->
                        val station = markerStationMap[marker.id]
                        if (station != null) {
                            onStationClick(station)
                            true
                        } else {
                            false
                        }
                    }

                    mapLibreMap = map
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { _ ->
            mapLibreMap?.let { map ->
                updateMarkers(map)
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
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }

        val currentState = lifecycleOwner.lifecycle.currentState
        mapViewRef[0]?.let { mapView ->
            if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStart()
            }
            if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                mapView.onResume()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef[0]?.onDestroy()
        }
    }
}
