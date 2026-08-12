package com.navrot.aifuelassistant.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph
import androidx.navigation.NavType
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.features.dashboard.DashboardScreen
import com.navrot.aifuelassistant.ui.fuel.AddFuelRecordScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen
import com.navrot.aifuelassistant.ui.fuel.GasStationDetailScreen
import com.navrot.aifuelassistant.ui.map.MapScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleListScreen
import kotlinx.serialization.Serializable

// Type-safe navigation routes
@Serializable
object MapRoute {
    val route = "map"
}

@Serializable
object DashboardRoute {
    val route = "ai"
}

@Serializable
object VehicleListRoute {
    val route = "garage"
}

@Serializable
object AddVehicleRoute {
    val route = "add_vehicle"
}

@Serializable
object FuelRecordListRoute {
    val route = "fuel_records"
}

@Serializable
object AddFuelRecordRoute {
    val route = "add_fuel_record"
}

@Serializable
data class StationDetailRoute(val stationId: Int) {
    val route = "station_detail/$stationId"
}

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
            composable<MapRoute> {
                MapScreen(
                    onStationClick = { station ->
                        navController.navigate(StationDetailRoute(station.id).route)
                    },
                    onRouteClick = { stationId ->
                        navController.navigate(StationDetailRoute(stationId).route)
                    }
                )
            }

            composable<DashboardRoute> { DashboardScreen() }

            composable<VehicleListRoute> {
                VehicleListScreen(
                    onAddClick = { navController.navigate(AddVehicleRoute.route) },
                    onVehicleClick = { vehicleId, vehicleName ->
                        val encoded = Uri.encode(vehicleName)
                        navController.navigate("${FuelRecordListRoute.route}/$vehicleId/$encoded")
                    }
                )
            }

            composable<AddVehicleRoute> {
                AddVehicleScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = "station_detail/{stationId}",
                arguments = listOf(navArgument("stationId") { type = NavType.IntType })
            ) { backStackEntry ->
                val stationId = androidx.navigation.NavBackStackEntry.getInt(backStackEntry, "stationId")
                GasStationDetailScreen(
                    stationId = stationId,
                    onBack = { navController.popBackStack() },
                    onRouteClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "${FuelRecordListRoute.route}/{vehicleId}/{vehicleName}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType },
                    navArgument("vehicleName") { type = NavType.StringType }
                )
            ) { entry ->
                val vehicleId = androidx.navigation.NavBackStackEntry.getLong(entry, "vehicleId")
                val vehicleName = Uri.decode(androidx.navigation.NavBackStackEntry.getString(entry, "vehicleName") ?: "")
                FuelRecordListScreen(
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    onBack = { navController.popBackStack() },
                    onAddClick = {
                        val encoded = Uri.encode(vehicleName)
                        navController.navigate("${AddFuelRecordRoute.route}/$vehicleId/$encoded")
                    }
                )
            }

            composable(
                route = "${AddFuelRecordRoute.route}/{vehicleId}/{vehicleName}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType },
                    navArgument("vehicleName") { type = NavType.StringType }
                )
            ) { entry ->
                val vehicleId = androidx.navigation.NavBackStackEntry.getLong(entry, "vehicleId")
                AddFuelRecordScreen(
                    vehicleId = vehicleId,
                    defaultFuelType = "АИ-95",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}