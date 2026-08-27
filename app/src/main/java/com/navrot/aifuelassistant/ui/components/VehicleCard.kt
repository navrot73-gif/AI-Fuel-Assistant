package com.navrot.aifuelassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.theme.FueldeckShapes

@Composable
fun VehicleCard(
    state: VehicleCardUiState,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onEditClick: (() -> Unit)? = null
) {
    val cardBorderColor = if (isActive) FueldeckColors.Mint else FueldeckColors.Line
    val cardBorderWidth = 1.dp
    
    val carIconBgColor = if (isActive) FueldeckColors.Mint else FueldeckColors.Amber
    val carIconColor = if (isActive) Color(0xFF17222B) else Color(0xFF1A1205)
    
    Surface(
        color = FueldeckColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(cardBorderWidth, cardBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            
            // Top row: car icon + name + fuel badge + active badge/radio + edit icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!state.photoUrl.isNullOrBlank()) {
                        NetworkImage(
                            url = state.photoUrl,
                            contentDescription = "Фото автомобиля",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(carIconBgColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DirectionsCar,
                                contentDescription = "Автомобиль",
                                tint = carIconColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = FueldeckColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (state.modelLine != "—") {
                                Surface(
                                    shape = FueldeckShapes.Pill,
                                    color = FueldeckColors.Surface2,
                                    border = BorderStroke(1.dp, FueldeckColors.Line),
                                ) {
                                    Text(
                                        text = state.modelLine.replace(" · ", " "),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = FueldeckColors.InkDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        
                        if (isActive) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = FueldeckShapes.Pill,
                                    color = FueldeckColors.Mint.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, FueldeckColors.Mint.copy(alpha = 0.3f)),
                                ) {
                                    Text(
                                        text = "АКТИВНО",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FueldeckColors.Mint,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier.size(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(Color.Transparent, RoundedCornerShape(7.dp))
                                            .border(1.5.dp, FueldeckColors.Mint, RoundedCornerShape(7.dp)),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(FueldeckColors.Mint, RoundedCornerShape(3.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = FueldeckShapes.Pill,
                        color = FueldeckColors.AmberSoft,
                        border = BorderStroke(1.dp, FueldeckColors.Amber.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            text = state.fuelGrade,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = FueldeckColors.Amber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    if (onEditClick != null) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать",
                                tint = FueldeckColors.Amber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            // Row: speedometer icon consumption (mint) · droplet icon fuel grade (amber)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Consumption with speedometer icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Расход",
                        tint = FueldeckColors.Mint,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (state.consumptionText != "—") "${state.consumptionText} л/100 км" else "— л/100 км",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = FueldeckColors.Mint,
                    )
                }
                
                // Divider dot
                Text(
                    text = "·",
                    fontSize = 14.sp,
                    color = FueldeckColors.InkFaint,
                )
                
                // Fuel type with droplet icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalGasStation,
                        contentDescription = "Тип топлива",
                        tint = FueldeckColors.Amber,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = state.fuelGrade,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = FueldeckColors.Amber,
                    )
                }
            }
            
            // Dim 12sp row: year · mileage · fill count (only if real data exists)
            val hasYear = state.modelLine.contains("·")
            val yearStr = if (hasYear) {
                state.modelLine.split("·").last().trim()
            } else ""
            
            val hasMileage = state.mileageText != "—" && state.mileageText.toDoubleOrNull() != null
            val mileageKm = if (hasMileage) {
                val thousands = state.mileageText.toDoubleOrNull() ?: 0.0
                "${(thousands * 1000).toInt()} км"
            } else ""
            
            val hasFillCount = state.fillCount > 0
            val fillCountStr = if (hasFillCount) "${state.fillCount} заправк${when (state.fillCount % 10) { 1 -> "а"; 2, 3, 4 -> "и"; else -> "ок" }}" else ""
            
            val detailsParts = mutableListOf<String>()
            if (yearStr.isNotBlank()) detailsParts.add(yearStr)
            if (mileageKm.isNotBlank()) detailsParts.add(mileageKm)
            if (fillCountStr.isNotBlank()) detailsParts.add(fillCountStr)
            
            if (detailsParts.isNotEmpty()) {
                Text(
                    text = detailsParts.joinToString(" · "),
                    fontSize = 12.sp,
                    color = FueldeckColors.InkDim,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

data class VehicleCardUiState(
    val id: Long = 0L,
    val name: String,
    val modelLine: String,
    val fuelGrade: String,
    val tankLiters: Int,
    val fillPercent: Int,
    val rangeKm: Int,
    val mileageText: String,
    val consumptionText: String,
    val fillCount: Int,
    val bars: List<Float>,
    val toKmLeft: Int,
    val toPercent: Int,
    val lastFillDate: String,
    val lastFillLiters: String,
    val lastFillBrand: String,
    val lastFillPrice: String,
    val photoUrl: String? = null
)