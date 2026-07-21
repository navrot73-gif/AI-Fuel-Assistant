package com.navrot.aifuelassistant.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navrot.aifuelassistant.features.dashboard.DashboardScreen
import com.navrot.aifuelassistant.ui.fuel.AddFuelRecordScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleListScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable(route = "dashboard") {
            DashboardScreen()
        }

        composable(route = "vehicles") {
            VehicleListScreen(
                onAddClick = { navController.navigate("add_vehicle") },
                onVehicleClick = { vehicleId, vehicleName ->
                    navController.navigate("fuel_records/$vehicleId/$vehicleName")
                }
            )
        }

        composable(route = "add_vehicle") {
            AddVehicleScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "fuel_records/{vehicleId}/{vehicleName}",
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("vehicleName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: 0L
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: ""

            FuelRecordListScreen(
                vehicleId = vehicleId,
                vehicleName = vehicleName,
                onBack = { navController.popBackStack() },
                onAddClick = {
                    navController.navigate("add_fuel_record/$vehicleId/$vehicleName")
                }
            )
        }

        composable(
            route = "add_fuel_record/{vehicleId}/{vehicleName}",
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("vehicleName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: 0L

            AddFuelRecordScreen(
                vehicleId = vehicleId,
                defaultFuelType = "Бензин",
                onBack = { navController.popBackStack() }
            )
        }
    }
}