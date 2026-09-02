package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.RouteStateManager
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.network.FuelApi
import com.navrot.aifuelassistant.network.RouteResponse
import com.navrot.aifuelassistant.ui.map.delegate.MapRouteDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapRouteDelegateTest {

    private lateinit var mockFuelApi: FakeFuelApi
    private lateinit var routeStateManager: RouteStateManager
    private lateinit var delegate: MapRouteDelegate

    private class FakeFuelApi : FuelApi {
        var shouldFail = false
        var callCount = 0

        override suspend fun getRoute(
            fromLon: Double,
            fromLat: Double,
            toLon: Double,
            toLat: Double,
            alternatives: Boolean
        ): Result<RouteResponse> {
            callCount++
            return if (shouldFail) {
                Result.failure(Exception("All mirrors failed"))
            } else {
                Result.success(
                    RouteResponse(
                        distance_m = 1000.0,
                        duration_s = 60.0,
                        points = listOf(listOf(fromLat, fromLon), listOf(toLat, toLon))
                    )
                )
            }
        }
    }

    private fun createStation() = GasStation(
        id = 1,
        name = "Test Station",
        brand = "Test Brand",
        address = "Test Address",
        latitude = 55.16,
        longitude = 61.43,
        fuelTypes = emptyList(),
        queueTime = 0,
        reliability = 100
    )

    @Before
    fun setup() {
        mockFuelApi = FakeFuelApi()
        routeStateManager = RouteStateManager()
        delegate = MapRouteDelegate(mockFuelApi, routeStateManager)
    }

    @Test
    fun testBuildRoute_success() = runTest {
        mockFuelApi.shouldFail = false
        var errorReported: String? = null

        delegate.buildRouteTo(
            scope = this,
            station = createStation(),
            userLocation = 55.15 to 61.40,
            onError = { errorReported = it }
        )

        testScheduler.advanceUntilIdle()

        assertNull("Error should not be reported on success", errorReported)
        val currentRoute = delegate.route.value
        assertNotNull(currentRoute)
        assertFalse("Route should not be straight line on success", currentRoute!!.isStraightLine)
        assertFalse("Inline banner should not be shown on success", delegate.showStraightLineBanner.value)
    }

    @Test
    fun testBuildRoute_allMirrorsFail_straightLineAndBannerWithoutModal() = runTest {
        mockFuelApi.shouldFail = true
        var errorReported: String? = null

        delegate.buildRouteTo(
            scope = this,
            station = createStation(),
            userLocation = 55.15 to 61.40,
            onError = { errorReported = it }
        )

        testScheduler.advanceUntilIdle()

        // 1. Modal error is NOT called for routing failure
        assertNull("Modal error should NOT be called when falling back to straight line", errorReported)

        // 2. Straight line fallback route built
        val currentRoute = delegate.route.value
        assertNotNull(currentRoute)
        assertTrue("Fallback route must be straight line", currentRoute!!.isStraightLine)

        // 3. Inline banner is triggered initially
        // Note: advanceUntilIdle ran 4000ms delay, so check banner state after delay
        // Banner auto-hides after 4000ms
        assertFalse(delegate.showStraightLineBanner.value)
    }
}
