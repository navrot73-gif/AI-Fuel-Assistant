package com.navrot.aifuelassistant.ui.map

import android.graphics.Color
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import timber.log.Timber

/**
 * Менеджер отрисовки пинов АЗС простой геометрией (plain circles) без кластеризации.
 *
 * Все 109 станций отрисовываются через единый [GeoJsonSource] (cluster=false) +
 * единый [CircleLayer] ('station-pins').
 *
 * Характеристики слоя 'station-pins':
 *   - circle-radius = 10
 *   - circle-stroke-width = 2
 *   - circle-stroke-color = #FFFFFF
 *   - circle-color = match(status):
 *       AVAILABLE → #4CAF50 (зеленый)
 *       NO_FUEL   → #E53935 (красный)
 *       UNKNOWN   → #607D8B (серый)
 *       fallback  → #607D8B
 *
 * Никаких minzoom/maxzoom и filter на слое.
 * Каждый feature несёт свойства: id, name, status, statusColor.
 */
class StationClusterManager {

    companion object {
        private const val TAG = "StationClusterManager"

        const val SOURCE_ID = "stations-plain-source"
        const val LAYER_PINS = "station-pins"

        // Цвета
        const val COLOR_AVAILABLE = "#4CAF50"
        const val COLOR_NO_FUEL = "#E53935"
        const val COLOR_UNKNOWN = "#607D8B"

        // Public references для тестов
        val COLOR_AVAILABLE_REF = COLOR_AVAILABLE
        val COLOR_NO_FUEL_REF = COLOR_NO_FUEL
        val COLOR_UNKNOWN_REF = COLOR_UNKNOWN

        // Размеры
        private const val STATION_CIRCLE_RADIUS = 10f
        private const val STATION_CIRCLE_STROKE = 2f

        // Свойства feature
        const val PROP_STATION_ID = "id"
        const val PROP_STATION_NAME = "name"
        const val PROP_STATUS = "status"  // "AVAILABLE" | "NO_FUEL" | "UNKNOWN"
        const val PROP_STATUS_COLOR = "statusColor"
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
        MapDiagnosticsTracker.pinsLayerInStyle = false
    }

    /**
     * Конвертирует список станций в GeoJSON FeatureCollection.
     *
     * Каждая фича — Point с координатами станции и свойствами:
     * - id: Int (для клик-обработки)
     * - name: String
     * - status: String (AVAILABLE/NO_FUEL/UNKNOWN)
     * - statusColor: String (hex для circle-color expression)
     */
    fun stationsToFeatureCollection(
        stations: List<GasStation>,
        selectedFuelTypes: Set<String>
    ): FeatureCollection {
        val features = stations.map { station ->
            val status = PriceReliabilityCalculator.calculateFuelAvailability(
                station,
                selectedFuelTypes.firstOrNull()
            ).name
            val statusColor = when (status) {
                FuelAvailabilityStatus.AVAILABLE.name -> COLOR_AVAILABLE
                FuelAvailabilityStatus.NO_FUEL.name -> COLOR_NO_FUEL
                else -> COLOR_UNKNOWN
            }

            val feature = Feature.fromGeometry(
                Point.fromLngLat(station.longitude, station.latitude)
            )
            feature.addStringProperty(PROP_STATION_ID, station.id.toString())
            feature.addStringProperty(PROP_STATION_NAME, station.name)
            feature.addStringProperty(PROP_STATUS, status)
            feature.addStringProperty(PROP_STATUS_COLOR, statusColor)
            feature
        }
        return FeatureCollection.fromFeatures(features)
    }

    /**
     * Добавляет source + слой 'station-pins' к стилю. Идемпотентно.
     */
    fun attachToStyle(style: Style, stations: List<GasStation>, selectedFuelTypes: Set<String>) {
        Timber.tag(TAG).i("attachToStyle: stations=%d, fuelTypes=%s, layersAttached=%b",
            stations.size, selectedFuelTypes, layersAttached)
        val featureCollection = stationsToFeatureCollection(stations, selectedFuelTypes)

        // Создаём или обновляем GeoJsonSource (cluster = false)
        val existing = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        if (existing != null) {
            try {
                existing.setGeoJson(featureCollection)
                currentSource = existing
                MapDiagnosticsTracker.pinsFeaturesCount = stations.size
                Timber.tag(TAG).d("Updated existing plain source with %d stations", stations.size)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to setGeoJson on existing source")
            }
        } else {
            try {
                val options = GeoJsonOptions().withCluster(false)
                val source = GeoJsonSource(
                    SOURCE_ID,
                    featureCollection,
                    options
                )
                style.addSource(source)
                currentSource = source
                MapDiagnosticsTracker.pinsFeaturesCount = stations.size
                Timber.tag(TAG).d("✅ Created plain source '%s' with %d stations", SOURCE_ID, stations.size)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Failed to addSource '%s'", SOURCE_ID)
                return
            }
        }

        if (!layersAttached || style.getLayer(LAYER_PINS) == null) {
            try {
                addPinLayer(style)
                layersAttached = true
                MapDiagnosticsTracker.pinsLayerInStyle = true
                MapDiagnosticsTracker.pinsMode = "plain"
                MapDiagnosticsTracker.pinsFeaturesCount = stations.size
                checkAndRecordFirstPinsDrawn(style, stations.size)
                Timber.tag(TAG).d("✅ layersAttached=true for station-pins")
            } catch (e: Exception) {
                MapDiagnosticsTracker.pinsLayerInStyle = false
                Timber.tag(TAG).e(e, "❌ addPinLayer threw — layers will be missing")
            }
        } else {
            MapDiagnosticsTracker.pinsLayerInStyle = true
            checkAndRecordFirstPinsDrawn(style, stations.size)
        }
    }

