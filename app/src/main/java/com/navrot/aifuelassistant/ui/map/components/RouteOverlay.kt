package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.map.AiRecommendationCard
import com.navrot.aifuelassistant.ui.map.MapViewModel
import com.navrot.aifuelassistant.ui.map.openMapsRoute
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

/**
 * Stateless оверлей маршрута: расширенная кнопка "Маршрут"/"Открыть в навигаторе",
 * кнопка сброса маршрута, pill "АЗС рядом" и AI-рекомендация.
 */
@Composable
fun RouteSelectorPanel(
    options: List<MapViewModel.RouteOption>,
    activeIndex: Int,
    onSelectOption: (Int) -> Unit,
    onConfirmRoute: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { idx, option ->
                    val isActive = idx == activeIndex
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectOption(idx) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) Color(0xFF2563EB) else Color(0xFF0F172A),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) Color(0xFF3B82F6) else Color(0xFF1E293B)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = option.title,
                                color = if (isActive) Color.White.copy(alpha = 0.9f) else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = option.durationText,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = option.distanceText,
                                color = if (isActive) Color.White.copy(alpha = 0.8f) else Color(0xFF64748B),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            androidx.compose.material3.Button(
                onClick = onConfirmRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = FueldeckColors.Amber,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    "Поехали",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun BoxScope.RouteOverlay(
    selectedStation: GasStation?,
    route: MapViewModel.RouteOption?,
    routeOptions: List<MapViewModel.RouteOption> = emptyList(),
    activeRouteIndex: Int = 0,
    isRoutePanelConfirmed: Boolean = false,
    routeStation: GasStation?,
    aiRecommendation: Triple<GasStation, com.navrot.aifuelassistant.data.model.FuelPrice, Double>?,
    showStationList: Boolean,
    onBuildRoute: () -> Unit,
    onSelectRouteOption: (Int) -> Unit = {},
    onConfirmRoute: () -> Unit = {},
    onClearRoute: () -> Unit,
    onExpandList: () -> Unit,
    onSelectAiPick: () -> Unit
) {
    val context = LocalContext.current

    if (route != null && selectedStation == null && !showStationList) {
        val routeText = if (route.isDirect || route.isStraightLine) {
            "${route.distanceText} по прямой"
        } else {
            "${route.distanceText} · ${route.durationText}"
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = FueldeckColors.Surface,
            border = BorderStroke(1.dp, FueldeckColors.Line),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🧭", fontSize = 14.sp)
                Text(
                    routeText,
                    color = FueldeckColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
    ) {
        if (!showStationList) {
            val showPanel = routeOptions.isNotEmpty() && !isRoutePanelConfirmed && selectedStation == null
            if (showPanel) {
                RouteSelectorPanel(
                    options = routeOptions,
                    activeIndex = activeRouteIndex,
                    onSelectOption = onSelectRouteOption,
                    onConfirmRoute = onConfirmRoute
                )
            } else {
                val arrowPulse = rememberInfiniteTransition(label = "pl").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pl"
                )
                Surface(
                    modifier = Modifier.padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = FueldeckColors.Surface,
                    border = BorderStroke(1.dp, FueldeckColors.Line),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onExpandList() }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "▲",
                            color = FueldeckColors.Amber.copy(alpha = 0.5f + 0.5f * arrowPulse.value),
                            fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(
                            "АЗС рядом",
                            color = FueldeckColors.Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (aiRecommendation != null && route == null) {
                    AiRecommendationCard(
                        recommendation = aiRecommendation,
                        onExpandList = onSelectAiPick
                    )
                }
            }
        }
    }

    // Расширенная кнопка "Маршрут" (или "Открыть в навигаторе", если маршрут построен)
    val yellowBottom = if (showStationList) 380.dp else 16.dp
    if (selectedStation != null) {
        ExtendedFloatingActionButton(
            onClick = onBuildRoute,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = yellowBottom),
            containerColor = FueldeckColors.Amber,
            contentColor = Color.Black,
            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            text = { Text("Маршрут", fontWeight = FontWeight.Bold) }
        )
    } else if (route != null && routeStation != null) {
        ExtendedFloatingActionButton(
            onClick = {
                routeStation.let { st ->
                    openMapsRoute(context, st.latitude, st.longitude, st.brand)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = yellowBottom),
            containerColor = FueldeckColors.Amber,
            contentColor = Color.Black,
            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            text = { Text("Маршрут", fontWeight = FontWeight.Bold) }
        )
    }

    // Кнопка сброса маршрута
    if (route != null) {
        SmallFloatingActionButton(
            onClick = onClearRoute,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = when {
                        showStationList -> 524.dp
                        selectedStation != null || (route != null && routeStation != null) -> 160.dp
                        else -> 88.dp
                    }
                ),
            containerColor = FueldeckColors.Surface,
            contentColor = FueldeckColors.Coral
        ) {
            Icon(Icons.Default.Close, contentDescription = "Сбросить маршрут")
        }
    }
}