package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.map.StationDetailCard

/**
 * Stateless оверлей деталей выбранной АЗС (анимация появления/скрытия).
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BoxScope.StationDetailOverlay(
    station: GasStation?,
    selectedFuelTypes: Set<String>,
    isRouting: Boolean,
    routeText: String?,
    onClose: () -> Unit,
    onBuildRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onReportPrice: (stationId: Int, fuelType: String, price: Double) -> Unit
) {
    val enterAnimation = remember {
        fadeIn() + slideInVertically(initialOffsetY = { it })
    }
    val exitAnimation = remember {
        fadeOut() + slideOutVertically(targetOffsetY = { it })
    }
    
    AnimatedVisibility(
        visible = station != null,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
        enter = enterAnimation,
        exit = exitAnimation
    ) {
        station?.let { st ->
            StationDetailCard(
                station = st,
                selectedFuelTypes = selectedFuelTypes,
                onClose = onClose,
                onBuildRoute = onBuildRoute,
                isRouting = isRouting,
                routeText = routeText,
                onClearRoute = onClearRoute,
                onReportPrice = onReportPrice
            )
        }
    }
}