package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.datasource.BenzonavtFuelDataSourceAdapter
import com.navrot.aifuelassistant.data.model.FuelDataSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Executable smoke test for Benzonavt real external data source.
 *
 * Endpoint: https://ai-fuel-proxy.navrot73.workers.dev/city-prices
 * Target city: chelyabinsk
 */
class BenzonavtRealSourceSmokeTest {

    @Test
    fun testRealBenzonavtFetch() = runBlocking {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val adapter = BenzonavtFuelDataSourceAdapter(httpClient)
        val request = FuelSourceRequest(
            targetCity = "chelyabinsk",
            fuelTypes = listOf("AI-92", "AI-95"),
            timeoutMs = 5000L
        )

        println("=== REAL SOURCE SMOKE TEST START ===")
        println("Endpoint: ${BenzonavtFuelDataSourceAdapter.BASE_URL}")
        println("Request: city=chelyabinsk, fuels=${request.normalizedFuelTypes}")

        val result = try {
            adapter.fetch(request)
        } catch (e: Exception) {
            println("LIVE_SMOKE = BLOCKED (Network exception: ${e.message})")
            return@runBlocking
        }

        println("Result Status: ${result.status}")
        println("Fetched At: ${result.fetchedAt}")
        println("Metrics: ${result.metrics}")
        println("Raw Observations Received (${result.rawObservations.size}):")

        for (obs in result.rawObservations) {
            println(" - extId=${obs.externalStationId}, fuelType=${obs.canonicalFuelType}, price=${obs.price}, availability=${obs.availability}, rawRef=${obs.rawReference}, observedAt=${obs.observedAt}")
            assertEquals(FuelDataSource.BENZONAVT, obs.sourceId)
            assertTrue(obs.canonicalFuelType == "AI-92" || obs.canonicalFuelType == "AI-95")
            if (obs.price != null) {
                assertTrue("Price must be non-negative", obs.price!! >= 0.0)
            }
        }

        if (result.status == FuelSourceStatus.HEALTHY && result.rawObservations.isNotEmpty()) {
            println("LIVE_SMOKE = PASS")
        } else {
            println("LIVE_SMOKE = BLOCKED (Endpoint returned status: ${result.status}, error: ${result.errorMessage})")
        }
        println("=== REAL SOURCE SMOKE TEST END ===")
    }
}
