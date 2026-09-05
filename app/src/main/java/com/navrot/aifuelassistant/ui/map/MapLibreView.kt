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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import com.navrot.aifuelassistant.data.UserPreferencesRepository
import com.navrot.aifuelassistant.data.model.GasStation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import timber.log.Timber

// Tile source fallback chain constants
const val TILE_SOURCE_OPENFREEMAP = "openfreemap"
const val TILE_SOURCE_VERSATILES = "versatiles"
const val TILE_SOURCE_OSM_RASTER = "osm_raster"

private const val OPENFREEMAP_LIGHT_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val OPENFREEMAP_DARK_URL = "https://tiles.openfreemap.org/styles/bright"

private const val VERSATILES_LIGHT_URL = "https://tiles.versatiles.org/assets/star.json"
private const val VERSATILES_DARK_URL = "https://tiles.versatiles.org/assets/neutral.json"

private const val OSM_RASTER_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

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
    val scope = rememberCoroutineScope()
    val userPrefsRepo = remember { UserPreferencesRepository(context) }

    val mapViewRef = remember { arrayOfNulls<MapView>(1) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val markerStationMap = remember { mutableMapOf<Long, GasStation>() }
    val activeStationMarkers = remember { mutableMapOf<Int, Marker>() }
    val finishMarkerRef = remember { arrayOfNulls<Marker>(1) }
    val userLocationMarkerRef = remember { arrayOfNulls<Marker>(1) }

    val tileSourceChain = remember {
        listOf(TILE_SOURCE_OPENFREEMAP, TILE_SOURCE_VERSATILES, TILE_SOURCE_OSM_RASTER)
    }
    var currentSourceIndex by remember { mutableIntStateOf(0) }
    var activeTileSource by remember { mutableStateOf(TILE_SOURCE_OPENFREEMAP) }

    // Load initial persisted tile source preference
    LaunchedEffect(Unit) {
        val savedSource = userPrefsRepo.mapTileSource.first()
        if (savedSource != null && tileSourceChain.contains(savedSource)) {
            activeTileSource = savedSource
            currentSourceIndex = tileSourceChain.indexOf(savedSource)
            Timber.tag("MapLibreView").d("Restored tile source preference: %s", savedSource)
        }
    }

    fun buildStyleBuilder(sourceKey: String, darkMode: Boolean): Style.Builder {
        return when (sourceKey) {
            TILE_SOURCE_OPENFREEMAP -> {
                val url = if (darkMode) OPENFREEMAP_DARK_URL else OPENFREEMAP_LIGHT_URL
                Style.Builder().fromUri(url)
            }
            TILE_SOURCE_VERSATILES -> {
                val url = if (darkMode) VERSATILES_DARK_URL else VERSATILES_LIGHT_URL
                Style.Builder().fromUri(url)
            }
            TILE_SOURCE_OSM_RASTER -> {
                val rasterSource = RasterSource("osm-raster-source", TileSet("2.2.0", OSM_RASTER_URL), 256)
                val rasterLayer = RasterLayer("osm-raster-layer", "osm-raster-source")
                if (darkMode) {
                    rasterLayer.setProperties(
                        PropertyFactory.rasterBrightnessMin(0.2f),
                        PropertyFactory.rasterBrightnessMax(0.7f),
                        PropertyFactory.rasterContrast(0.2f),
                        PropertyFactory.rasterSaturation(-0.5f)
                    )
                }
                Style.Builder()
                    .withSource(rasterSource)
                    .withLayer(rasterLayer)
            }
            else -> {
                Style.Builder().fromUri(if (darkMode) OPENFREEMAP_DARK_URL else OPENFREEMAP_LIGHT_URL)
            }
        }
    }

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
        val iconFactory = IconFactory.getInstance(context)

        val diff = MarkerDiffCalculator.calculateDiff(activeStationMarkers.keys, stations) { it.id }

        diff.toRemoveIds.forEach { stationId ->
            val marker = activeStationMarkers.remove(stationId)
            marker?.let {
                map.removeMarker(it)
                markerStationMap.remove(it.id)
            }
        }

        diff.toAdd.forEach { station ->
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
            activeStationMarkers[station.id] = addedMarker
            markerStationMap[addedMarker.id] = station
        }

        diff.toUpdate.forEach { station ->
            val existingMarker = activeStationMarkers[station.id]
            if (existingMarker != null) {
                existingMarker.position = LatLng(station.latitude, station.longitude)
                existingMarker.title = station.name
                existingMarker.snippet = buildStationSnippet(station, selectedFuelTypes)
                val markerColor = getMarkerColor(station, selectedFuelTypes)
                val drawable = createColoredMarker(context, markerColor)
                val bitmap = drawableToBitmap(drawable)
                val icon = iconFactory.fromBitmap(bitmap)
                existingMarker.icon = icon
                markerStationMap[existingMarker.id] = station
            }
        }

        val finishPt = route?.points?.lastOrNull()
        if (finishPt != null) {
            val redPinDrawable = createRedPinIcon(context)
            val redPinBitmap = drawableToBitmap(redPinDrawable)
            val finishIcon = iconFactory.fromBitmap(redPinBitmap)
            val currentFinishMarker = finishMarkerRef[0]
            if (currentFinishMarker != null) {
                currentFinishMarker.position = LatLng(finishPt.latitude, finishPt.longitude)
                currentFinishMarker.icon = finishIcon
            } else {
                val finishMarkerOptions = MarkerOptions()
                    .position(LatLng(finishPt.latitude, finishPt.longitude))
                    .title("Финиш")
                    .icon(finishIcon)
                finishMarkerRef[0] = map.addMarker(finishMarkerOptions)
            }
        } else {
            finishMarkerRef[0]?.let { map.removeMarker(it) }
            finishMarkerRef[0] = null
        }

        val loc = userLocation
        if (loc != null && loc.latitude != 0.0 && loc.longitude != 0.0) {
            val userLocationDrawable = createUserLocationIcon(context)
            val userLocationBitmap = drawableToBitmap(userLocationDrawable)
            val userLocationIcon = iconFactory.fromBitmap(userLocationBitmap)
            val currentLocMarker = userLocationMarkerRef[0]
            if (currentLocMarker != null) {
                currentLocMarker.position = LatLng(loc.latitude, loc.longitude)
                currentLocMarker.icon = userLocationIcon
            } else {
                val locationMarkerOptions = MarkerOptions()
                    .position(LatLng(loc.latitude, loc.longitude))
                    .title("Моё местоположение")
                    .icon(userLocationIcon)
                userLocationMarkerRef[0] = map.addMarker(locationMarkerOptions)
            }
        } else {
            userLocationMarkerRef[0]?.let { map.removeMarker(it) }
            userLocationMarkerRef[0] = null
        }

        map.style?.let { style ->
            updateRouteLayer(style)
        }
    }

    fun applyStyleWithFallback(map: MapLibreMap, sourceIndex: Int) {
        val sourceKey = tileSourceChain.getOrElse(sourceIndex) { TILE_SOURCE_OSM_RASTER }
        activeTileSource = sourceKey
        Timber.tag("MapLibreView").d("Applying style for source [%d/%d]: %s (isDarkMode=%b)",
            sourceIndex + 1, tileSourceChain.size, sourceKey, isDarkMode)

        var fallbackTimerJob: Job? = null
        var tilesLoadedCount = 0
        var hasFailedMapLoad = false

        val mapView = mapViewRef[0]

        val tileActionListener = MapView.OnTileActionListener { tileOp, x, y, z, zoom, sourceId, url ->
            when (tileOp) {
                org.maplibre.android.tile.TileOperation.LoadFromNetwork,
                org.maplibre.android.tile.TileOperation.LoadFromCache -> {
                    tilesLoadedCount++
                    Timber.tag("MapLibreView").d("Tile loaded (%s) [%s]: z=%d (%d,%d), total: %d, url=%s",
                        tileOp.name, sourceKey, zoom, x, y, tilesLoadedCount, url)
                }
                org.maplibre.android.tile.TileOperation.Error -> {
                    Timber.tag("MapLibreView").e("Tile error [%s]: z=%d (%d,%d), url=%s",
                        sourceKey, zoom, x, y, url)
                }
                else -> {}
            }
        }

        val failMapListener = MapView.OnDidFailLoadingMapListener { errorMessage ->
            hasFailedMapLoad = true
            Timber.tag("MapLibreView").e("onDidFailLoadingMap for source %s: %s", sourceKey, errorMessage)
        }

        val finishStyleListener = MapView.OnDidFinishLoadingStyleListener {
            Timber.tag("MapLibreView").i("onDidFinishLoadingStyle for %s", sourceKey)
        }

        mapView?.addOnTileActionListener(tileActionListener)
        mapView?.addOnDidFailLoadingMapListener(failMapListener)
        mapView?.addOnDidFinishLoadingStyleListener(finishStyleListener)

        val styleBuilder = buildStyleBuilder(sourceKey, isDarkMode)
        map.setStyle(styleBuilder) { style ->
            Timber.tag("MapLibreView").d("onDidFinishLoadingStyle completed callback for %s", sourceKey)

            // Fix 1: Apply Russian-only labels to ALL symbol layers (strip name_en / Latin duplicates)
            try {
                for (layer in style.layers) {
                    if (layer is org.maplibre.android.style.layers.SymbolLayer) {
                        layer.setProperties(
                            PropertyFactory.textField(
                                org.maplibre.android.style.expressions.Expression.coalesce(
                                    org.maplibre.android.style.expressions.Expression.get("name:ru"),
                                    org.maplibre.android.style.expressions.Expression.get("name")
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag("MapLibreView").w(e, "Error applying Russian labels to symbol layers")
            }

            updateMarkers(map)
            updateRouteLayer(style)

            if (sourceKey == TILE_SOURCE_OSM_RASTER) {
                // Osm raster fallback loaded successfully
                scope.launch { userPrefsRepo.setMapTileSource(sourceKey) }
            } else {
                // Start 8-second timer to verify tile loading
                fallbackTimerJob = scope.launch {
                    delay(8000L)
                    if (tilesLoadedCount == 0 || hasFailedMapLoad) {
                        Timber.tag("MapLibreView").w("MapChange/Timeout: 8s passed with %d tiles (failed=%b) for source: %s, switching source",
                            tilesLoadedCount, hasFailedMapLoad, sourceKey)
                        mapView?.removeOnTileActionListener(tileActionListener)
                        mapView?.removeOnDidFailLoadingMapListener(failMapListener)
                        mapView?.removeOnDidFinishLoadingStyleListener(finishStyleListener)
                        val nextIdx = (sourceIndex + 1) % tileSourceChain.size
                        currentSourceIndex = nextIdx
                        applyStyleWithFallback(map, nextIdx)
                    } else {
                        Timber.tag("MapLibreView").i("Tile source %s active and loaded %d tiles within timeout", sourceKey, tilesLoadedCount)
                        userPrefsRepo.setMapTileSource(sourceKey)
                    }
                }
            }
        }
    }

    LaunchedEffect(isDarkMode) {
        mapLibreMap?.let { map ->
            applyStyleWithFallback(map, currentSourceIndex)
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
                    val paddingPx = (80 * density).toInt()
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
                setOnTouchListener { view, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE -> {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }
                onCreate(null)
                getMapAsync { map ->
                    applyStyleWithFallback(map, currentSourceIndex)

                    val centerLat = userLocation?.latitude?.takeIf { it != 0.0 } ?: 55.1644
                    val centerLon = userLocation?.longitude?.takeIf { it != 0.0 } ?: 61.4368
                    val initialCenter = LatLng(centerLat, centerLon)
                    val initialZoom = if (userLocation != null && userLocation.latitude != 0.0) 15.0 else 12.0
                    map.cameraPosition = CameraPosition.Builder()
                        .target(initialCenter)
                        .zoom(initialZoom)
                        .build()
                    Timber.tag("MapLibreView").d("Camera initialized at target: %s, zoom: %.1f", initialCenter, initialZoom)

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
