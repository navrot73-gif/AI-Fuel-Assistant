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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.navrot.aifuelassistant.data.UserPreferencesRepository
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import timber.log.Timber

// Tile source fallback chain constants
const val TILE_SOURCE_OSM_RASTER = "osm_raster"
const val TILE_SOURCE_OPENFREEMAP = "openfreemap"
const val TILE_SOURCE_VERSATILES = "versatiles"

private const val LOCAL_STYLE_ASSET_URL = "asset://map_style_local.json"

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
    focusPoint: Pair<Double, Double>? = null,
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
    val focusMarkerRef = remember { arrayOfNulls<Marker>(1) }
    val lastFocusPoint = remember { arrayOfNulls<Pair<Double, Double>>(1) }

    val tileSourceChain = remember {
        listOf(TILE_SOURCE_OSM_RASTER, TILE_SOURCE_OPENFREEMAP, TILE_SOURCE_VERSATILES)
    }
    var currentSourceIndex by remember { mutableIntStateOf(0) }
    var activeTileSource by remember { mutableStateOf(TILE_SOURCE_OSM_RASTER) }
    val failedSources = remember { mutableSetOf<String>() }

    // Load initial persisted tile source preference
    LaunchedEffect(Unit) {
        val savedSource = userPrefsRepo.mapTileSource.first()
        if (savedSource != null && tileSourceChain.contains(savedSource) && !failedSources.contains(savedSource)) {
            activeTileSource = savedSource
            currentSourceIndex = tileSourceChain.indexOf(savedSource)
            Timber.tag("MapLibreView").d("Restored tile source preference: %s", savedSource)
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

        // Восстановление фокус-маркера после сброса трекеров (P2).
        // resetMarkerTrackers обнуляет focusMarkerRef, но lastFocusPoint сохраняется.
        // Здесь мы пересоздаём синий пин найденного адреса на свежем стиле.
        lastFocusPoint[0]?.let { fp ->
            if (focusMarkerRef[0] == null && fp.first != 0.0 && fp.second != 0.0) {
                val bluePinDrawable = createBlueAddressPinIcon(context)
                val bluePinBitmap = drawableToBitmap(bluePinDrawable)
                val bluePinIcon = iconFactory.fromBitmap(bluePinBitmap)
                val focusMarkerOptions = MarkerOptions()
                    .position(LatLng(fp.first, fp.second))
                    .title("Найденный адрес")
                    .icon(bluePinIcon)
                focusMarkerRef[0] = map.addMarker(focusMarkerOptions)
            }
        }

        map.style?.let { style ->
            updateRouteLayer(style)
        }
    }

    fun attachTileSourceToStyle(style: Style, sourceKey: String) {
        val sourceId = "runtime-tile-source-$sourceKey"
        val layerId = "runtime-tile-layer-$sourceKey"

        // Remove any previous runtime layer & source if exists
        for (key in tileSourceChain) {
            val sId = "runtime-tile-source-$key"
            val lId = "runtime-tile-layer-$key"
            style.getLayer(lId)?.let { style.removeLayer(it) }
            style.getSource(sId)?.let { style.removeSource(it) }
        }

        when (sourceKey) {
            TILE_SOURCE_OSM_RASTER -> {
                val tileSet = TileSet("2.2.0", OSM_RASTER_URL).apply {
                    attribution = "© OpenStreetMap contributors"
                }
                val rasterSource = RasterSource(sourceId, tileSet, 256)
                val rasterLayer = RasterLayer(layerId, sourceId)
                if (isDarkMode) {
                    rasterLayer.setProperties(
                        PropertyFactory.rasterBrightnessMin(0.2f),
                        PropertyFactory.rasterBrightnessMax(0.7f),
                        PropertyFactory.rasterContrast(0.2f),
                        PropertyFactory.rasterSaturation(-0.5f)
                    )
                }
                style.addSource(rasterSource)
                style.addLayerAt(rasterLayer, 0)
            }
            TILE_SOURCE_OPENFREEMAP -> {
                val tileUrl = if (isDarkMode) OPENFREEMAP_DARK_URL else OPENFREEMAP_LIGHT_URL
                val tileSet = TileSet("2.2.0", tileUrl)
                val rasterSource = RasterSource(sourceId, tileSet, 256)
                val rasterLayer = RasterLayer(layerId, sourceId)
                style.addSource(rasterSource)
                style.addLayerAt(rasterLayer, 0)
            }
            TILE_SOURCE_VERSATILES -> {
                val tileUrl = if (isDarkMode) VERSATILES_DARK_URL else VERSATILES_LIGHT_URL
                val tileSet = TileSet("2.2.0", tileUrl)
                val rasterSource = RasterSource(sourceId, tileSet, 256)
                val rasterLayer = RasterLayer(layerId, sourceId)
                style.addSource(rasterSource)
                style.addLayerAt(rasterLayer, 0)
            }
        }
    }

    /**
     * Сброс трекеров маркеров при смене стиля.
     *
     * КОРНЕВОЙ ФИКС (P0-1): MapLibre.setStyle() визуально удаляет все маркеры,
     * но наши трекеры [activeStationMarkers] / [markerStationMap] об этом не знали.
     * MarkerDiffCalculator на следующем updateMarkers видел, что ID уже в existingIds,
     * и не добавлял маркеры заново → «серый лист без пинов» после каждого fallback.
     *
     * Вызывать ПЕРЕД любым setStyle (в начале applyStyleWithFallback и в switchToNextSource).
     */
    fun resetMarkerTrackers(map: MapLibreMap) {
        // 1. Удаляем физические маркеры с карты (на случай, если они ещё висят)
        activeStationMarkers.values.forEach { marker ->
            try { map.removeMarker(marker) } catch (_: Throwable) {}
        }
        finishMarkerRef[0]?.let { m ->
            try { map.removeMarker(m) } catch (_: Throwable) {}
        }
        userLocationMarkerRef[0]?.let { m ->
            try { map.removeMarker(m) } catch (_: Throwable) {}
        }
        focusMarkerRef[0]?.let { m ->
            try { map.removeMarker(m) } catch (_: Throwable) {}
        }
        // 2. Чистим трекеры — теперь updateMarkers добавит все маркеры заново.
        //    focusMarkerRef НЕ обнуляем: LaunchedEffect(focusPoint) пересоздаст его
        //    после setStyle, и процессБ-лог lastFocusPoint сохраним, чтобы знать,
        //    что точку нужно перерисовать.
        activeStationMarkers.clear()
        markerStationMap.clear()
        finishMarkerRef[0] = null
        userLocationMarkerRef[0] = null
        focusMarkerRef[0] = null  // будет пересоздан в LaunchedEffect(focusPoint) или updateMarkers
        Timber.tag("MapLibreView").d("Marker trackers reset (stationMarkers=%d, stationMap=%d)",
            activeStationMarkers.size, markerStationMap.size)
    }

    fun applyStyleWithFallback(map: MapLibreMap, sourceIndex: Int) {
        val sourceKey = tileSourceChain.getOrElse(sourceIndex) { TILE_SOURCE_OSM_RASTER }
        activeTileSource = sourceKey
        MapDiagnosticsTracker.activeTileSource = sourceKey
        Timber.tag("MapLibreView").d("Applying style for source [%d/%d]: %s (isDarkMode=%b)",
            sourceIndex + 1, tileSourceChain.size, sourceKey, isDarkMode)

        // P0-1: сброс трекеров перед каждой сменой стиля — иначе «undead contracts»
        resetMarkerTrackers(map)

        var fallbackTimerJob: Job? = null
        var tilesLoadedCount = 0
        var hasFailedMapLoad = false

        val mapView = mapViewRef[0]

        val tileActionListener = MapView.OnTileActionListener { tileOp, x, y, z, zoom, sourceId, url ->
            when (tileOp) {
                org.maplibre.android.tile.TileOperation.LoadFromNetwork,
                org.maplibre.android.tile.TileOperation.LoadFromCache -> {
                    tilesLoadedCount++
                    MapDiagnosticsTracker.tileStatus = "ok"
                    Timber.tag("MapLibreView").d("Tile loaded (%s) [%s]: z=%d (%d,%d), total: %d, url=%s",
                        tileOp.name, sourceKey, zoom, x, y, tilesLoadedCount, url)
                }
                org.maplibre.android.tile.TileOperation.Error -> {
                    MapDiagnosticsTracker.recordTileFallback("$sourceKey:tile_error")
                    Timber.tag("MapLibreView").e("Tile error [%s]: z=%d (%d,%d), url=%s",
                        sourceKey, zoom, x, y, url)
                }
                else -> {}
            }
        }

        val failMapListener = MapView.OnDidFailLoadingMapListener { errorMessage ->
            hasFailedMapLoad = true
            MapDiagnosticsTracker.recordTileFallback("$sourceKey:map_load_fail")
            Timber.tag("MapLibreView").e("onDidFailLoadingMap for source %s: %s", sourceKey, errorMessage)
        }

        val finishStyleListener = MapView.OnDidFinishLoadingStyleListener {
            Timber.tag("MapLibreView").i("onDidFinishLoadingStyle for %s", sourceKey)
        }

        mapView?.addOnTileActionListener(tileActionListener)
        mapView?.addOnDidFailLoadingMapListener(failMapListener)
        mapView?.addOnDidFinishLoadingStyleListener(finishStyleListener)

        fun switchToNextSource() {
            failedSources.add(sourceKey)
            mapView?.removeOnTileActionListener(tileActionListener)
            mapView?.removeOnDidFailLoadingMapListener(failMapListener)
            mapView?.removeOnDidFinishLoadingStyleListener(finishStyleListener)

            // P0-1: повторный сброс перед рекурсивным applyStyleWithFallback —
            // защищает от гонок, если маркеры успели добавиться между setStyle и switch
            resetMarkerTrackers(map)

            val nextUnfailedIndex = tileSourceChain.indices.firstOrNull { it > sourceIndex && !failedSources.contains(tileSourceChain[it]) }
            if (nextUnfailedIndex != null) {
                currentSourceIndex = nextUnfailedIndex
                applyStyleWithFallback(map, nextUnfailedIndex)
            } else {
                MapDiagnosticsTracker.tileStatus = "fail"
                MapDiagnosticsTracker.recordTileFallback("all_sources_failed_fallback_osmdroid")
                Timber.tag("MapLibreView").e("All tile sources failed! Auto-fallback to osmdroid engine.")

                android.widget.Toast.makeText(
                    context,
                    "Вектор недоступен — классика",
                    android.widget.Toast.LENGTH_LONG
                ).show()

                scope.launch {
                    userPrefsRepo.setMapEngine(UserPreferencesRepository.ENGINE_OSMDROID)
                }
            }
        }

        // Start from local style asset (offline-first!), then attach runtime tile source
        map.setStyle(Style.Builder().fromUri(LOCAL_STYLE_ASSET_URL)) { style ->
            Timber.tag("MapLibreView").d("Local style loaded successfully for source %s", sourceKey)

            try {
                for (layer in style.layers) {
                    if (layer is SymbolLayer) {
                        layer.setProperties(
                            PropertyFactory.textField(
                                Expression.coalesce(
                                    Expression.get("name:ru"),
                                    Expression.get("name")
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag("MapLibreView").w(e, "Error applying Russian labels to symbol layers")
            }

            // Immediately draw markers & route on local style.
            // P0-1: трекеры уже сброшены выше, поэтому updateMarkers добавит все маркеры.
            updateMarkers(map)
            updateRouteLayer(style)

            // Attach dynamic tile source at runtime onto local style
            try {
                attachTileSourceToStyle(style, sourceKey)
            } catch (e: Exception) {
                Timber.tag("MapLibreView").e(e, "Error attaching tile source %s, switching", sourceKey)
                MapDiagnosticsTracker.recordTileFallback("$sourceKey:attach_error")
                switchToNextSource()
                return@setStyle
            }

            // P0-2: таймер больше НЕ переключает источник только из-за tilesLoadedCount==0.
            // Оффлайн = серый фон + пины — это нормальное состояние, не авария.
            // Переключаем только при явной ошибке загрузки карты (onDidFailLoadingMap).
            // Таймаут увеличен с 6 до 10 секунд — даём сети шанс на плохом коннекте.
            fallbackTimerJob = scope.launch {
                delay(10000L)
                if (hasFailedMapLoad) {
                    Timber.tag("MapLibreView").w("MapChange/Timeout: 10s passed with map_load_fail=true for source: %s, switching source",
                        sourceKey)
                    MapDiagnosticsTracker.recordTileFallback("$sourceKey:timeout_10s_map_fail")
                    switchToNextSource()
                } else if (tilesLoadedCount == 0) {
                    // Оффлайн или кеш пуст — НЕ переключаем. Пины уже нарисованы,
                    // тайлы подтянутся когда появится сеть.
                    Timber.tag("MapLibreView").i("Offline-like state: 0 tiles loaded, but no map_load_fail for %s — keeping source, markers visible",
                        sourceKey)
                    MapDiagnosticsTracker.tileStatus = "offline_ok"
                    MapDiagnosticsTracker.activeTileSource = sourceKey
                } else {
                    Timber.tag("MapLibreView").i("Tile source %s active and loaded %d tiles within timeout", sourceKey, tilesLoadedCount)
                    MapDiagnosticsTracker.tileStatus = "ok"
                    userPrefsRepo.setMapTileSource(sourceKey)
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

    // P2: обработка focusPoint — паритет с OsmMapView.
    // Синий пин найденного адреса + плавная анимация камеры с зумом 16.
    LaunchedEffect(focusPoint) {
        val map = mapLibreMap ?: return@LaunchedEffect

        // Удаляем старый маркер фокуса, если был
        focusMarkerRef[0]?.let { oldMarker ->
            try { map.removeMarker(oldMarker) } catch (_: Throwable) {}
            focusMarkerRef[0] = null
        }

        val newPoint = focusPoint
        if (newPoint != null) {
            val (lat, lon) = newPoint
            if (lat != 0.0 && lon != 0.0) {
                val iconFactory = IconFactory.getInstance(context)
                val bluePinDrawable = createBlueAddressPinIcon(context)
                val bluePinBitmap = drawableToBitmap(bluePinDrawable)
                val bluePinIcon = iconFactory.fromBitmap(bluePinBitmap)

                val focusMarkerOptions = MarkerOptions()
                    .position(LatLng(lat, lon))
                    .title("Найденный адрес")
                    .icon(bluePinIcon)
                focusMarkerRef[0] = map.addMarker(focusMarkerOptions)
                lastFocusPoint[0] = newPoint

                // Плавная анимация камеры к точке с зумом 16 (как в OsmMapView)
                val targetZoom = if (map.cameraPosition.zoom < 16.0) 16.0 else map.cameraPosition.zoom
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), targetZoom),
                    600  // мс — плавнее, чем дефолт
                )
                Timber.tag("MapLibreView").d("Focus marker added at (%.5f, %.5f), zoom=%.1f", lat, lon, targetZoom)
            }
        } else {
            lastFocusPoint[0] = null
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
