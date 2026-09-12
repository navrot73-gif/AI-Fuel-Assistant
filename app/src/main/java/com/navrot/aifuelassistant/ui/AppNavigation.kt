package com.navrot.aifuelassistant.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.navrot.aifuelassistant.R
import com.navrot.aifuelassistant.features.dashboard.DashboardScreen
import com.navrot.aifuelassistant.ui.fuel.AddFuelRecordScreen
import com.navrot.aifuelassistant.ui.fuel.FuelRecordListScreen
import com.navrot.aifuelassistant.ui.map.MapScreen
import com.navrot.aifuelassistant.ui.reports.FuelReportsScreen
import com.navrot.aifuelassistant.ui.vehicles.AddVehicleScreen
import com.navrot.aifuelassistant.ui.vehicles.GarageListScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleDetailScreen
import com.navrot.aifuelassistant.ui.vehicles.VehicleViewModel
import kotlinx.coroutines.launch

data class Tab(val route: Any, val iconRes: Int, val title: String)

val TABS = listOf(
    Tab(NavRoute.Map, R.drawable.ic_tab_map, "Карта"),
    Tab(NavRoute.Dashboard, R.drawable.ic_tab_ai, "AI"),
    Tab(NavRoute.Garage, R.drawable.ic_tab_car, "Гараж")
)

val TAB_ACTIVE_COLOR = Color(0xFFE8A750)
val TAB_INACTIVE_COLOR = Color(0xFF8A949E)

/**
 * Helper function to convert a route object or string representation to its main tab page index.
 * Returns 0 for Map, 1 for AI, 2 for Garage, or null for non-tab routes.
 */
fun getPageIndexForRoute(route: Any?): Int? {
    if (route == null) return null
    return when (route) {
        is NavRoute.Map, is NavRoute.MapBuildRoute, is NavRoute.MapShowStations -> 0
        is NavRoute.Dashboard -> 1
        is NavRoute.Garage, is NavRoute.GarageList, is NavRoute.GarageDetail -> 2
        is String -> when {
            route.isMapRoute() -> 0
            route == "ai" || route == "Dashboard" -> 1
            route.isGarageRoute() -> 2
            else -> null
        }
        else -> null
    }
}

// Helper to check if route is in garage sub-navigation.
fun Any?.isGarageRoute(): Boolean {
    if (this == null) return false
    if (this is NavRoute.Garage || this is NavRoute.GarageList || this is NavRoute.GarageDetail) return true
    if (this is String) {
        return this == "garage" ||
                this == "garage_list" ||
                this.startsWith("garage/") ||
                this.startsWith("garage_detail") ||
                this.startsWith("garage_list?") ||
                this.contains("Garage")
    }
    return false
}

// Helper to check if route is a map navigation route
fun Any?.isMapRoute(): Boolean {
    if (this == null) return false
    if (this is NavRoute.Map || this is NavRoute.MapBuildRoute || this is NavRoute.MapShowStations) return true
    if (this is String) {
        return this == "map" ||
                this.startsWith("map/") ||
                this.startsWith("map?") ||
                this.contains("Map")
    }
    return false
}

