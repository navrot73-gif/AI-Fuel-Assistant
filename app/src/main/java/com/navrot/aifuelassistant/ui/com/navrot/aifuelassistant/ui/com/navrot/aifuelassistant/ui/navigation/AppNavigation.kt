package com.navrot.aifuelassistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.navrot.aifuelassistant.data.GasStationData
import com.navrot.aifuelassistant.ui.fuel.AddFuelRecordScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen
import com.navrot.aifuelassistant.ui.fuel.GasStationDetailScreen
import com.navrot.aifuelassistant.ui.fuel.MapScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleListScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "main_map"
    ) {
        // Главный экран с картой
        composable("main_map") {
            MapScreen(
                vehicleId = 0L,
                vehicleName = "Поблизости",
                onBack = { },
                onVehiclesClick = { navController.navigate("vehicle_list") },
                onStationClick = { station ->
                    navController.navigate("station_detail/${station.id}")
                }
            )
        }

        // Список автомобилей
        composable("vehicle_list") {
            VehicleListScreen(
                onAddClick = { navController.navigate("add_vehicle") },
                onVehicleClick = { vehicleId, vehicleName ->
                    navController.navigate("fuel_records/$vehicleId/$vehicleName")
                }
            )
        }

        // Добавить автомобиль
        composable("add_vehicle") {
            AddVehicleScreen(
                onBack = { navController.popBackStack() }
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

            FuelRecordListScreen(
                vehicleId = vehicleId,
                vehicleName = vehicleName,
                onBack = { navController.popBackStack() },
                onAddClick = {
                    navController.navigate("add_fuel_record/$vehicleId")
                },
                onMapClick = {
                    navController.navigate("map/$vehicleId/$vehicleName")
                }
            )
        }

        // Карта для конкретного авто
        composable(
            route = "map/{vehicleId}/{vehicleName}",
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType },
                navArgument("vehicleName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: 0L
            val vehicleName = backStackEntry.arguments?.getString("vehicleName") ?: ""

            MapScreen(
                vehicleId = vehicleId,
                vehicleName = vehicleName,
                onBack = { navController.popBackStack() },
                onVehiclesClick = { navController.navigate("vehicle_list") },
                onStationClick = { station ->
                    navController.navigate("station_detail/${station.id}")
                }
            )
        }

        // ===== ЭКРАН ДЕТАЛЕЙ АЗС =====
        composable(
            route = "station_detail/{stationId}",
            arguments = listOf(
                navArgument("stationId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val stationId = backStackEntry.arguments?.getLong("stationId") ?: 0L
            val station = GasStationData.stations.find { it.id == stationId }

            if (station != null) {
                GasStationDetailScreen(
                    station = station,
                    onBack = { navController.popBackStack() },
                    onRouteClick = {
                        // TODO: Открыть Google Maps/Яндекс.Навигатор с маршрутом
                    }
                )
            }
        }

        // Добавить запись о заправке
        composable(
            route = "add_fuel_record/{vehicleId}",
            arguments = listOf(
                navArgument("vehicleId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: 0L
            AddFuelRecordScreen(
                vehicleId = vehicleId,
                defaultFuelType = "Бензин АИ-95",
                onBack = { navController.popBackStack() }
            )
        }
    }
}