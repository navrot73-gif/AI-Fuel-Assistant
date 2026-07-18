package com.navrot.aifuelassistant.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navrot.aifuelassistant.features.dashboard.DashboardScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        // Главный экран / Дашборд
        composable(route = "dashboard") {
            DashboardScreen()
        }

        // Добавить автомобиль
        composable(route = "add_vehicle") {
            AddVehicleScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Список записей о заправке
        composable(
            route = "fuel_records/{vehicleId}/{vehicleName}",
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("vehicleName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: 0L
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: ""

            // ИСПРАВЛЕНО: приведены к правильным именам параметры onBack и onAddClick
            FuelRecordListScreen(
                vehicleId = vehicleId,
                vehicleName = vehicleName,
                onBack = { navController.popBackStack() },
                onAddClick = { /* TODO: navController.navigate("add_fuel_record") */ }
            )
        }
    }
}