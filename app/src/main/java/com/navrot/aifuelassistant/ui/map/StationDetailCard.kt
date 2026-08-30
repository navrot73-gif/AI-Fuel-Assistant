package com.navrot.aifuelassistant.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.isMedianFromNetwork
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.domain.reliability.PriceSource
import com.navrot.aifuelassistant.ui.components.NetworkImage
import com.navrot.aifuelassistant.ui.map.components.StationActionsBlock
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StationDetailCard(
    station: GasStation,
    selectedFuelTypes: Set<String>,
    onClose: () -> Unit,
    onBuildRoute: () -> Unit = {},
    isRouting: Boolean = false,
    routeText: String? = null,
    onClearRoute: () -> Unit = {},
    onReportPrice: (stationId: Int, fuelType: String, price: Double) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val primaryFuelType = selectedFuelTypes.firstOrNull() ?: station.fuelTypes.firstOrNull()?.type ?: "АИ-95"
    val availabilityStatus = PriceReliabilityCalculator.calculateFuelAvailability(station, primaryFuelType)

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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // ===== Шапка: бренд, адрес, крестик =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.brand,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = station.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBadgeBg
                    ) {
                        Text(
                            text = statusBadgeText,
                            fontSize = 11.sp,
                            color = statusBadgeTextColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }

            // ===== Photos Display =====
            Column {
                // Monument Photo
                if (station.monumentPhotoUrl != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "📷 Стелла",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (station.monumentPhotoUrl.isNotBlank()) {
                        NetworkImage(
                            url = station.monumentPhotoUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Monument Placeholder",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Entrance Photo
                if (station.entrancePhotoUrl != null) {
                    Text(
                        "🚪 Вход",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (station.entrancePhotoUrl.isNotBlank()) {
                        NetworkImage(
                            url = station.entrancePhotoUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Entrance Placeholder",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ===== Цены в одну строку =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                station.fuelTypes.take(3).forEach { fuel ->
                    val isSelected = selectedFuelTypes.contains(fuel.type)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = fuel.type,
                            fontSize = 11.sp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${if (fuel.isMedianFromNetwork) "~" else ""}${Format.price2(fuel.price)} ₽",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = FueldeckColors.Mint
                        )
                    }
                }
            }

            val liveFuels = station.fuelTypes.take(3).filter { it.isMedianFromNetwork }
            if (liveFuels.isNotEmpty()) {
                val maxCount = liveFuels.maxOfOrNull { it.sourceCount } ?: 0
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (maxCount > 0) {
                        "🔄 обновлено только что · медиана из $maxCount источников"
                    } else {
                        "🔄 обновлено только что · медиана"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== Очередь и индикатор надёжности =====
            val priceReliability = remember(station, primaryFuelType) {
                PriceReliabilityCalculator.calculate(station, primaryFuelType)
            }

            val badgeBgColor = when {
                priceReliability.percent >= 80 -> FueldeckColors.MintSoft
                priceReliability.percent >= 50 -> FueldeckColors.AmberSoft
                else -> FueldeckColors.CoralSoft
            }
            val badgeTextColor = when {
                priceReliability.percent >= 80 -> FueldeckColors.Mint
                priceReliability.percent >= 50 -> FueldeckColors.Amber
                else -> FueldeckColors.Coral
            }

            val sourceText = when (priceReliability.source) {
                PriceSource.USER_CONFIRMED -> "Проверено пользователем"
                PriceSource.NETWORK -> "Сеть Benzonavt"
                PriceSource.CACHE -> "Локальный кэш"
                PriceSource.ASSETS -> "Базовые данные (офлайн)"
            }

            val ageText = when {
                priceReliability.source == PriceSource.ASSETS -> ""
                priceReliability.ageDays == 0 -> " · сегодня"
                priceReliability.ageDays == 1 -> " · вчера"
                else -> " · ${priceReliability.ageDays} дн. назад"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (station.queueTime > 0) {
                    Text(
                        "Очередь: ${station.queueTime} мин",
                        fontSize = 12.sp,
                        color = FueldeckColors.Danger,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        "Без очереди",
                        fontSize = 12.sp,
                        color = FueldeckColors.Mint,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBgColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Надёжность: ${priceReliability.percent}%",
                        fontSize = 12.sp,
                        color = badgeTextColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$sourceText$ageText",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ===== StationActionsBlock (💰 Сообщить цену + 📷 Фото стеллы) =====
            StationActionsBlock(
                station = station,
                onReportPrice = onReportPrice
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ===== Кнопка: маршрут =====
            Button(
                onClick = onBuildRoute,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isRouting) "Уточняем..." else "Маршрут")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== Навигатор =====
            OutlinedButton(
                onClick = { openMapsRoute(context, station.latitude, station.longitude, station.brand) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Открыть в навигаторе")
            }

            // ===== Текст маршрута =====
            if (routeText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🧭 $routeText",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    TextButton(onClick = onClearRoute) { Text("Сбросить") }
                }
            }
        }
    }
}
