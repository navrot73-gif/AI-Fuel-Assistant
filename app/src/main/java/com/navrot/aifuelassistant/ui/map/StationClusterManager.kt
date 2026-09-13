package com.navrot.aifuelassistant.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import timber.log.Timber

/**
 * Менеджер кластеризации пинов АЗС в стиле «ГдеБЕНЗ».
 *
 * Заменяет построчное добавление [org.maplibre.android.annotations.Marker] на
 * кластеризованный [GeoJsonSource] + [SymbolLayer] + [CircleLayer].
 *
 * Архитектура слоёв (снизу вверх):
 *   1. station-clusters-count — SymbolLayer с числом станций в кластере
 *   2. station-clusters — CircleLayer (круги-кластеры, цвет по размеру)
 *   3. station-unclustered — SymbolLayer для отдельных станций (цветные точки)
 *
 * Цвет отдельных станций зависит от наличия топлива (как в [getMarkerColor]):
 *   - AVAILABLE → зелёный
 *   - NO_FUEL → красный
 *   - UNKNOWN → серый
 *
 * Клик по кластеру → zoom +1 (стандартное поведение MapLibre clustering).
 * Клик по отдельной станции → callback [onStationClick] с ID станции.
 *
 * Бэклог №10 — теперь на стабильном фундаменте PR #177.
 */
class StationClusterManager {

    companion object {
        private const val TAG = "StationClusterManager"

        const val SOURCE_ID = "stations-cluster-source"
        const val LAYER_UNCLUSTERED = "station-unclustered"
        const val LAYER_CLUSTERS = "station-clusters"
        const val LAYER_CLUSTERS_COUNT = "station-clusters-count"

        /** Зум, до которого работает кластеризация. Выше — отдельные пины. */
        const val CLUSTER_MAX_ZOOM = 14

        /** Радиус кластеризации в пикселях (на экране). */
        const val CLUSTER_RADIUS = 50

        // Цвета (должны совпадать с FueldeckColors в theme)
        const val COLOR_AVAILABLE = "#4CAF50"  // зелёный (Mint)
        const val COLOR_NO_FUEL = "#F44336"    // красный (Coral)
        const val COLOR_UNKNOWN = "#9E9E9E"    // серый (InkFaint)

        // Public references для тестов (StationClusterManagerTest)
        val COLOR_AVAILABLE_REF = COLOR_AVAILABLE
        val COLOR_NO_FUEL_REF = COLOR_NO_FUEL
        val COLOR_UNKNOWN_REF = COLOR_UNKNOWN

        // Размеры
        private const val STATION_CIRCLE_RADIUS = 8f
        private const val STATION_CIRCLE_STROKE = 2f
        private const val CLUSTER_MIN_RADIUS = 18f
        private const val CLUSTER_MAX_RADIUS = 40f

        // Свойства feature
        const val PROP_STATION_ID = "stationId"
        const val PROP_STATION_NAME = "stationName"
        const val PROP_AVAILABILITY = "availability"  // "AVAILABLE" | "NO_FUEL" | "UNKNOWN"
        const val PROP_COLOR = "color"
    }

    private var currentSource: GeoJsonSource? = null
    private var layersAttached = false

    /**
     * Сбрасывает флаг [layersAttached]. Вызывается из MapLibreView.resetMarkerTrackers()
     * перед повторным setStyle — после смены стиля слои нужно пересоздать на новом стиле.
     */
    fun resetLayersAttached() {
        layersAttached = false
        currentSource = null
    }

