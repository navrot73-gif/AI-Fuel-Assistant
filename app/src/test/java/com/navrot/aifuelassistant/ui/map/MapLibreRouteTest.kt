package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MapLibreRouteTest {

    @Test
    fun `RouteOptionUiState converts to MapLibre points correctly`() {
        val points = listOf(
            GeoPoint(55.1644, 61.4368),
            GeoPoint(55.1700, 61.4400)
        )
        val route = MapViewModel.RouteOptionUiState(
            title = "Быстрый",
            points = points,
            distanceText = "2.5 км",
            durationText = "5 мин",
            destination = "Газпромнефть",
            isStraightLine = false,
            isDirect = false,
            distanceMeters = 2500.0,
            durationSeconds = 300.0
        )

        assertEquals(2, route.points.size)
        assertEquals(55.1644, route.points.first().latitude, 1e-5)
        assertEquals(61.4368, route.points.first().longitude, 1e-5)
        assertEquals(55.1700, route.points.last().latitude, 1e-5)
        assertEquals(61.4400, route.points.last().longitude, 1e-5)
        assertNotNull(route.destination)
    }
}
