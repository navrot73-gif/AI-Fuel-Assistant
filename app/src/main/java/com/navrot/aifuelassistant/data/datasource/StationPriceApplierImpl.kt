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

    override fun applyUserPrices(stations: List<GasStation>): List<GasStation> {
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
        return withUser.map { station -> applyBenzonavtToStation(station, benzonavt, city) }
    }

    override fun applyBenzonavtToStation(
        station: GasStation,
        benzonavt: Map<String, FuelPriceInfo>,
        city: String
    ): GasStation {
        var changed = false
        val newFuelTypes = station.fuelTypes.map { fuel ->
            val info = benzonavt[fuel.type]
                ?: benzonavt.entries.firstOrNull { it.key.equals(fuel.type, ignoreCase = true) }?.value
                ?: return@map fuel
            val benzonavtTs = parseUpdatedAt(info.updatedAt)
            if (benzonavtTs > fuel.updatedAt) {
                changed = true
                fuel.copy(
                    price = info.median,
                    available = true,
                    source = FuelDataSource.BENZONAVT,
                    sourceCount = info.sourceCount,
                    updatedAt = benzonavtTs
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