    /**
     * Конвертирует список станций в GeoJSON FeatureCollection.
     *
     * Каждая фича — Point с координатами станции и свойствами:
     * - stationId: Int (для клик-обработки)
     * - stationName: String
     * - availability: String (AVAILABLE/NO_FUEL/UNKNOWN)
     * - color: String (hex для circle-color expression)
     */
    fun stationsToFeatureCollection(
        stations: List<GasStation>,
        selectedFuelTypes: Set<String>
    ): FeatureCollection {
        val features = stations.map { station ->
            val availability = PriceReliabilityCalculator.calculateFuelAvailability(
                station,
                selectedFuelTypes.firstOrNull()
            ).name
            val color = when (availability) {
                FuelAvailabilityStatus.AVAILABLE.name -> COLOR_AVAILABLE
                FuelAvailabilityStatus.NO_FUEL.name -> COLOR_NO_FUEL
                else -> COLOR_UNKNOWN
            }

            Feature.fromGeometry(
                Point.fromLngLat(station.longitude, station.latitude),
                mapOf(
                    PROP_STATION_ID to station.id,
                    PROP_STATION_NAME to station.name,
                    PROP_AVAILABILITY to availability,
                    PROP_COLOR to color
                )
            )
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Добавляет source + слои к стилю. Идемпотентно — повторный вызов обновляет данные.
     *
     * Должно вызываться ПОСЛЕ установки стиля (в колбэке setStyle) и до первого
     * [updateStations]. Слои добавляются ПОВЕРХ runtime-tile-source (см. attachTileSourceToStyle).
     */
    fun attachToStyle(style: Style, stations: List<GasStation>, selectedFuelTypes: Set<String>) {
        val featureCollection = stationsToFeatureCollection(stations, selectedFuelTypes)

        // Создаём или обновляем source
        val existing = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        if (existing != null) {
            existing.setGeoJson(featureCollection)
            currentSource = existing
            Timber.tag(TAG).d("Updated existing cluster source with %d stations", stations.size)
        } else {
            val source = GeoJsonSource(
                SOURCE_ID,
                featureCollection,
                GeoJsonSource.Options()
                    .withCluster(true)
                    .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
                    .withClusterRadius(CLUSTER_RADIUS)
            )
            style.addSource(source)
            currentSource = source
            Timber.tag(TAG).d("Created cluster source with %d stations", stations.size)
        }

        // Добавляем слои один раз
        if (!layersAttached) {
            addClusterLayers(style)
            layersAttached = true
        }
    }

    /**
     * Обновляет данные в source без пересоздания слоёв.
     * Вызывать при изменении списка станций или выбранных типов топлива.
     */
    fun updateStations(style: Style, stations: List<GasStation>, selectedFuelTypes: Set<String>) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: run {
            // Source ещё не создан — вызываем полный attach
            attachToStyle(style, stations, selectedFuelTypes)
            return
        }
        val featureCollection = stationsToFeatureCollection(stations, selectedFuelTypes)
        source.setGeoJson(featureCollection)
        Timber.tag(TAG).d("Updated cluster source: %d stations", stations.size)
    }

    /**
     * Удаляет source и слои из стиля (например, при смене движка).
     */
    fun detachFromStyle(style: Style) {
        style.getLayer(LAYER_CLUSTERS_COUNT)?.let { style.removeLayer(it) }
        style.getLayer(LAYER_CLUSTERS)?.let { style.removeLayer(it) }
        style.getLayer(LAYER_UNCLUSTERED)?.let { style.removeLayer(it) }
        style.getSource(SOURCE_ID)?.let { style.removeSource(it) }
        currentSource = null
        layersAttached = false
        Timber.tag(TAG).d("Detached cluster layers and source")
    }

    private fun addClusterLayers(style: Style) {
        // 1. Слой отдельных (некластеризованных) станций — цветные круги.
        //    Фильтр: точки БЕЗ свойства "point_count" (это некластеризованные фичи).
        val unclusteredLayer = CircleLayer(LAYER_UNCLUSTERED, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(STATION_CIRCLE_RADIUS),
                PropertyFactory.circleColor(Expression.get(PROP_COLOR)),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(STATION_CIRCLE_STROKE),
                PropertyFactory.circleOpacity(0.9f),
                PropertyFactory.circleStrokeOpacity(1.0f)
            )
            // Фильтр: только некластеризованные точки (НЕ имеют point_count).
            // MapLibre автоматически добавляет point_count кластерам, отдельным точкам — нет.
            setFilter(Expression.not(Expression.has("point_count")))
        }
        style.addLayer(unclusteredLayer)

        // 2. Слой кластеров — круги с цветом по размеру кластера.
        //    Фильтр: только кластеры (ИМЕЮТ point_count).
        val clusterLayer = CircleLayer(LAYER_CLUSTERS, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(
                    Expression.step(
                        Expression.get("point_count"),
                        CLUSTER_MIN_RADIUS,
                        Expression.stop(10, CLUSTER_MIN_RADIUS + 6),
                        Expression.stop(50, CLUSTER_MIN_RADIUS + 14),
                        Expression.stop(100, CLUSTER_MAX_RADIUS)
                    )
                ),
                PropertyFactory.circleColor(
                    Expression.step(
                        Expression.get("point_count"),
                        Color.parseColor("#51bbd6"),  // малые кластеры — голубой
                        Expression.stop(10, Color.parseColor("#f1f075")),  // жёлтый
                        Expression.stop(50, Color.parseColor("#f28cb1"))   // розовый
                    )
                ),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleOpacity(0.85f)
            )
            setFilter(Expression.has("point_count"))
        }
        style.addLayer(clusterLayer)

