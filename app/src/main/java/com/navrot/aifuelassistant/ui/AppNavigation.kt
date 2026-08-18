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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.toRoute
import com.navrot.aifuelassistant.R
import com.navrot.aifuelassistant.features.dashboard.DashboardScreen
import com.navrot.aifuelassistant.ui.fuel.AddFuelRecordScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen
import com.navrot.aifuelassistant.ui.map.MapScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.vehicles.GarageListScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleDetailScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleListScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleViewModel
import androidx.hilt.navigation.compose.hiltViewModel
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
@SerialName("map/build_route_station_id")
data class MapBuildRouteRoute(val stationId: Int)

@Serializable
@SerialName("map/show_stations")
object MapShowStationsRoute {
    val route = "map/show_stations"
}

@Serializable
@SerialName("ai")
object DashboardRoute {
    val route = "ai"
}

// Garage sealed class for nested navigation
sealed class GarageDestination {
    object List : GarageDestination()
    data class Detail(val vehicleId: Long, val vehicleName: String) : GarageDestination()
}

const val GARAGE_DETAIL_ROUTE = "garage/detail/{vehicleId}/{vehicleName}"

@Serializable
@SerialName("garage")
object GarageRoute {
    val route = "garage"
}

@Serializable
@SerialName("add_vehicle")
object AddVehicleRoute {
    val route = "add_vehicle"
}

@Serializable
@SerialName("fuel_records")
data class FuelRecordListRoute(val vehicleId: Long, val vehicleName: String)

@Serializable
@SerialName("add_fuel_record")
data class AddFuelRecordRoute(val vehicleId: Long, val vehicleName: String)

private data class Tab(val route: String, val iconRes: Int, val title: String)

private val TABS = listOf(
    Tab(MapRoute.route, R.drawable.ic_tab_map, "Карта"),
    Tab(DashboardRoute.route, R.drawable.ic_tab_ai, "AI"),
    Tab(GarageRoute.route, R.drawable.ic_tab_car, "Гараж")
)

private val TAB_ACTIVE_COLOR = Color(0xFFE8A750)
private val TAB_INACTIVE_COLOR = Color(0xFF8A949E)

private val TAB_ROUTES = TABS.map { it.route }.toSet()

// Helper to check if route is in garage sub-navigation
private fun String?.isGarageRoute(): Boolean {
    if (this == null) return false
    return this == "garage" || this == "garage_list" || this.startsWith("garage/")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TAB_ROUTES || currentRoute.isGarageRoute()

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
                        val selected = if (tab.route == GarageRoute.route) {
                            currentRoute.isGarageRoute()
                        } else {
                            currentRoute == tab.route
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { go(tab.route) },
                            icon = {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(id = tab.iconRes),
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) TAB_ACTIVE_COLOR else TAB_INACTIVE_COLOR
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TAB_ACTIVE_COLOR,
                                unselectedIconColor = TAB_INACTIVE_COLOR,
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
                val pendingOpenStationId by backStackEntry.savedStateHandle
                    .getStateFlow<Int?>("open_station_id", null)
                    .collectAsStateWithLifecycle()
                val aiAnswerText by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("ai_answer_text", null)
                    .collectAsStateWithLifecycle()
                MapScreen(
                    pendingRouteStationId = pendingRouteId,
                    pendingOpenStationId = pendingOpenStationId,
                    aiAnswerText = aiAnswerText,
                    onConsumePendingRoute = {
                        backStackEntry.savedStateHandle.remove<Int>("build_route_station_id")
                    },
                    onConsumePendingOpenStation = {
                        backStackEntry.savedStateHandle.remove<Int>("open_station_id")
                        backStackEntry.savedStateHandle.remove<String>("ai_answer_text")
                    }
                )
            }

            composable<MapBuildRouteRoute> { entry ->
                val args = entry.toRoute<MapBuildRouteRoute>()
                MapScreen(pendingRouteStationId = args.stationId)
            }

            composable<MapShowStationsRoute> {
                MapScreen(showStationList = true)
            }

            composable<DashboardRoute> { DashboardScreen() }

            // Garage nested navigation
            navigation(startDestination = "garage_list", route = "garage") {
                composable(route = "garage_list") {
                    val garageViewModel: VehicleViewModel = hiltViewModel()
                    GarageListScreen(
                        onAddClick = { navController.navigate(AddVehicleRoute) },
                        onVehicleClick = { vehicleId ->
                            val vehicle = garageViewModel.vehiclesWithStats.value.find { it.id == vehicleId }
                            val vehicleName = vehicle?.name ?: ""
                            val routeStr = GARAGE_DETAIL_ROUTE
                                .replace("{vehicleId}", vehicleId.toString())
                                .replace("{vehicleName}", vehicleName)
                            navController.navigate(routeStr)
                        },
                        viewModel = garageViewModel
                    )
                }
                
                composable(
                    route = GARAGE_DETAIL_ROUTE,
                    arguments = listOf(
                        navArgument("vehicleId") { type = NavType.LongType },
                        navArgument("vehicleName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val arguments = backStackEntry.arguments
                    val vehicleId = arguments?.getLong("vehicleId") ?: -1L
                    val vehicleName = arguments?.getString("vehicleName") ?: ""
                    VehicleDetailScreen(
                        vehicleId = vehicleId,
                        vehicleName = vehicleName,
                        onBack = { navController.popBackStack() },
                        onAddClick = { navController.navigate(AddFuelRecordRoute(vehicleId, vehicleName)) },
                        viewModel = hiltViewModel()
                    )
                }
            }

            composable<AddVehicleRoute> {
                AddVehicleScreen(onNavigateBack = { navController.popBackStack() })
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

            composable<AddFuelRecordRoute> { entry ->
                val args = entry.toRoute<AddFuelRecordRoute>()
                AddFuelRecordScreen(
                    vehicleId = args.vehicleId,
                    defaultFuelType = "АИ-95",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}