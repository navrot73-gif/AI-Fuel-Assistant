package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.ui.map.MapViewModel
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

/** Цвета вариантов маршрута: Синий (Быстрый), Зелёный (Без пробок), Фиолетовый (Альтернативный) */
val ROUTE_OPTION_COLORS = listOf(
    Color(0xFF2196F3), // Синий
    Color(0xFF4CAF50), // Зелёный
    Color(0xFF9C27B0)  // Фиолетовый
)

@Composable
fun RouteOptionsPanel(
    routeOptions: List<MapViewModel.RouteOptionUiState>,
    activeRouteIndex: Int,
    onSelectOption: (Int) -> Unit,
    onGoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (routeOptions.isEmpty()) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = FueldeckColors.Surface,
        border = BorderStroke(1.dp, FueldeckColors.Line),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                routeOptions.forEachIndexed { index, option ->
                    val isActive = index == activeRouteIndex
                    val optionColor = ROUTE_OPTION_COLORS.getOrElse(index) { Color(0xFF2196F3) }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectOption(index) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) optionColor.copy(alpha = 0.25f) else FueldeckColors.Surface2,
                        border = BorderStroke(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) optionColor else FueldeckColors.Line
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = option.title,
                                color = if (isActive) optionColor else FueldeckColors.InkDim,
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = option.durationText,
                                color = FueldeckColors.Ink,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = option.distanceText,
                                color = FueldeckColors.InkDim,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onGoClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FueldeckColors.Amber,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Поехали",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
