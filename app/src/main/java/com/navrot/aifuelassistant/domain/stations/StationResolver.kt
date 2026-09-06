package com.navrot.aifuelassistant.domain.stations

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.matchesBrand
import com.navrot.aifuelassistant.geo.GeoUtils
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationResolver @Inject constructor(
    private val repository: GasStationRepositoryInterface
) {
    companion object {
        private const val TAG = "StationResolver"
        const val DEFAULT_LAT = 55.1644
        const val DEFAULT_LON = 61.4368

        private val KNOWN_BRANDS = listOf(
            "газпромнефть", "газпром", "роснефть", "татнефть", "башнефть",
            "лукойл", "шелл", "shell", "смарт", "олекс", "новатек", "терминал",
            "irbis", "опти"
        )

        private val STOP_WORDS = setOf(
            "построй", "маршрут", "до", "ближайшая", "ближайший", "заправка", "азс",
            "где", "есть", "на", "у", "по", "в", "к", "цена", "дешевле", "топливо",
            "акция", "скидка", "рублей", "руб", "доведи", "покажи", "найди"
        )
    }

    private suspend fun getAllMergedStations(
        lat: Double? = null,
        lon: Double? = null,
        fallbackStations: List<GasStation> = emptyList()
    ): List<GasStation> {
        val userLat = lat ?: DEFAULT_LAT
        val userLon = lon ?: DEFAULT_LON
        val repoStations = try {
            val nearby = repository.getNearbyStations(userLat, userLon, 50.0)
            if (nearby.isNotEmpty()) nearby else repository.getAllStations()
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to get nearby stations, falling back to all stations: %s", e.message)
            try {
                repository.getAllStations()
            } catch (ex: Exception) {
                Timber.tag(TAG).e(ex, "Failed to get all stations in StationResolver")
                emptyList()
            }
        }
        return if (repoStations.isNotEmpty()) repoStations else fallbackStations
    }

    suspend fun getStationById(
        id: Int,
        lat: Double? = null,
        lon: Double? = null,
        fallbackStations: List<GasStation> = emptyList()
    ): GasStation? {
        val stations = getAllMergedStations(lat, lon, fallbackStations)
        return stations.firstOrNull { it.id == id } ?: repository.getStationById(id)
    }

    /**
     * Finds nearest station matching the brand across ALL merged stations (including OSM-only, UNKNOWN, NO_FUEL).
     */
    suspend fun nearestByBrand(
        brand: String,
        lat: Double,
        lon: Double,
        fallbackStations: List<GasStation> = emptyList()
    ): GasStation? {
        val stations = getAllMergedStations(lat, lon, fallbackStations)
        val matching = stations.filter { st ->
            st.matchesBrand(brand) ||
                    st.brand.contains(brand, ignoreCase = true) ||
                    st.name.contains(brand, ignoreCase = true)
        }
        return matching.minByOrNull { GeoUtils.calculateDistance(lat, lon, it.latitude, it.longitude) }
    }

    /**
     * Finds stations matching the address substring (case-insensitive).
     */
    suspend fun findByAddress(
        substring: String,
        lat: Double? = null,
        lon: Double? = null,
        fallbackStations: List<GasStation> = emptyList()
    ): List<GasStation> {
        val cleanedSub = substring.trim().lowercase()
        if (cleanedSub.isBlank()) return emptyList()

        val stations = getAllMergedStations(lat, lon, fallbackStations)

        // Generate stemmed/cleaned keywords (e.g. "свердловском" -> "свердловск")
        val keywords = cleanedSub.split(Regex("[\\s,.]+"))
            .map { it.trim() }
            .filter { it.length >= 3 && !STOP_WORDS.contains(it) }
            .map { word ->
                when {
                    word.endsWith("ском") || word.endsWith("ский") || word.endsWith("ском") -> word.dropLast(4)
                    word.endsWith("ом") || word.endsWith("ем") || word.endsWith("ах") || word.endsWith("е") || word.endsWith("у") -> word.dropLast(1)
                    else -> word
                }
            }

        return stations.filter { st ->
            val normAddress = st.address.lowercase()
            val normName = st.name.lowercase()
            val fullText = "$normName $normAddress"

            if (fullText.contains(cleanedSub)) {
                true
            } else if (keywords.isNotEmpty()) {
                keywords.all { kw -> fullText.contains(kw) }
            } else false
        }
    }

    /**
     * Resolves a natural query string to a single GasStation.
     * Extracts brand and/or address terms and returns the nearest matching station to lat, lon.
     */
    suspend fun resolveQuery(
        text: String,
        lat: Double? = null,
        lon: Double? = null,
        fallbackStations: List<GasStation> = emptyList()
    ): GasStation? {
        val userLat = lat ?: DEFAULT_LAT
        val userLon = lon ?: DEFAULT_LON
        val normText = text.trim().lowercase()

        val detectedBrand = KNOWN_BRANDS.firstOrNull { normText.contains(it) }

        // Extract potential address terms by stripping stop words and brand
        val words = normText.split(Regex("[\\s,.]+"))
            .map { it.trim() }
            .filter { word ->
                word.length >= 3 &&
                        !STOP_WORDS.contains(word) &&
                        (detectedBrand == null || !word.contains(detectedBrand))
            }

        val addressSubstring = words.joinToString(" ")

        val allStations = getAllMergedStations(userLat, userLon, fallbackStations)

        var candidateStations = allStations

        if (detectedBrand != null) {
            candidateStations = candidateStations.filter { st ->
                st.matchesBrand(detectedBrand) ||
                        st.brand.contains(detectedBrand, ignoreCase = true) ||
                        st.name.contains(detectedBrand, ignoreCase = true)
            }
        }

        if (addressSubstring.isNotBlank()) {
            val addressMatched = findByAddress(addressSubstring, userLat, userLon, fallbackStations)
            if (addressMatched.isNotEmpty()) {
                val intersection = candidateStations.filter { st -> addressMatched.any { it.id == st.id } }
                if (intersection.isNotEmpty()) {
                    candidateStations = intersection
                } else if (detectedBrand == null) {
                    candidateStations = addressMatched
                }
            }
        }

        if (candidateStations.isEmpty()) {
            // Fallback: search raw query text
            val searchResults = try { repository.searchStations(text) } catch (e: Exception) { emptyList() }
            if (searchResults.isNotEmpty()) {
                candidateStations = searchResults
            }
        }

        return candidateStations.minByOrNull { GeoUtils.calculateDistance(userLat, userLon, it.latitude, it.longitude) }
    }
}
