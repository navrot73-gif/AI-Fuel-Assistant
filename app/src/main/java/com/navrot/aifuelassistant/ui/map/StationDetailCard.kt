package com.navrot.aifuelassistant.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.data.model.GasStation

@Composable
fun StationDetailCard(
    station: GasStation,
    selectedFuelTypes: Set<String>,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.brand,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = station.address,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Text("✕", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            station.fuelTypes.forEach { fuel ->
                val isSelected = selectedFuelTypes.contains(fuel.type)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (fuel.available) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = fuel.type,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (fuel.available) "${String.format("%.2f", fuel.price)} ₽" else "Нет",
                        fontWeight = FontWeight.SemiBold,
                        color = if (fuel.available) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val queueColor = when {
                    station.queueTime <= 5 -> Color(0xFF4CAF50)
                    station.queueTime <= 15 -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = queueColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Очередь: ${station.queueTime} мин",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = queueColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Надёжность: ${station.reliability}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { openMapsRoute(context, station.latitude, station.longitude, station.brand) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Построить маршрут")
            }
        }
    }
}