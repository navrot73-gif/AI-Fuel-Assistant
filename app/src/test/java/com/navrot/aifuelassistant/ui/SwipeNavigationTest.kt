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
        assertEquals(NavRoute.Map, TABS[0].route)
        assertEquals("Карта", TABS[0].title)
        assertEquals(NavRoute.Dashboard, TABS[1].route)
        assertEquals("AI", TABS[1].title)
        assertEquals(NavRoute.Garage, TABS[2].route)
        assertEquals("Гараж", TABS[2].title)
    }

    @Test
    fun `test getPageIndexForRoute returns correct page index for main tabs`() {
        assertEquals(0, getPageIndexForRoute(NavRoute.Map))
        assertEquals(0, getPageIndexForRoute(NavRoute.MapBuildRoute(1)))
        assertEquals(0, getPageIndexForRoute(NavRoute.MapShowStations))
        assertEquals(0, getPageIndexForRoute("map"))
        assertEquals(0, getPageIndexForRoute("map/build_route_station_id"))
        assertEquals(0, getPageIndexForRoute("map/show_stations"))

        assertEquals(1, getPageIndexForRoute(NavRoute.Dashboard))
        assertEquals(1, getPageIndexForRoute("ai"))

        assertEquals(2, getPageIndexForRoute(NavRoute.Garage))
        assertEquals(2, getPageIndexForRoute(NavRoute.GarageList))
        assertEquals(2, getPageIndexForRoute(NavRoute.GarageDetail(1, "Car")))
        assertEquals(2, getPageIndexForRoute("garage"))
        assertEquals(2, getPageIndexForRoute("garage_list"))
        assertEquals(2, getPageIndexForRoute("garage_detail/1/Car"))
    }

    @Test
    fun `test getPageIndexForRoute returns null for detail screens`() {
        assertNull(getPageIndexForRoute(NavRoute.Reports))
        assertNull(getPageIndexForRoute(NavRoute.AddVehicle()))
        assertNull(getPageIndexForRoute(NavRoute.AddFuelRecord(1, "Car")))
        assertNull(getPageIndexForRoute(NavRoute.FuelRecordList(1, "Car")))
        assertNull(getPageIndexForRoute("reports"))
        assertNull(getPageIndexForRoute("add_vehicle"))
        assertNull(getPageIndexForRoute("add_fuel_record"))
        assertNull(getPageIndexForRoute("fuel_records"))
        assertNull(getPageIndexForRoute(null))
    }

    @Test
    fun `test isGarageRoute helper`() {
        assertTrue(NavRoute.Garage.isGarageRoute())
        assertTrue(NavRoute.GarageList.isGarageRoute())
        assertTrue(NavRoute.GarageDetail(1, "Car").isGarageRoute())
        assertTrue("garage".isGarageRoute())
        assertTrue("garage_list".isGarageRoute())
        assertTrue("garage_detail".isGarageRoute())
        assertFalse(NavRoute.Map.isGarageRoute())
        assertFalse(NavRoute.Dashboard.isGarageRoute())
        assertFalse("map".isGarageRoute())
        assertFalse("ai".isGarageRoute())
        assertFalse(null.isGarageRoute())
    }

    @Test
    fun `test isMapRoute helper`() {
        assertTrue(NavRoute.Map.isMapRoute())
        assertTrue(NavRoute.MapShowStations.isMapRoute())
        assertTrue(NavRoute.MapBuildRoute(1).isMapRoute())
        assertTrue("map".isMapRoute())
        assertTrue("map/show_stations".isMapRoute())
        assertTrue("map/build_route_station_id".isMapRoute())
        assertFalse(NavRoute.Garage.isMapRoute())
        assertFalse(NavRoute.Dashboard.isMapRoute())
        assertFalse("garage".isMapRoute())
        assertFalse("ai".isMapRoute())
        assertFalse(null.isMapRoute())
    }
}