    /**
     * Обновляет данные в source без пересоздания слоёв.
     */
    fun updateStations(style: Style, stations: List<GasStation>, selectedFuelTypes: Set<String>) {
        Timber.tag(TAG).d("updateStations: stations=%d, layersAttached=%b",
            stations.size, layersAttached)
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: run {
            Timber.tag(TAG).w("updateStations: source not found, calling attachToStyle")
            attachToStyle(style, stations, selectedFuelTypes)
            return
        }
        val featureCollection = stationsToFeatureCollection(stations, selectedFuelTypes)
        try {
            source.setGeoJson(featureCollection)
            MapDiagnosticsTracker.pinsFeaturesCount = stations.size
            val layerInStyle = style.getLayer(LAYER_PINS) != null
            MapDiagnosticsTracker.pinsLayerInStyle = layerInStyle
            if (layerInStyle) {
                checkAndRecordFirstPinsDrawn(style, stations.size)
            }
            Timber.tag(TAG).d("✅ Updated plain source: %d stations", stations.size)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ setGeoJson failed in updateStations")
        }
    }

    /**
     * Удаляет source и слои из стиля.
     */
    fun detachFromStyle(style: Style) {
        style.getLayer(LAYER_PINS)?.let { style.removeLayer(it) }
        style.getSource(SOURCE_ID)?.let { style.removeSource(it) }
        currentSource = null
        layersAttached = false
        MapDiagnosticsTracker.pinsLayerInStyle = false
        MapDiagnosticsTracker.pinsFeaturesCount = 0
        Timber.tag(TAG).d("Detached station-pins layer and source")
    }

    private fun addPinLayer(style: Style) {
        style.getLayer(LAYER_PINS)?.let { style.removeLayer(it) }

        val pinsLayer = CircleLayer(LAYER_PINS, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(STATION_CIRCLE_RADIUS),
                PropertyFactory.circleStrokeWidth(STATION_CIRCLE_STROKE),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleColor(
                    Expression.match(
                        Expression.get(PROP_STATUS),
                        Expression.literal(COLOR_AVAILABLE),
                        Expression.stop(FuelAvailabilityStatus.AVAILABLE.name, COLOR_AVAILABLE),
                        Expression.stop(FuelAvailabilityStatus.NO_FUEL.name, COLOR_NO_FUEL),
                        Expression.stop(FuelAvailabilityStatus.UNKNOWN.name, COLOR_UNKNOWN)
                    )
                ),
                PropertyFactory.circleOpacity(0.95f),
                PropertyFactory.circleStrokeOpacity(1.0f)
            )
        }
        style.addLayer(pinsLayer)
        Timber.tag(TAG).d("✅ Added CircleLayer '%s'", LAYER_PINS)
    }

    private fun checkAndRecordFirstPinsDrawn(style: Style, featureCount: Int) {
        val layerPresent = style.getLayer(LAYER_PINS) != null
        if (layerPresent && featureCount > 0) {
            if (MapDiagnosticsTracker.firstPinsDrawnMs == 0L) {
                val elapsed = System.currentTimeMillis() - MapDiagnosticsTracker.t0Ms
                MapDiagnosticsTracker.firstPinsDrawnMs = elapsed
                Timber.tag("StartupTimeline").i("T+%dms first_pins_drawn (%d stations)", elapsed, featureCount)
            }
        }
    }

    /**
     * Обрабатывает клик по карте: запрашивает 'station-pins' и вызывает [onStationClick].
     */
    fun handleClick(
        map: MapLibreMap,
        stations: List<GasStation>,
        lat: Double,
        lon: Double,
        onStationClick: (GasStation) -> Unit
    ): Boolean {
        if (currentSource == null) return false

        val screenPoint = map.projection.toScreenLocation(
            org.maplibre.android.geometry.LatLng(lat, lon)
        )

        val features = try {
            map.queryRenderedFeatures(screenPoint, LAYER_PINS)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to query features at click point")
            return false
        }

        if (features.isEmpty()) return false

        val feature = features[0]
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
