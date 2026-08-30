package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.isMedianFromNetwork
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.util.Format
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationListItem(
    station: GasStation,
    selectedFuelTypes: Set<String>,
    userLocation: GeoPoint?,
    onClick: () -> Unit
) {
    val selectedFuel = selectedFuelTypes.firstOrNull() ?: "АИ-95"
    val fuelObj = station.fuelTypes.find { it.type == selectedFuel } ?: station.fuelTypes.firstOrNull()
    val availabilityStatus = PriceReliabilityCalculator.calculateFuelAvailability(station, selectedFuel)

    val distance = userLocation?.let {
        GeoUtils.calculateDistance(it.latitude, it.longitude, station.latitude, station.longitude)
    }

    val brandColors = listOf(
        FueldeckColors.Mint, FueldeckColors.Amber, FueldeckColors.Coral,
        Color(0xFF2F7FD1), Color(0xFF7A6BD1), Color(0xFF2FAA55)
    )
    val brandColor = brandColors[Math.floorMod(station.brand.hashCode(), brandColors.size)]

    val statusBadgeText = when (availabilityStatus) {
        FuelAvailabilityStatus.AVAILABLE -> "🟢 Есть топливо"
        FuelAvailabilityStatus.NO_FUEL -> "🔴 Нет топлива"
        FuelAvailabilityStatus.UNKNOWN -> "⚪ Нет данных"
    }

    val statusBadgeBg = when (availabilityStatus) {
        FuelAvailabilityStatus.AVAILABLE -> FueldeckColors.MintSoft
        FuelAvailabilityStatus.NO_FUEL -> FueldeckColors.CoralSoft
        FuelAvailabilityStatus.UNKNOWN -> Color(0x0AFFFFFF)
    }

    val statusBadgeTextColor = when (availabilityStatus) {
        FuelAvailabilityStatus.AVAILABLE -> FueldeckColors.Mint
        FuelAvailabilityStatus.NO_FUEL -> FueldeckColors.Coral
        FuelAvailabilityStatus.UNKNOWN -> FueldeckColors.InkFaint
    }

    val timestamp = fuelObj?.updatedAt?.takeIf { it > 0L } ?: station.updatedAt
    val updatedText = if (timestamp > 0L) {
        val diffMs = maxOf(0L, System.currentTimeMillis() - timestamp)
        val hours = (diffMs / (1000 * 60 * 60)).toInt()
        val days = hours / 24
        when {
            hours < 1 -> "обновлено только что"
            hours < 24 -> "обновлено ${hours}ч назад"
            else -> "обновлено ${days}дн назад"
        }
    } else {
        "обновлено давно"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FueldeckColors.Surface),
        border = BorderStroke(1.dp, FueldeckColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(brandColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = station.brand.first().toString().uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.brand,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = FueldeckColors.Ink
                )
                Text(
                    text = station.address,
                    fontSize = 12.sp,
                    color = FueldeckColors.InkDim,
                    maxLines = 1
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBadgeBg,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "$statusBadgeText • $updatedText",
                        fontSize = 10.sp,
                        color = statusBadgeTextColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                fuelObj?.let { fuel ->
                    val tilde = if (fuel.isMedianFromNetwork) "~" else ""
                    Text(
                        text = "$tilde${Format.price(fuel.price)} ₽",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (availabilityStatus == FuelAvailabilityStatus.NO_FUEL) FueldeckColors.Coral else FueldeckColors.Mint
                    )
                } ?: Text(
                    text = "Нет данных",
                    fontSize = 12.sp,
                    color = FueldeckColors.InkFaint
                )

                distance?.let { dist ->
                    Text(
                        text = "${Format.km(dist)} км",
                        fontSize = 11.sp,
                        color = FueldeckColors.InkDim
                    )
                }
            }
        }
    }
}
