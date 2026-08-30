package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.GasStation

interface OverpassFuelProvider {
    suspend fun fetchStations(lat: Double, lon: Double, radiusMeters: Double): List<GasStation>
}
