package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.ui.fuel.MapViewModel
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun SortBar(
    currentSort: MapViewModel.SortMode,
    onSortChange: (MapViewModel.SortMode) -> Unit
) {
    val sorts = listOf(
        MapViewModel.SortMode.BEST to "Лучшее",
        MapViewModel.SortMode.PRICE_ASC to "Дешевле",
        MapViewModel.SortMode.NEARBY to "Ближе",
        MapViewModel.SortMode.QUEUE to "Без очереди"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sorts.forEach { (mode, label) ->
            val isSelected = currentSort == mode
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) FueldeckColors.Amber else FueldeckColors.Surface,
                border = if (isSelected) null else BorderStroke(1.dp, FueldeckColors.Line),
                modifier = Modifier.clickable { onSortChange(mode) }
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (isSelected) Color(0xFF1A1205) else FueldeckColors.InkDim,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            }
        }
    }
}