        // 3. Слой счётчика в кластерах — текст с числом.
        val countLayer = SymbolLayer(LAYER_CLUSTERS_COUNT, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.textField(Expression.get("point_count")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular", "Arial Unicode MS Regular")),
                PropertyFactory.textHaloColor(Color.parseColor("#1a1a1a")),
                PropertyFactory.textHaloWidth(1f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
            setFilter(Expression.has("point_count"))
        }
        style.addLayer(countLayer)

        Timber.tag(TAG).d("Attached 3 cluster layers (unclustered, clusters, count)")
    }

    /**
     * Обрабатывает клик по карте. Возвращает true, если клик был по станции или кластеру.
     *
     * Логика:
     * - Клик по кластеру → zoom +1.5 к центру кластера, return true
     * - Клик по отдельной станции → вызвать [onStationClick] с найденной станцией, return true
     * - Клик по пустому месту → return false (пусть MapLibre обработает сам)
     */
    fun handleClick(
        map: MapLibreMap,
        stations: List<GasStation>,
        lat: Double,
        lon: Double,
        onStationClick: (GasStation) -> Unit
    ): Boolean {
        if (currentSource == null) return false

        // Query rendered features в точке клика
        val screenPoint = map.projection.toScreenLocation(
            org.maplibre.android.geometry.LatLng(lat, lon)
        )

        // Сначала проверяем кластеры (верхний слой)
        val clusterFeatures = try {
            map.queryRenderedFeatures(screenPoint, LAYER_CLUSTERS)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to query cluster features at click point")
            emptyList()
        }
        if (clusterFeatures.isNotEmpty()) {
            val feature = clusterFeatures[0]
            // Геометрия кластера — Point с центром
            val geometry = feature.geometry()
            if (geometry is Point) {
                val zoomToApply = (map.cameraPosition.zoom + 1.5).coerceAtMost(20.0)
                map.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        org.maplibre.android.geometry.LatLng(
                            geometry.latitude(),
                            geometry.longitude()
                        ),
                        zoomToApply
                    ),
                    400
                )
                Timber.tag(TAG).d("Cluster click: zooming to (%.4f, %.4f) zoom=%.1f",
                    geometry.latitude(), geometry.longitude(), zoomToApply)
                return true
            }
        }

        // Затем проверяем отдельные станции
        val stationFeatures = try {
            map.queryRenderedFeatures(screenPoint, LAYER_UNCLUSTERED)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to query station features at click point")
            emptyList()
        }
        if (stationFeatures.isNotEmpty()) {
            val feature = stationFeatures[0]
            // Свойство stationId хранится как число в GeoJSON properties
            val stationId = feature.getStringProperty(PROP_STATION_ID)?.toIntOrNull()
                ?: feature.getNumberProperty(PROP_STATION_ID)?.toInt()
            if (stationId != null) {
                val station = stations.firstOrNull { it.id == stationId }
                if (station != null) {
                    onStationClick(station)
                    Timber.tag(TAG).d("Station click: id=%d, name=%s", station.id, station.name)
                    return true
                } else {
                    Timber.tag(TAG).w("Station click: id=%d not found in stations list (size=%d)",
                        stationId, stations.size)
                }
            }
        }

        return false
    }
}
