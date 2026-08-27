package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.GasStation

interface StationLoader {
    suspend fun loadStations(): List<GasStation>
    suspend fun loadFromRemote(): List<GasStation>?
    suspend fun loadFromCache(): List<GasStation>?
    fun loadFromAssets(): List<GasStation>
}
