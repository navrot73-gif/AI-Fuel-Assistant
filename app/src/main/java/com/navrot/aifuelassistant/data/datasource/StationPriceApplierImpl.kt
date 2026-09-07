package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.UserPriceRepository
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.data.providers.FuelPriceInfo
import timber.log.Timber
import javax.inject.Inject

class StationPriceApplierImpl @Inject constructor(
    private val userPrices: UserPriceRepository,
    private val benzonavtProvider: BenzonavtProvider
) : StationPriceApplier {

    companion object {
        private const val TAG = "StationPriceApplier"
    }

    override suspend fun applyUserPrices(stations: List<GasStation>): List<GasStation> {
        val overrides = userPrices.getAll()
        if (overrides.isEmpty()) return stations
        return stations.map { station ->
            var userReported = false
            val newFuelTypes = station.fuelTypes.map { fuel ->
                val override = overrides[Pair(station.id, fuel.type)]
                if (override != null && override > 0) {
                    userReported = true
                    fuel.copy(
                        price = override,
                        source = FuelDataSource.USER_REPORT,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    fuel
                }
            }
            if (userReported) {
                station.copy(
                    fuelTypes = newFuelTypes,
                    dataSources = station.dataSources + FuelDataSource.USER_REPORT
                )
            } else {
                station.copy(fuelTypes = newFuelTypes)
            }
        }
    }

    override suspend fun applyAllPrices(stations: List<GasStation>): List<GasStation> {
        val withUser = applyUserPrices(stations)
        val city = benzonavtProvider.currentCity()
        val benzonavt = benzonavtProvider.fetchCityPrices(city)
        if (benzonavt.isEmpty()) return withUser

        val (fuelCodeEntries, stationEntries) = benzonavt.entries.partition { (key, _) ->
            isFuelCodeKey(key)
        }

        var result = withUser

        if (stationEntries.isNotEmpty()) {
            val observations = stationEntries.map { (key, info) ->
                FuelObservation(
                    brand = "",
                    address = key,
                    fuelType = "АИ-95",
                    available = info.available,
                    price = info.median
                )
            }
            result = RussiabaseMatcher.applyBenzonavtObservations(result, observations)
        } else {
            com.navrot.aifuelassistant.data.diagnostics.MapDiagnosticsTracker.benzonavtUnmatched = 0
        }

        if (fuelCodeEntries.isNotEmpty()) {
            val fuelMap = fuelCodeEntries.associate { it.key to it.value }
            result = result.map { station -> applyBenzonavtToStation(station, fuelMap, city) }
        }

        return result
    }

    override fun applyBenzonavtToStation(
        station: GasStation,
        benzonavt: Map<String, FuelPriceInfo>,
        city: String
    ): GasStation {
        var changed = false
        val now = System.currentTimeMillis()
        val newFuelTypes = station.fuelTypes.map { fuel ->
            val info = findPriceInfoForFuel(fuel.type, benzonavt)
                ?: return@map fuel
            val benzonavtTs = parseUpdatedAt(info.updatedAt)
            val effectiveTs = if (benzonavtTs > 0L) benzonavtTs else now

            val isRussiabaseNoFuel = (fuel.source == FuelDataSource.RUSSIABASE || station.dataSources.contains(FuelDataSource.RUSSIABASE)) &&
                    !fuel.available &&
                    (now - fuel.updatedAt <= 24 * 60 * 60 * 1000L)

            if (isRussiabaseNoFuel) {
                fuel
            } else if (effectiveTs >= fuel.updatedAt) {
                changed = true
                fuel.copy(
                    price = info.median,
                    available = info.available,
                    source = FuelDataSource.BENZONAVT,
                    sourceCount = info.sourceCount,
                    updatedAt = effectiveTs
                )
            } else {
                fuel
            }
        }
        if (!changed) return station
        Timber.tag(TAG).d(
            "station %d (%s): цены обновлены из BENZONAVT, город=%s",
            station.id,
            station.brand,
            city
        )
        return station.copy(
            fuelTypes = newFuelTypes,
            dataSources = station.dataSources + FuelDataSource.BENZONAVT
        )
    }

    private fun findPriceInfoForFuel(fuelType: String, benzonavt: Map<String, FuelPriceInfo>): FuelPriceInfo? {
        benzonavt[fuelType]?.let { return it }
        val mappedTarget = RussiabaseHtmlParser.mapMarkToFuelType(fuelType)
        for ((key, value) in benzonavt) {
            if (key.equals(fuelType, ignoreCase = true) || RussiabaseHtmlParser.mapMarkToFuelType(key).equals(mappedTarget, ignoreCase = true)) {
                return value
            }
        }
        return null
    }

    private fun isFuelCodeKey(key: String): Boolean {
        val norm = key.trim().lowercase().replace("-", "").replace(" ", "")
        return norm in setOf("ai92", "ai95", "ai98", "ai100", "dt", "gas", "аи92", "аи95", "аи98", "аи100", "дт", "газ", "diesel")
    }

    private fun parseUpdatedAt(value: String): Long {
        if (value.isBlank()) return 0L
        return try {
            java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.Instant.parse(value).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}
