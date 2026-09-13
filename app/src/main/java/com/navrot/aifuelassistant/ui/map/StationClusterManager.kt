package com.navrot.aifuelassistant.ui.map

import android.graphics.Color
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
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
        const val COLOR_CLUSTER = "#51bbd6"    // голубой для кластеров

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

            val feature = Feature.fromGeometry(
                Point.fromLngLat(station.longitude, station.latitude)
            )
            // Properties добавляем через addStringProperty — надёжнее, чем mapOf,
            // который требует совпадения типов JsonValue и может не скомпилироваться
            // в разных версиях MapLibre geojson.
            feature.addStringProperty(PROP_STATION_ID, station.id.toString())
            feature.addStringProperty(PROP_STATION_NAME, station.name)
            feature.addStringProperty(PROP_AVAILABILITY, availability)
            feature.addStringProperty(PROP_COLOR, color)
            feature
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
        Timber.tag(TAG).i("attachToStyle: stations=%d, fuelTypes=%s, layersAttached=%b",
            stations.size, selectedFuelTypes, layersAttached)
        val featureCollection = stationsToFeatureCollection(stations, selectedFuelTypes)

        // Создаём или обновляем source
        val existing = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        if (existing != null) {
            try {
                existing.setGeoJson(featureCollection)
                currentSource = existing
                Timber.tag(TAG).d("Updated existing cluster source with %d stations", stations.size)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to setGeoJson on existing source")
            }
        } else {
            try {
                val clusterOptions = GeoJsonOptions()
                    .withCluster(true)
                    .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
                    .withClusterRadius(CLUSTER_RADIUS)
                val source = GeoJsonSource(
                    SOURCE_ID,
                    featureCollection,
                    clusterOptions
                )
                style.addSource(source)
                currentSource = source
                Timber.tag(TAG).d("✅ Created cluster source '%s' with %d stations", SOURCE_ID, stations.size)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Failed to addSource '%s'", SOURCE_ID)
                return  // Не добавляем слои, если source не создан
            }
        }

        // Добавляем слои один раз
        if (!layersAttached) {
            try {
                addClusterLayers(style)
                layersAttached = true
                Timber.tag(TAG).d("✅ layersAttached=true")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ addClusterLayers threw — layers will be missing")
            }
        }
    }

    /**
     * Обновляет данные в source без пересоздания слоёв.
     * Вызывать при изменении списка станций или выбранных типов топлива.
     */
    fun updateStations(style: Style, stations: List<GasStation>, selectedFuelTypes: Set<String>) {
        Timber.tag(TAG).d("updateStations: stations=%d, layersAttached=%b",
            stations.size, layersAttached)
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: run {
            // Source ещё не создан — вызываем полный attach
            Timber.tag(TAG).w("updateStations: source not found, calling attachToStyle")
            attachToStyle(style, stations, selectedFuelTypes)
            return
        }
        val featureCollection = stationsToFeatureCollection(stations, selectedFuelTypes)
        try {
            source.setGeoJson(featureCollection)
            Timber.tag(TAG).d("✅ Updated cluster source: %d stations", stations.size)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ setGeoJson failed in updateStations")
        }
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
        // P0-фикс: упрощаем слои до минимума, который гарантированно работает.
        // Убраны: setFilter (часто бросает исключение в MapLibre 11.5.0),
        //         textFont (падает на устройствах без указанных шрифтов),
        //         сложные Expression.step (заменены на простые литералы).
        //
        // Стратегия: рисуем ВСЕ точки из source. Кластеры автоматически получают
        // point_count от MapLibre, отдельные станции — нет. Через Expression.switchCase
        // получаем число для размера круга: если point_count есть — это кластер
        // (большой круг), иначе — отдельная станция (маленький круг с цветом).
        //
        // ВАЖНО по цветам (P0-фикс #179): MapLibre Expression.literal(Int) для цвета
        // НЕ работает — выдаёт "Expected color but found number instead".
        // Нужно передавать СТРОКУ "#RRGGBB" — MapLibre сам парсит.
        // Color.parseColor() возвращает Int, что ломает Expression.

        // 1. Слой всех точек — круги. Размер зависит от наличия point_count.
        val unclusteredLayer = CircleLayer(LAYER_UNCLUSTERED, SOURCE_ID).apply {
            setProperties(
                // Если point_count есть (кластер) — радиус 24, иначе 8 (станция)
                PropertyFactory.circleRadius(
                    Expression.switchCase(
                        Expression.has("point_count"),
                        Expression.literal(24f),
                        Expression.literal(STATION_CIRCLE_RADIUS)
                    )
                ),
                // Цвет для кластеров — голубой, для станций — по доступности топлива.
                // P0-фикс: используем СТРОКИ "#RRGGBB", не Color.parseColor (Int).
                // MapLibre Expression.literal(Int) для цвета выдаёт
                // "Expected color but found number instead" — поэтому только строки.
                PropertyFactory.circleColor(
                    Expression.switchCase(
                        Expression.has("point_count"),
                        Expression.literal(COLOR_CLUSTER),
                        // Для отдельных станций: цвет по доступности топлива
                        Expression.switchCase(
                            Expression.eq(Expression.literal(FuelAvailabilityStatus.NO_FUEL.name), Expression.get(PROP_AVAILABILITY)),
                            Expression.literal(COLOR_NO_FUEL),
                            Expression.eq(Expression.literal(FuelAvailabilityStatus.UNKNOWN.name), Expression.get(PROP_AVAILABILITY)),
                            Expression.literal(COLOR_UNKNOWN),
                            Expression.literal(COLOR_AVAILABLE)  // default = AVAILABLE
                        )
                    )
                ),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(STATION_CIRCLE_STROKE),
                PropertyFactory.circleOpacity(0.9f),
                PropertyFactory.circleStrokeOpacity(1.0f)
            )
        }
        try {
            style.addLayer(unclusteredLayer)
            Timber.tag(TAG).d("✅ Added CircleLayer '%s'", LAYER_UNCLUSTERED)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to add CircleLayer '%s'", LAYER_UNCLUSTERED)
            throw e
        }

        // 2. Слой текста для кластеров — показывает число станций.
        //    textField через Expression.toString безопаснее, чем прямой get.
        val countLayer = SymbolLayer(LAYER_CLUSTERS_COUNT, SOURCE_ID).apply {
            setProperties(
                // Показываем число только если point_count существует (кластер)
                PropertyFactory.textField(
                    Expression.switchCase(
                        Expression.has("point_count"),
                        Expression.toString(Expression.get("point_count")),
                        Expression.literal("")
                    )
                ),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textHaloColor(Color.parseColor("#1a1a1a")),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
        }
        try {
            style.addLayer(countLayer)
            Timber.tag(TAG).d("✅ Added SymbolLayer '%s'", LAYER_CLUSTERS_COUNT)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to add SymbolLayer '%s' — continuing without it", LAYER_CLUSTERS_COUNT)
        }

        Timber.tag(TAG).d("Attached %d cluster layers", 2)
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

        // P0-фикс: теперь у нас один слой LAYER_UNCLUSTERED, который рисует и кластеры,
        // и отдельные станции. Запрашиваем его.
        val features = try {
            map.queryRenderedFeatures(screenPoint, LAYER_UNCLUSTERED)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to query features at click point")
            return false
        }

        if (features.isEmpty()) return false

        val feature = features[0]

        // Если есть point_count — это кластер, zoom +1.5
        val isCluster = feature.hasProperty("point_count")
        if (isCluster) {
            val geometry = feature.geometry()
            if (geometry is Point) {
                val coords = geometry.coordinates()
                if (coords != null && coords.size >= 2) {
                    val clusterLon = coords[0]
                    val clusterLat = coords[1]
                    val zoomToApply = (map.cameraPosition.zoom + 1.5).coerceAtMost(20.0)
                    map.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            org.maplibre.android.geometry.LatLng(clusterLat, clusterLon),
                            zoomToApply
                        ),
                        400
                    )
                    Timber.tag(TAG).d("Cluster click: zooming to (%.4f, %.4f) zoom=%.1f",
                        clusterLat, clusterLon, zoomToApply)
                    return true
                }
            }
            return false
        }

        // Иначе — отдельная станция, вызываем callback
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

        return false
    }
}
