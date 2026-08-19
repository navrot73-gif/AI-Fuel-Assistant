package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.isMedianFromNetwork
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.util.Format
import org.osmdroid.util.GeoPoint

@Composable
fun StationListItem(
    station: GasStation,
    selectedFuelTypes: Set<String>,
    userLocation: GeoPoint?,
    onClick: () -> Unit
) {
    val primaryFuel = station.fuelTypes.find { selectedFuelTypes.contains(it.type) && it.available }
    val distance = userLocation?.let {
        GeoUtils.calculateDistance(it.latitude, it.longitude, station.latitude, station.longitude)
    }

    val brandColors = listOf(
        FueldeckColors.Mint, FueldeckColors.Amber, FueldeckColors.Coral,
        Color(0xFF2F7FD1), Color(0xFF7A6BD1), Color(0xFF2FAA55)
    )
    val brandColor = brandColors[Math.floorMod(station.brand.hashCode(), brandColors.size)]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FueldeckColors.Surface),
        border = BorderStroke(1.dp, FueldeckColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    station.fuelTypes.filter { selectedFuelTypes.contains(it.type) }.forEach { fuel ->
                        val hot = fuel.available
                        val isPrimary = primaryFuel != null && fuel.type == primaryFuel.type
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (hot) FueldeckColors.MintSoft else Color(0x0AFFFFFF),
                            border = BorderStroke(
                                1.dp,
                                if (hot) FueldeckColors.Mint.copy(alpha = 0.35f) else FueldeckColors.Line
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (hot) {
                                        val tilde = if (fuel.isMedianFromNetwork) "~" else ""
                                        "${fuel.type} $tilde${Format.price(fuel.price)}"
                                    } else "${fuel.type} нет",
                                    fontSize = 10.sp,
                                    color = if (hot) FueldeckColors.Mint else FueldeckColors.InkFaint,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                // Show queue + reliability icons only for selected fuel type
                                if (isPrimary && hot) {
                                    if (station.queueTime > 0) {
                                        Text(
                                            text = " 👥${station.queueTime}мин",
                                            fontSize = 10.sp,
                                            color = FueldeckColors.Danger,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(start = 2.dp, top = 2.dp, end = 0.dp, bottom = 2.dp)
                                        )
                                    }
                                    if (station.reliability > 0) {
                                        Text(
                                            text = " ⭐${station.reliability}",
                                            fontSize = 10.sp,
                                            color = FueldeckColors.Amber,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(start = 2.dp, top = 2.dp, end = 0.dp, bottom = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                primaryFuel?.let { fuel ->
                    val tilde = if (fuel.isMedianFromNetwork) "~" else ""
                    Text(
                        text = "$tilde${Format.price(fuel.price)} ₽",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = FueldeckColors.Mint,
                        fontFamily = FontFamily.Monospace
                    )
                    if (fuel.isMedianFromNetwork && fuel.sourceCount > 0) {
                        Text(
                            text = "🔄 из ${fuel.sourceCount} источников",
                            fontSize = 9.sp,
                            color = FueldeckColors.Mint
                        )
                    }
                } ?: Text(
                    text = "Нет топлива",
                    fontSize = 12.sp,
                    color = FueldeckColors.Coral
                )

                distance?.let { dist ->
                    Text(
                        text = "${Format.km(dist)} км",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = FueldeckColors.InkDim
                    )
                }
            }
        }
    }
}