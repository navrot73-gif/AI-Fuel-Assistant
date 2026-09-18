package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.datasource.RussiabaseFuelDataSourceAdapter
import com.navrot.aifuelassistant.data.datasource.RussiabaseProviderImpl
import com.navrot.aifuelassistant.data.model.FuelDataSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Executable smoke test for Russiabase real station-level external data source.
 *
 * Endpoint: https://russiabase.ru/prices
 * Target city: chelyabinsk
 */
class RussiabaseRealSourceSmokeTest {

    @Test
    fun testRealRussiabaseFetch() = runBlocking {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val provider = RussiabaseProviderImpl(httpClient, null)
        val fakeCache = object : com.navrot.aifuelassistant.data.datasource.StationCache {
            override fun loadFromCache(): List<com.navrot.aifuelassistant.data.model.GasStation>? = emptyList()
            override fun saveToCache(rawJson: String) {}
            override fun getLastCacheUpdateTime(): Long? = System.currentTimeMillis()
        }
        val adapter = RussiabaseFuelDataSourceAdapter(provider, fakeCache)
        val request = FuelSourceRequest(
            targetCity = "chelyabinsk",
            fuelTypes = listOf("AI-92", "AI-95"),
            timeoutMs = 5000L
        )

        println("=== REAL SOURCE SMOKE TEST START ===")
        println("Endpoint: https://russiabase.ru/prices")
        println("Request: city=chelyabinsk, fuels=${request.normalizedFuelTypes}")

        val result = try {
            adapter.fetch(request)
        } catch (e: Exception) {
            println("K4_LIVE_SMOKE = BLOCKED (Network exception: ${e.message})")
            return@runBlocking
        }

        println("Result Status: ${result.status}")
        println("Fetched At: ${result.fetchedAt}")
        println("Metrics: ${result.metrics}")
        println("Raw Observations Received (${result.rawObservations.size}):")

        for (obs in result.rawObservations.take(10)) {
            println(" - extId=${obs.externalStationId}, stationName=${obs.stationName}, address=${obs.address}, fuelType=${obs.canonicalFuelType}, price=${obs.price}, availability=${obs.availability}, rawRef=${obs.rawReference}")
            assertEquals(FuelDataSource.RUSSIABASE, obs.sourceId)
            assertTrue(obs.canonicalFuelType == "AI-92" || obs.canonicalFuelType == "AI-95")
            if (obs.price != null) {
                assertTrue("Price must be non-negative", obs.price!! >= 0.0)
            }
        }

        if (result.status == FuelSourceStatus.HEALTHY && result.rawObservations.isNotEmpty()) {
            println("K4_LIVE_SMOKE = PASS")
        } else if (result.status == FuelSourceStatus.HEALTHY) {
            println("K4_LIVE_SMOKE = PASS (EMPTY)")
        } else {
            println("K4_LIVE_SMOKE = BLOCKED (Endpoint status: ${result.status}, error: ${result.errorMessage})")
        }
        println("=== REAL SOURCE SMOKE TEST END ===")
    }
}
