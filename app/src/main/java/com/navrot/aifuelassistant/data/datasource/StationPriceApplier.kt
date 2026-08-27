package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.FuelPriceInfo

interface StationPriceApplier {
    fun applyUserPrices(stations: List<GasStation>): List<GasStation>
    suspend fun applyAllPrices(stations: List<GasStation>): List<GasStation>
    fun applyBenzonavtToStation(
        station: GasStation,
        benzonavt: Map<String, FuelPriceInfo>,
        city: String
    ): GasStation
}
