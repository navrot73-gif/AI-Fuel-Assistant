package com.navrot.aifuelassistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
        composable("vehicle_list") {
            VehicleListScreen(
                onAddClick = { navController.navigate("add_vehicle") }
            )
        }
        composable("add_vehicle") {
            AddVehicleScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}