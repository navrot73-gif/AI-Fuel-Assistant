package com.navrot.aifuelassistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for HorizontalPager swipe navigation and tab synchronization logic.
 */
class SwipeNavigationTest {

    @Test
    fun `test main tabs list contains exactly 3 main pages in correct order`() {
        assertEquals(3, TABS.size)
        assertEquals(MapRoute.route, TABS[0].route)
        assertEquals("Карта", TABS[0].title)
        assertEquals(DashboardRoute.route, TABS[1].route)
        assertEquals("AI", TABS[1].title)
        assertEquals(GarageRoute.route, TABS[2].route)
        assertEquals("Гараж", TABS[2].title)
    }

    @Test
    fun `test getPageIndexForRoute returns correct page index for main tabs`() {
        assertEquals(0, getPageIndexForRoute("map"))
        assertEquals(0, getPageIndexForRoute("map/build_route_station_id"))
        assertEquals(0, getPageIndexForRoute("map/show_stations"))

        assertEquals(1, getPageIndexForRoute("ai"))

        assertEquals(2, getPageIndexForRoute("garage"))
        assertEquals(2, getPageIndexForRoute("garage_list"))
        assertEquals(2, getPageIndexForRoute("garage_detail/1/Car"))
    }

    @Test
    fun `test getPageIndexForRoute returns null for detail screens`() {
        assertNull(getPageIndexForRoute("reports"))
        assertNull(getPageIndexForRoute("add_vehicle"))
        assertNull(getPageIndexForRoute("add_fuel_record"))
        assertNull(getPageIndexForRoute("fuel_records"))
        assertNull(getPageIndexForRoute(null))
    }

    @Test
    fun `test isGarageRoute helper`() {
        assertTrue("garage".isGarageRoute())
        assertTrue("garage_list".isGarageRoute())
        assertTrue("garage_detail".isGarageRoute())
        assertFalse("map".isGarageRoute())
        assertFalse("ai".isGarageRoute())
        assertFalse(null.isGarageRoute())
    }

    @Test
    fun `test isMapRoute helper`() {
        assertTrue("map".isMapRoute())
        assertTrue("map/show_stations".isMapRoute())
        assertTrue("map/build_route_station_id".isMapRoute())
        assertFalse("garage".isMapRoute())
        assertFalse("ai".isMapRoute())
        assertFalse(null.isMapRoute())
    }
}
