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
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

@Composable
fun FuelTypeFilter(
    fuelTypes: List<String>,
    selectedFuelTypes: Set<String>,
    onFuelTypeToggled: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fuelTypes.forEach { type ->
            val isSelected = selectedFuelTypes.contains(type)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) FueldeckColors.Amber else FueldeckColors.Surface,
                border = if (isSelected) null else BorderStroke(1.dp, FueldeckColors.Line),
                modifier = Modifier.clickable { onFuelTypeToggled(type) }
            ) {
                Text(
                    text = type,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isSelected) Color(0xFF1A1205) else FueldeckColors.InkDim,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            }
        }
    }
}