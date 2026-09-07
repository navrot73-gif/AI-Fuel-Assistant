package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.matchesBrand
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.geo.GeoUtils

object StationQueryFacade {

    private val STREET_DICTIONARY = setOf(
        "свердловский", "тракт", "мира", "курчатова", "артиллерийская", "копейское", "шоссе",
        "механическая", "игуменка", "победы", "бажова", "комарова", "энгельса", "первой",
        "пятилетки", "городская", "московская", "гагарина", "ленина", "коммунистический",
        "советская", "строителей", "свободы", "чичерина", "салавата", "юлаева",
        "молодогвардейцев", "комсомольский", "дзержинского", "машиностроителей",
        "новороссийская", "троицкий", "блюхера", "худякова", "лесопарковая", "воровского",
        "труда", "российская", "черкасская", "улица", "ул", "проспект", "пр", "проезд",
        "переулок", "пер", "бульвар", "набережная", "квартал", "площадь", "перекресток",
        "перекрёсток"
    )

    private val BRAND_KEYWORDS = listOf(
        "газпромнефть", "газпром", "лукойл", "роснефть", "татнефть", "смарт", "шелл",
        "новатек", "башнефть", "опти"
    )

    private val STOP_WORDS = setOf(
        "азс", "заправка", "заправку", "заправки", "заправь", "заправочная", "заправочной", "заправочную",
        "станции", "станция", "станцию", "станций",
        "маршрут", "построй", "доведи", "ближайшая", "ближайший", "ближайшую", "ближайшей",
        "ближайшего", "ближайшие", "ближе", "близко",
        "дешевле", "где", "на", "до", "в", "к", "по", "у", "эй", "привет", "найди",
        "покажи", "топливо", "цена", "наличие", "есть", "ли", "руб", "рублей",
        "аи-92", "аи-95", "аи-98", "аи-100", "92", "95", "98", "100", "дт", "дизель", "газ"
    )

    data class ParsedQuery(
        val brand: String?,
        val streetTokens: List<String>,
        val houseNumber: String?
    )

    fun parseQuery(text: String): ParsedQuery {
        val normalizedText = text.lowercase().replace('ё', 'е')
        val matchedBrand = BRAND_KEYWORDS.find { normalizedText.contains(it) }

        val rawTokens = normalizedText.split(Regex("[^a-zA-Z0-9а-яА-ЯёЁ/-]+"))
            .filter { it.isNotBlank() }

        val streetTokens = mutableListOf<String>()
        var houseNumber: String? = null

        for (token in rawTokens) {
            val clean = token.trim('/')
            if (clean.isEmpty()) continue

            if (clean.matches(Regex("^\\d+.*"))) {
                if (houseNumber == null) {
                    houseNumber = clean
                }
                continue
            }

            if (BRAND_KEYWORDS.contains(clean) || STOP_WORDS.contains(clean)) {
                continue
            }

            if (STREET_DICTIONARY.contains(clean) || clean.length >= 3) {
                streetTokens.add(clean)
            }
        }

        return ParsedQuery(
            brand = matchedBrand,
            streetTokens = streetTokens,
            houseNumber = houseNumber
        )
    }

    /**
     * Resolves a text query to a gas station result using address matching and fallback to brand/GPS nearest.
     */
    fun resolveQuery(
        text: String,
        stations: List<GasStation>,
        userLat: Double,
        userLon: Double,
        fuelType: String? = null
    ): NearestBrandResult? {
        if (stations.isEmpty()) return null

        val parsed = parseQuery(text)
        val brand = parsed.brand ?: extractBrandFallback(text)

        val hasAddressTokens = parsed.streetTokens.isNotEmpty() || parsed.houseNumber != null

        if (hasAddressTokens) {
            val candidateStations = stations.filter { st ->
                val normAddr = (st.address + " " + st.name + " " + st.brand).lowercase().replace('ё', 'е')

                val matchesStreetTokens = parsed.streetTokens.all { token ->
                    normAddr.contains(token)
                }

                val matchesHouse = if (parsed.houseNumber != null) {
                    val queryHouseLower = parsed.houseNumber.lowercase()
                    // Extract house tokens from station address (e.g., "12", "12в", "16/3", "40/1")
                    val addrTokens = normAddr.split(Regex("[^a-zA-Z0-9а-яА-ЯёЁ/-]+"))
                    addrTokens.any { t ->
                        t.startsWith(queryHouseLower) || queryHouseLower.startsWith(t)
                    }
                } else true

                val matchesBrandIfPresent = if (brand != null) {
                    st.matchesBrand(brand) || normAddr.contains(brand)
                } else true

                matchesStreetTokens && matchesHouse && matchesBrandIfPresent
            }

            if (candidateStations.isNotEmpty()) {
                val nearest = candidateStations.minByOrNull {
                    GeoUtils.calculateDistance(userLat, userLon, it.latitude, it.longitude)
                }
                if (nearest != null) {
                    return createResultForStation(nearest, stations, fuelType)
                }
            } else {
                // RULE 4: НИКОГДА не возвращать станцию, не совпавшую по адресным токенам, если токены были в запросе
                return null
            }
        }

        val targetBrand = brand ?: "газпромнефть"
        return NearestStationFinder.findNearestStationByBrand(
            stations = stations,
            brand = targetBrand,
            userLat = userLat,
            userLon = userLon,
            fuelType = fuelType
        )
    }

    fun getTopCandidates(
        stations: List<GasStation>,
        brand: String,
        userLat: Double,
        userLon: Double,
        fuelType: String? = null,
        limit: Int = 3
    ): List<StationCandidate> {
        return NearestStationFinder.getTopCandidates(
            stations = stations,
            brand = brand,
            userLat = userLat,
            userLon = userLon,
            fuelType = fuelType,
            limit = limit
        )
    }

    private fun extractBrandFallback(text: String): String? {
        val lower = text.lowercase()
        return BRAND_KEYWORDS.find { lower.contains(it) }
    }

    private fun createResultForStation(
        nearest: GasStation,
        stations: List<GasStation>,
        fuelType: String?
    ): NearestBrandResult {
        val availability = PriceReliabilityCalculator.calculateFuelAvailability(nearest, fuelType)
        val fuelPrice = fuelType?.let { ft -> nearest.fuelTypes.find { it.type == ft }?.price ?: 0.0 } ?: (nearest.fuelTypes.firstOrNull()?.price ?: 0.0)
        val hasFuel = availability == FuelAvailabilityStatus.AVAILABLE && fuelPrice > 0.0

        val alternative = if (!hasFuel) {
            stations.filter { st ->
                val status = PriceReliabilityCalculator.calculateFuelAvailability(st, fuelType)
                val price = fuelType?.let { ft -> st.fuelTypes.find { it.type == ft }?.price ?: 0.0 } ?: (st.fuelTypes.firstOrNull()?.price ?: 0.0)
                status == FuelAvailabilityStatus.AVAILABLE && price > 0.0 && st.id != nearest.id
            }.minByOrNull {
                GeoUtils.calculateDistance(nearest.latitude, nearest.longitude, it.latitude, it.longitude)
            }
        } else null

        return NearestBrandResult(
            nearestStation = nearest,
            isNearestFuelAvailable = hasFuel,
            alternativeWithFuel = alternative
        )
    }
}
