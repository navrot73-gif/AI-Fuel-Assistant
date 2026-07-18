package com.navrot.aifuelassistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navrot.aifuelassistant.ui.fuel.AddFuelRecordScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleListScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "vehicle_list"
    ) {
        // Список автомобилей
        composable("vehicle_list") {
            VehicleListScreen(
                onAddClick = { navController.navigate("add_vehicle") },
                onVehicleClick = { vehicleId, vehicleName ->
                    navController.navigate("fuel_records/$vehicleId/$vehicleName")
                }
            )
        }

        // Добавление автомобиля
        composable("add_vehicle") {
            AddVehicleScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Список заправок для автомобиля
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
                    navController.navigate("add_fuel_record/$vehicleId")
                }
            )
        }

        // Добавление заправки
        composable(
            route = "add_fuel_record/{vehicleId}",
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: 0L
            // Получаем defaultFuelType из предыдущего экрана (упрощённо)
            AddFuelRecordScreen(
                vehicleId = vehicleId,
                defaultFuelType = "Бензин АИ-95", // Можно улучшить позже
                onBack = { navController.popBackStack() }
            )
        }
    }
}