package com.navrot.aifuelassistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.navrot.aifuelassistant.features.dashboard.DashboardScreen
import com.navrot.aifuelassistant.ui.fuel.AddFuelRecordScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen
import com.navrot.aifuelassistant.ui.fuel.GasStationDetailScreen
import com.navrot.aifuelassistant.ui.map.MapScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleListScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Type-safe navigation routes
@Serializable
@SerialName("map")
object MapRoute {
    val route = "map"
}

@Serializable
@SerialName("ai")
object DashboardRoute {
    val route = "ai"
}

@Serializable
@SerialName("garage")
object VehicleListRoute {
    val route = "garage"
}

@Serializable
@SerialName("add_vehicle")
object AddVehicleRoute {
    val route = "add_vehicle"
}

@Serializable
@SerialName("station_detail")
data class StationDetailRoute(val stationId: Int)

@Serializable
@SerialName("fuel_records")
data class FuelRecordListRoute(val vehicleId: Long, val vehicleName: String)

@Serializable
@SerialName("add_fuel_record")
data class AddFuelRecordRoute(val vehicleId: Long, val vehicleName: String)

private data class Tab(val route: String, val glyph: String, val title: String)

private val TABS = listOf(
    Tab(MapRoute.route, "🗺️", "Карта"),
    Tab(DashboardRoute.route, "🤖", "AI"),
    Tab(VehicleListRoute.route, "🚗", "Гараж")
)

private val TAB_ROUTES = TABS.map { it.route }.toSet()

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TAB_ROUTES

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { go(tab.route) },
                            icon = {
                                Text(
                                    text = tab.glyph,
                                    fontSize = 22.sp,
                                    letterSpacing = 0.sp
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MapRoute.route,
            modifier = Modifier.padding(padding)
        ) {
            composable<MapRoute> { backStackEntry ->
                val pendingRouteId by backStackEntry.savedStateHandle
                    .getStateFlow<Int?>("build_route_station_id", null)
                    .collectAsStateWithLifecycle()
                MapScreen(
                    onStationClick = { station ->
                        navController.navigate(StationDetailRoute(station.id))
                    },
                    onRouteClick = { stationId ->
                        navController.navigate(StationDetailRoute(stationId))
                    },
                    pendingRouteStationId = pendingRouteId,
                    onConsumePendingRoute = {
                        backStackEntry.savedStateHandle.remove<Int>("build_route_station_id")
                    }
                )
            }

            composable<DashboardRoute> { DashboardScreen() }

            composable<VehicleListRoute> {
                VehicleListScreen(
                    onAddClick = { navController.navigate(AddVehicleRoute) },
                    onVehicleClick = { vehicleId, vehicleName ->
                        navController.navigate(FuelRecordListRoute(vehicleId, vehicleName))
                    }
                )
            }

            composable<AddVehicleRoute> {
                AddVehicleScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable<StationDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<StationDetailRoute>()
                GasStationDetailScreen(
                    stationId = route.stationId,
                    onBack = { navController.popBackStack() },
                    onRouteClick = {
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set("build_route_station_id", route.stationId)
                        navController.popBackStack()
                    }
                )
            }

            composable<FuelRecordListRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<FuelRecordListRoute>()
                FuelRecordListScreen(
                    vehicleId = route.vehicleId,
                    vehicleName = route.vehicleName,
                    onBack = { navController.popBackStack() },
                    onAddClick = {
                        navController.navigate(AddFuelRecordRoute(route.vehicleId, route.vehicleName))
                    }
                )
            }

            composable<AddFuelRecordRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AddFuelRecordRoute>()
                AddFuelRecordScreen(
                    vehicleId = route.vehicleId,
                    defaultFuelType = "АИ-95",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}