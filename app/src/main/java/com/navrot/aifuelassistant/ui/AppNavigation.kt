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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navrot.aifuelassistant.features.dashboard.DashboardScreen
import com.navrot.aifuelassistant.ui.fuel.AddFuelRecordScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen
import com.navrot.aifuelassistant.ui.fuel.GasStationDetailScreen
import com.navrot.aifuelassistant.ui.map.MapScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleListScreen

private data class Tab(val route: String, val glyph: String, val title: String)

private val TABS = listOf(
    Tab("map", "🗺️", "Карта"),
    Tab("ai", "🤖", "AI"),
    Tab("garage", "🚗", "Гараж")
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
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
            startDestination = "map",
            modifier = Modifier.padding(padding)
        ) {
            composable("map") {
                MapScreen(
                    onStationClick = { station ->
                        navController.navigate("station_detail/${station.id}")
                    }
                )
            }

            composable("ai") { DashboardScreen() }

            composable("garage") {
                VehicleListScreen(
                    onAddClick = { navController.navigate("add_vehicle") },
                    onVehicleClick = { vehicleId, vehicleName ->
                        val encoded = Uri.encode(vehicleName)
                        navController.navigate("fuel_records/$vehicleId/$encoded")
                    }
                )
            }

            composable("add_vehicle") {
                AddVehicleScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(
                route = "station_detail/{stationId}",
                arguments = listOf(
                    navArgument("stationId") { type = NavType.IntType }
                )
            ) { entry ->
                val stationId = entry.arguments?.getInt("stationId") ?: return@composable
                // Экран будет искать станцию по id через ViewModel
                // Передаём пока заглушку — реальная реализация потребует
                // передачи GasStation через SavedStateHandle или NavType
                Text("Детали АЗС #$stationId — в разработке")
            }

            composable(
                route = "fuel_records/{vehicleId}/{vehicleName}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType },
                    navArgument("vehicleName") { type = NavType.StringType }
                )
            ) { entry ->
                val vehicleId = entry.arguments?.getLong("vehicleId") ?: 0L
                val vehicleName = Uri.decode(entry.arguments?.getString("vehicleName") ?: "")
                FuelRecordListScreen(
                    vehicleId = vehicleId,
                    vehicleName = vehicleName,
                    onBack = { navController.popBackStack() },
                    onAddClick = {
                        val encoded = Uri.encode(vehicleName)
                        navController.navigate("add_fuel_record/$vehicleId/$encoded")
                    }
                )
            }

            composable(
                route = "add_fuel_record/{vehicleId}/{vehicleName}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.LongType },
                    navArgument("vehicleName") { type = NavType.StringType }
                )
            ) { entry ->
                val vehicleId = entry.arguments?.getLong("vehicleId") ?: 0L
                AddFuelRecordScreen(
                    vehicleId = vehicleId,
                    defaultFuelType = "АИ-95",
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}