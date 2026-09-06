package com.navrot.aifuelassistant.ui.map.delegate

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.geo.GeoException
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.geo.GeocodingProvider
import com.navrot.aifuelassistant.ui.common.ErrorContext
import com.navrot.aifuelassistant.ui.common.ErrorMessageMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

import com.navrot.aifuelassistant.data.UserPreferencesRepository
import com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker
import com.navrot.aifuelassistant.ui.map.TileWarmupService

class MapSearchDelegate @Inject constructor(
    private val geocodingProvider: GeocodingProvider,
    private val benzonavtProvider: BenzonavtProvider,
    private val repository: GasStationRepositoryInterface,
    private val tileWarmupService: TileWarmupService,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) {

    companion object {
        private const val TAG = "MapSearchDelegate"
    }

    private val searchRequestId = AtomicInteger(0)

    private val _geocodedLocation = MutableStateFlow<GeoPoint?>(null)
    val geocodedLocation: StateFlow<GeoPoint?> = _geocodedLocation.asStateFlow()

    private val _currentCity = MutableStateFlow("Рядом с вами")
    val currentCity: StateFlow<String> = _currentCity.asStateFlow()

    fun clearGeocodedLocation() {
        _geocodedLocation.value = null
    }

    /**
     * Сначала синхронно/быстро берёт кэш (DataStore, ≤100 мс) или хардкод → эмит СРАЗУ.
     * Nominatim — ТОЛЬКО фоновое освежение с таймаутом ≤3 сек.
     */
    fun updateCityAndPrices(scope: CoroutineScope, lat: Double, lon: Double) {
        scope.launch {
            val startTs = System.currentTimeMillis()
            val cached = try {
                withTimeoutOrNull(100L) {
                    userPreferencesRepository?.cachedCity?.firstOrNull()
                }
            } catch (_: Exception) {
                null
            }

            val initialCity = if (!cached.isNullOrBlank()) {
                cached
            } else {
                GeoUtils.hardcodedDetectCity(lat, lon)
            }

            val cacheTimeMs = System.currentTimeMillis() - startTs
            val cacheSource = if (!cached.isNullOrBlank()) "cache" else "hardcode"
            _currentCity.value = initialCity
            val slug = GeoUtils.toCitySlug(initialCity)
            benzonavtProvider.setCity(slug)

            MapDiagnosticsTracker.cityResolveMs = cacheTimeMs
            MapDiagnosticsTracker.cityResolveSource = cacheSource

            val elapsedMs = System.currentTimeMillis() - MapDiagnosticsTracker.t0Ms
            Timber.tag("StartupTimeline").i("T+%dms city_resolved (source=%s, city=%s)", elapsedMs, cacheSource, initialCity)

            try {
                repository.refreshPrices()
                tileWarmupService.startPrefetch(lat, lon)
            } catch (e: Exception) {
                Timber.tag(TAG).w("Failed initial price refresh/tile prefetch: %s", e.message)
            }

            // Фоновое обновление города через Nominatim (таймаут ≤3 сек)
            try {
                val netCity = withTimeoutOrNull(3000L) {
                    val result = geocodingProvider.reverseGeocode(lat, lon)
                    result.displayName.split(",").firstOrNull()?.trim()
                }
                if (!netCity.isNullOrBlank() && netCity != initialCity) {
                    val netTimeMs = System.currentTimeMillis() - startTs
                    _currentCity.value = netCity
                    userPreferencesRepository?.setCachedCity(netCity)
                    val newSlug = GeoUtils.toCitySlug(netCity)
                    benzonavtProvider.setCity(newSlug)

                    MapDiagnosticsTracker.cityResolveMs = netTimeMs
                    MapDiagnosticsTracker.cityResolveSource = "network"

                    val netElapsedMs = System.currentTimeMillis() - MapDiagnosticsTracker.t0Ms
                    Timber.tag("StartupTimeline").i("T+%dms city_resolved (source=network, city=%s)", netElapsedMs, netCity)

                    repository.refreshPrices()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w("Background city resolution failed/timed out: %s", e.message)
            }
        }
    }

    /**
     * Устанавливает город вручную пользователем.
     */
    fun setManualCity(scope: CoroutineScope, cityName: String) {
        scope.launch {
            _currentCity.value = cityName
            userPreferencesRepository?.setCachedCity(cityName)
            val slug = GeoUtils.toCitySlug(cityName)
            benzonavtProvider.setCity(slug)
            repository.refreshPrices()
        }
    }

    /**
     * Поиск АЗС с fallback на Nominatim-геокодинг.
     *
     * 1. Сначала ищем в локальном списке (repository.searchStations)
     * 2. Если локальных совпадений нет — геокодим запрос через Nominatim (debounce 400мс)
     * 3. Race-guard: только последний запрос срабатывает (AtomicInteger)
     */
    fun searchStations(
        scope: CoroutineScope,
        query: String,
        onSearchResult: (stations: List<GasStation>, centerLat: Double?, centerLon: Double?) -> Unit,
        onError: (String) -> Unit,
        onLoading: (Boolean) -> Unit
    ) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            clearGeocodedLocation()
            return
        }

        val currentRequestId = searchRequestId.incrementAndGet()
        scope.launch {
            onLoading(true)
            onError("") // clear error
            try {
                // 1. Локальный поиск
                val localResults = repository.searchStations(trimmed)

                if (localResults.isNotEmpty()) {
                    if (currentRequestId == searchRequestId.get()) {
                        _geocodedLocation.value = null
                        onSearchResult(localResults, null, null)
                    }
                    return@launch
                }

                // 2. Нет локальных совпадений → геокодинг с debounce 400мс
                delay(400)
                if (currentRequestId != searchRequestId.get()) return@launch

                val result = geocodingProvider.geocode(trimmed)
                if (currentRequestId != searchRequestId.get()) return@launch

                _geocodedLocation.value = result.point

                val nearby = repository.getNearbyStations(
                    result.point.latitude, result.point.longitude, 10.0
                )
                if (currentRequestId == searchRequestId.get()) {
                    onSearchResult(nearby, result.point.latitude, result.point.longitude)
                }
            } catch (e: GeoException.NoResults) {
                if (currentRequestId == searchRequestId.get()) {
                    _geocodedLocation.value = null
                    onSearchResult(emptyList(), null, null)
                }
            } catch (e: GeoException.NetworkError) {
                Timber.tag(TAG).w("Geocoding network error: %s", e.message)
                if (currentRequestId == searchRequestId.get()) {
                    onSearchResult(emptyList(), null, null)
                    onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.GENERAL))
                }
            } catch (e: Exception) {
                if (currentRequestId == searchRequestId.get()) {
                    onError(ErrorMessageMapper.mapToUserMessage(e, ErrorContext.GENERAL))
                }
            } finally {
                if (currentRequestId == searchRequestId.get()) {
                    onLoading(false)
                }
            }
        }
    }
}
