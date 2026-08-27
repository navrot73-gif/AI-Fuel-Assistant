package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.GasStation

interface StationCache {
    fun loadFromCache(): List<GasStation>?
    fun saveToCache(rawJson: String)
    fun getLastCacheUpdateTime(): Long?
}