@Composable
fun MainTabsScreen(
    navController: NavHostController,
    mainBackStackEntry: NavBackStackEntry
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    val targetTab by mainBackStackEntry.savedStateHandle
        .getStateFlow<Int?>("target_tab", null)
        .collectAsStateWithLifecycle()

    LaunchedEffect(targetTab) {
        targetTab?.let { page ->
            pagerState.animateScrollToPage(page)
            mainBackStackEntry.savedStateHandle.remove<Int>("target_tab")
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                TABS.forEachIndexed { index, tab ->
                    val selected = (pagerState.currentPage == index)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = {
                            Icon(
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
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (page) {
                0 -> {
                    val pendingRouteId by mainBackStackEntry.savedStateHandle
                        .getStateFlow<Int?>("build_route_station_id", null)
                        .collectAsStateWithLifecycle()
                    val pendingOpenStationId by mainBackStackEntry.savedStateHandle
                        .getStateFlow<Int?>("open_station_id", null)
                        .collectAsStateWithLifecycle()
                    val aiAnswerText by mainBackStackEntry.savedStateHandle
                        .getStateFlow<String?>("ai_answer_text", null)
                        .collectAsStateWithLifecycle()
                    MapScreen(
                        pendingRouteStationId = pendingRouteId,
                        pendingOpenStationId = pendingOpenStationId,
                        aiAnswerText = aiAnswerText,
                        onConsumePendingRoute = {
                            mainBackStackEntry.savedStateHandle.remove<Int>("build_route_station_id")
                        },
                        onConsumePendingOpenStation = {
                            mainBackStackEntry.savedStateHandle.remove<Int>("open_station_id")
                            mainBackStackEntry.savedStateHandle.remove<String>("ai_answer_text")
                        }
                    )
                }
                1 -> {
                    DashboardScreen(navController = navController)
                }
                2 -> {
                    val garageViewModel: VehicleViewModel = hiltViewModel()
                    GarageListScreen(
                        onAddClick = { navController.navigate(NavRoute.AddVehicle()) },
                        onVehicleClick = { vehicleId ->
                            val vehicle = garageViewModel.vehiclesWithStats.value.find { it.id == vehicleId }
                            val vehicleName = vehicle?.name ?: ""
                            navController.navigate(NavRoute.GarageDetail(vehicleId, vehicleName))
                        },
                        onEditClick = { vehicleId ->
                            navController.navigate(NavRoute.AddVehicle(vehicleId = vehicleId))
                        },
                        onReportsClick = {
                            navController.navigate(NavRoute.Reports)
                        },
                        viewModel = garageViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoute.MainTabs
    ) {
        composable<NavRoute.MainTabs> { backStackEntry ->
            MainTabsScreen(navController = navController, mainBackStackEntry = backStackEntry)
        }

        composable<NavRoute.Map> {
            LaunchedEffect(Unit) {
                val entry = navController.getBackStackEntry(NavRoute.MainTabs)
                entry.savedStateHandle["target_tab"] = 0
                navController.popBackStack()
            }
        }

        composable<NavRoute.Dashboard> {
            LaunchedEffect(Unit) {
                val entry = navController.getBackStackEntry(NavRoute.MainTabs)
                entry.savedStateHandle["target_tab"] = 1
                navController.popBackStack()
            }
        }

        composable<NavRoute.Garage> {
            LaunchedEffect(Unit) {
                val entry = navController.getBackStackEntry(NavRoute.MainTabs)
                entry.savedStateHandle["target_tab"] = 2
                navController.popBackStack()
            }
        }

        composable<NavRoute.GarageList> {
            LaunchedEffect(Unit) {
                val entry = navController.getBackStackEntry(NavRoute.MainTabs)
                entry.savedStateHandle["target_tab"] = 2
                navController.popBackStack()
            }
        }

        composable<NavRoute.MapBuildRoute> { entry ->
            val args = entry.toRoute<NavRoute.MapBuildRoute>()
            val aiAnswerText by entry.savedStateHandle
                .getStateFlow<String?>("ai_answer_text", null)
                .collectAsStateWithLifecycle()
            MapScreen(
                pendingRouteStationId = args.stationId,
                aiAnswerText = aiAnswerText,
                onConsumePendingRoute = {
                    entry.savedStateHandle.remove<String>("ai_answer_text")
                }
            )
        }

        composable<NavRoute.MapShowStations> { entry ->
            val aiAnswerText by entry.savedStateHandle
                .getStateFlow<String?>("ai_answer_text", null)
                .collectAsStateWithLifecycle()
            MapScreen(
                showStationList = true,
                aiAnswerText = aiAnswerText,
                onConsumePendingOpenStation = {
                    entry.savedStateHandle.remove<String>("ai_answer_text")
                }
            )
        }

        composable<NavRoute.GarageDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<NavRoute.GarageDetail>()
            val vehicleId = args.vehicleId
            val vehicleName = args.vehicleName
            VehicleDetailScreen(
                vehicleId = vehicleId,
                vehicleName = vehicleName,
                onBack = { navController.popBackStack() },
                onAddClick = { navController.navigate(NavRoute.AddFuelRecord(vehicleId, vehicleName)) },
                onEditClick = { id -> navController.navigate(NavRoute.AddVehicle(vehicleId = id)) },
                viewModel = hiltViewModel()
            )
        }

        composable<NavRoute.AddVehicle> { backStackEntry ->
            val args = backStackEntry.toRoute<NavRoute.AddVehicle>()
            AddVehicleScreen(
                vehicleId = args.vehicleId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<NavRoute.FuelRecordList> { backStackEntry ->
            val route = backStackEntry.toRoute<NavRoute.FuelRecordList>()
            FuelRecordListScreen(
                vehicleId = route.vehicleId,
                vehicleName = route.vehicleName,
                onBack = { navController.popBackStack() },
                onAddClick = {
                    navController.navigate(NavRoute.AddFuelRecord(route.vehicleId, route.vehicleName))
                }
            )
        }

        composable<NavRoute.AddFuelRecord> { entry ->
            val args = entry.toRoute<NavRoute.AddFuelRecord>()
            AddFuelRecordScreen(
                vehicleId = args.vehicleId,
                defaultFuelType = "АИ-95",
                onBack = { navController.popBackStack() }
            )
        }

        composable<NavRoute.Reports> {
            FuelReportsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
