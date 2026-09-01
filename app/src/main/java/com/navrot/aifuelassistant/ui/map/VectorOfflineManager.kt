package com.navrot.aifuelassistant.ui.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class VectorOfflineState {
    object Idle : VectorOfflineState()
    data class Downloading(val progressPercent: Float, val downloadedBytes: Long) : VectorOfflineState()
    data class Downloaded(val regionId: Long, val sizeBytes: Long, val regionName: String) : VectorOfflineState()
    data class Error(val message: String) : VectorOfflineState()
}

data class OfflineRegionItem(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val bounds: LatLngBounds
)

@Singleton
class VectorOfflineManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "VectorOfflineManager"
        const val DEFAULT_STYLE_URL = "https://demotiles.maplibre.org/style.json"
        const val MIN_ZOOM = 10.0
        const val MAX_ZOOM = 17.0
        const val PIXEL_RATIO = 2.0f
        const val RADIUS_KM = 20.0
    }

    private val _offlineState = MutableStateFlow<VectorOfflineState>(VectorOfflineState.Idle)
    val offlineState: StateFlow<VectorOfflineState> = _offlineState.asStateFlow()

    private val offlineManager: OfflineManager by lazy {
        OfflineManager.getInstance(context)
    }

    fun calculateCityBounds(centerLat: Double, centerLon: Double, radiusKm: Double = RADIUS_KM): LatLngBounds {
        val latDelta = radiusKm / 111.0
        val cosLat = Math.cos(Math.toRadians(centerLat)).let { if (it == 0.0) 0.0001 else it }
        val lonDelta = radiusKm / (111.0 * cosLat)

        val north = (centerLat + latDelta).coerceAtMost(90.0)
        val south = (centerLat - latDelta).coerceAtLeast(-90.0)
        val east = (centerLon + lonDelta).coerceAtMost(180.0)
        val west = (centerLon - lonDelta).coerceAtLeast(-180.0)

        return LatLngBounds.Builder()
            .include(LatLng(north, east))
            .include(LatLng(south, west))
            .build()
    }

    fun createRegionDefinition(
        bounds: LatLngBounds,
        styleUrl: String = DEFAULT_STYLE_URL,
        minZoom: Double = MIN_ZOOM,
        maxZoom: Double = MAX_ZOOM,
        pixelRatio: Float = PIXEL_RATIO
    ): OfflineTilePyramidRegionDefinition {
        return OfflineTilePyramidRegionDefinition(
            styleUrl,
            bounds,
            minZoom,
            maxZoom,
            pixelRatio
        )
    }

    fun downloadCurrentCityRegion(
        centerLat: Double,
        centerLon: Double,
        cityName: String = "Текущий город",
        styleUrl: String = DEFAULT_STYLE_URL
    ) {
        try {
            val bounds = calculateCityBounds(centerLat, centerLon)
            val definition = createRegionDefinition(bounds, styleUrl)

            val metadataJson = JSONObject().apply {
                put("name", cityName)
                put("lat", centerLat)
                put("lon", centerLon)
            }
            val metadataBytes = metadataJson.toString().toByteArray(Charsets.UTF_8)

            _offlineState.value = VectorOfflineState.Downloading(0f, 0L)

            offlineManager.createOfflineRegion(
                definition,
                metadataBytes,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        Timber.tag(TAG).d("Offline region created, id=${offlineRegion.id}")
                        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                val required = status.requiredResourceCount
                                val completed = status.completedResourceCount
                                val sizeBytes = status.completedResourceSize
                                val progress = if (required > 0) (completed.toFloat() / required * 100f) else 0f

                                if (status.isComplete) {
                                    Timber.tag(TAG).d("Offline region download complete. Size=$sizeBytes bytes")
                                    _offlineState.value = VectorOfflineState.Downloaded(
                                        regionId = offlineRegion.id,
                                        sizeBytes = sizeBytes,
                                        regionName = cityName
                                    )
                                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                } else {
                                    _offlineState.value = VectorOfflineState.Downloading(progress, sizeBytes)
                                }
                            }

                            override fun onError(error: OfflineRegionError) {
                                Timber.tag(TAG).e("Offline region error: %s - %s", error.reason, error.message)
                                _offlineState.value = VectorOfflineState.Error(error.message ?: "Ошибка скачивания")
                            }

                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                Timber.tag(TAG).w("Tile count limit exceeded: $limit")
                                _offlineState.value = VectorOfflineState.Error("Превышен лимит тайлов ($limit)")
                            }
                        })

                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        Timber.tag(TAG).e("Failed to create offline region: $error")
                        _offlineState.value = VectorOfflineState.Error(error)
                    }
                }
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception starting offline download")
            _offlineState.value = VectorOfflineState.Error(e.message ?: "Ошибка инициализации загрузки")
        }
    }

    fun checkDownloadedRegions(onResult: ((List<OfflineRegionItem>) -> Unit)? = null) {
        try {
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val regions = offlineRegions ?: emptyArray()
                    val items = mutableListOf<OfflineRegionItem>()
                    var processedCount = 0
                    var foundDownloaded = false

                    if (regions.isEmpty()) {
                        if (_offlineState.value !is VectorOfflineState.Downloading) {
                            _offlineState.value = VectorOfflineState.Idle
                        }
                        onResult?.invoke(emptyList())
                        return
                    }

                    regions.forEach { region ->
                        val metadataStr = try {
                            String(region.metadata, Charsets.UTF_8)
                        } catch (e: Exception) {
                            ""
                        }
                        val name = try {
                            JSONObject(metadataStr).optString("name", "Регион #${region.id}")
                        } catch (e: Exception) {
                            "Регион #${region.id}"
                        }

                        val def = region.definition as? OfflineTilePyramidRegionDefinition
                        val bounds = def?.bounds ?: LatLngBounds.from(55.2, 61.5, 55.0, 61.3)

                        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                            override fun onStatus(status: OfflineRegionStatus?) {
                                val sizeBytes = status?.completedResourceSize ?: 0L
                                val isComplete = status?.isComplete == true

                                val item = OfflineRegionItem(
                                    id = region.id,
                                    name = name,
                                    sizeBytes = sizeBytes,
                                    bounds = bounds
                                )
                                items.add(item)
                                processedCount++

                                if (isComplete || sizeBytes > 0) {
                                    foundDownloaded = true
                                    _offlineState.value = VectorOfflineState.Downloaded(
                                        regionId = region.id,
                                        sizeBytes = sizeBytes,
                                        regionName = name
                                    )
                                }

                                if (processedCount == regions.size) {
                                    if (!foundDownloaded && _offlineState.value !is VectorOfflineState.Downloading) {
                                        _offlineState.value = VectorOfflineState.Idle
                                    }
                                    onResult?.invoke(items)
                                }
                            }

                            override fun onError(error: String?) {
                                Timber.tag(TAG).w("Error getting region status: $error")
                                processedCount++
                                if (processedCount == regions.size) {
                                    onResult?.invoke(items)
                                }
                            }
                        })
                    }
                }

                override fun onError(error: String) {
                    Timber.tag(TAG).e("Error listing offline regions: $error")
                    onResult?.invoke(emptyList())
                }
            })
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception checking downloaded regions")
            onResult?.invoke(emptyList())
        }
    }

    fun deleteRegion(regionId: Long, onComplete: (() -> Unit)? = null) {
        try {
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val regionToDelete = offlineRegions?.firstOrNull { it.id == regionId }
                    if (regionToDelete != null) {
                        regionToDelete.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {
                                Timber.tag(TAG).d("Deleted offline region $regionId")
                                _offlineState.value = VectorOfflineState.Idle
                                onComplete?.invoke()
                            }

                            override fun onError(error: String) {
                                Timber.tag(TAG).e("Failed to delete offline region $regionId: $error")
                                _offlineState.value = VectorOfflineState.Error("Не удалось удалить регион: $error")
                            }
                        })
                    } else {
                        _offlineState.value = VectorOfflineState.Idle
                        onComplete?.invoke()
                    }
                }

                override fun onError(error: String) {
                    Timber.tag(TAG).e("Error listing regions during delete: $error")
                }
            })
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception deleting region $regionId")
        }
    }

    fun isRegionDownloaded(bounds: LatLngBounds, callback: (Boolean) -> Unit) {
        checkDownloadedRegions { items ->
            val downloaded = items.any { item ->
                item.bounds.contains(bounds.center) ||
                (item.bounds.latitudeNorth >= bounds.latitudeNorth &&
                 item.bounds.latitudeSouth <= bounds.latitudeSouth &&
                 item.bounds.longitudeEast >= bounds.longitudeEast &&
                 item.bounds.longitudeWest <= bounds.longitudeWest)
            }
            callback(downloaded)
        }
    }
}
