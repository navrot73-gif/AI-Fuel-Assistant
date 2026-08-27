package com.navrot.aifuelassistant.data.datasource

import android.content.Context
import com.navrot.aifuelassistant.data.model.GasStation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject

class StationLoaderImpl @Inject constructor(
    private val httpClient: OkHttpClient,
    private val stationCache: StationCache,
    private val jsonParser: StationJsonParser,
    @ApplicationContext private val context: Context
) : StationLoader {

    companion object {
        private const val TAG = "StationLoader"
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/navrot73-gif/AI-Fuel-Assistant/main/app/src/main/assets/stations.json"
    }

    override suspend fun loadStations(): List<GasStation> {
        return loadFromRemote() ?: loadFromCache() ?: loadFromAssets()
    }

    override suspend fun loadFromRemote(): List<GasStation>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(REMOTE_URL).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val stations = jsonParser.parseJson(body)
                if (stations.isNotEmpty()) {
                    stationCache.saveToCache(body)
                }
                stations.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Не удалось загрузить станции из сети: %s", e.message)
            null
        }
    }

    override suspend fun loadFromCache(): List<GasStation>? = withContext(Dispatchers.IO) {
        stationCache.loadFromCache()
    }

    override fun loadFromAssets(): List<GasStation> {
        return try {
            val jsonString = context.assets.open("stations.json").bufferedReader().use { it.readText() }
            jsonParser.parseJson(jsonString)
        } catch (e: Exception) {
            Timber.tag(TAG).w("Не удалось загрузить станции из assets: %s", e.message)
            emptyList()
        }
    }
}
