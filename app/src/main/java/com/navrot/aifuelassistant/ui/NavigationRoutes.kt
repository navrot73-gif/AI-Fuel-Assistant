package com.navrot.aifuelassistant.ui

import kotlinx.serialization.Serializable

sealed interface NavRoute {
    @Serializable data object MainTabs : NavRoute
    @Serializable data object Map : NavRoute
    @Serializable data class MapBuildRoute(val stationId: Int) : NavRoute
    @Serializable data object MapShowStations : NavRoute
    @Serializable data object Dashboard : NavRoute
    @Serializable data object Garage : NavRoute
    @Serializable data object GarageList : NavRoute
    @Serializable data class GarageDetail(
        val vehicleId: Long,
        val vehicleName: String = ""
    ) : NavRoute
    @Serializable data class AddVehicle(val vehicleId: Long? = null) : NavRoute
    @Serializable data class FuelRecordList(
        val vehicleId: Long,
        val vehicleName: String = ""
    ) : NavRoute
    @Serializable data class AddFuelRecord(
        val vehicleId: Long,
        val vehicleName: String = ""
    ) : NavRoute
    @Serializable data object Reports : NavRoute

    @Serializable data object Vehicles : NavRoute
    @Serializable data object Settings : NavRoute
    @Serializable data class FuelForm(
        val vehicleId: Long? = null,
        val stationId: Int? = null
    ) : NavRoute
    @Serializable data class VehicleDetail(val vehicleId: Long) : NavRoute
    @Serializable data class StationDetail(
        val stationId: Int,
        val fuelType: String? = null
    ) : NavRoute
}

typealias MainTabsRoute = NavRoute.MainTabs
typealias MapRoute = NavRoute.Map
typealias MapBuildRouteRoute = NavRoute.MapBuildRoute
typealias MapShowStationsRoute = NavRoute.MapShowStations
typealias DashboardRoute = NavRoute.Dashboard
typealias GarageListRoute = NavRoute.GarageList
typealias GarageDetailRoute = NavRoute.GarageDetail
typealias GarageRoute = NavRoute.Garage
typealias AddVehicleRoute = NavRoute.AddVehicle
typealias FuelRecordListRoute = NavRoute.FuelRecordList
typealias AddFuelRecordRoute = NavRoute.AddFuelRecord
typealias ReportsRoute = NavRoute.Reports
