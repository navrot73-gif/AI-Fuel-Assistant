package com.navrot.aifuelassistant.data.datasource

import android.content.Context
import com.navrot.aifuelassistant.data.model.GasStation
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class StationCacheImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonParser: StationJsonParser
) : StationCache {

    companion object {
        private const val TAG = "StationCache"
        private const val CACHE_FILE = "stations_cache.json"
    }

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE)

    override fun loadFromCache(): List<GasStation>? {
        return try {
            val file = cacheFile()
            if (!file.exists()) null
            else jsonParser.parseJson(file.readText()).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Не удалось загрузить станции из кеша: %s", e.message)
            null
        }
    }

    override fun saveToCache(rawJson: String) {
        try {
            cacheFile().writeText(rawJson)
        } catch (e: Exception) {
            Timber.tag(TAG).w("Не удалось сохранить станции в кеш: %s", e.message)
        }
    }

    override fun getLastCacheUpdateTime(): Long? {
        val file = cacheFile()
        return if (file.exists()) file.lastModified() else null
    }
}
