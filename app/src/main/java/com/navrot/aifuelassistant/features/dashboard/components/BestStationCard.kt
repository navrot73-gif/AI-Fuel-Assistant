package com.navrot.aifuelassistant.features.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendationReason
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.domain.smart.SmartRecommendationReason
import com.navrot.aifuelassistant.domain.smart.SmartStationRecommendation
import com.navrot.aifuelassistant.features.dashboard.BestStationUiState
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.theme.FueldeckShapes
import java.util.Locale

@Composable
fun BestStationCard(
    uiState: BestStationUiState,
    selectedFuelType: String,
    userLat: Double? = null,
    userLon: Double? = null,
    onRouteClick: (Int) -> Unit,
    onDetailClick: ((Int) -> Unit)? = null,
    onRefreshClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    currentTimeMs: Long = System.currentTimeMillis()
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2531)
        ),
        shape = FueldeckShapes.Lg,
        border = androidx.compose.foundation.BorderStroke(1.dp, FueldeckColors.Line2),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                uiState.isLoading -> {
                    BestStationLoadingSkeleton()
                }

                uiState.error != null -> {
                    BestStationErrorCard(
                        errorMessage = uiState.error,
                        onRetry = onRefreshClick
                    )
                }

                uiState.smartRecommendation == null && uiState.recommendation == null -> {
                    BestStationEmptyCard(
                        onRefresh = onRefreshClick
                    )
                }

                else -> {
                    if (uiState.smartRecommendation != null) {
                        SmartStationContent(
                            smartRec = uiState.smartRecommendation,
                            uiState = uiState,
                            selectedFuelType = selectedFuelType,
                            userLat = userLat,
                            userLon = userLon,
                            onRouteClick = onRouteClick,
                            onDetailClick = onDetailClick,
                            currentTimeMs = currentTimeMs
                        )

                        if (uiState.smartAlternatives.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            SmartStationAlternativesList(
                                alternatives = uiState.smartAlternatives,
                                selectedFuelType = selectedFuelType,
                                userLat = userLat,
                                userLon = userLon,
                                onStationClick = { stationId ->
                                    if (onDetailClick != null) {
                                        onDetailClick(stationId)
                                    } else {
                                        onRouteClick(stationId)
                                    }
                                }
                            )
                        }
                    } else if (uiState.recommendation != null) {
                        val rec = uiState.recommendation
                        BestStationContent(
                            recommendation = rec,
                            uiState = uiState,
                            selectedFuelType = selectedFuelType,
                            userLat = userLat,
                            userLon = userLon,
                            onRouteClick = onRouteClick,
                            onDetailClick = onDetailClick,
                            currentTimeMs = currentTimeMs
                        )

                        if (uiState.alternatives.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            BestStationAlternativesList(
                                alternatives = uiState.alternatives,
                                selectedFuelType = selectedFuelType,
                                userLat = userLat,
                                userLon = userLon,
                                onStationClick = { stationId ->
                                    if (onDetailClick != null) {
                                        onDetailClick(stationId)
                                    } else {
                                        onRouteClick(stationId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartStationContent(
    smartRec: SmartStationRecommendation,
    uiState: BestStationUiState,
    selectedFuelType: String,
    userLat: Double?,
    userLon: Double?,
    onRouteClick: (Int) -> Unit,
    onDetailClick: ((Int) -> Unit)?,
    currentTimeMs: Long
) {
    val station = smartRec.station

    // 1. HEADER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ЛУЧШАЯ АЗС СЕЙЧАС",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3ECDB0),
            letterSpacing = 1.2.sp
        )
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verified recommendation",
            tint = Color(0xFF3ECDB0),
            modifier = Modifier.size(16.dp)
        )
    }

    // 2. STATION NAME & ADDRESS
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = smartRec.stationName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF5F7FA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (smartRec.address.isNotBlank()) {
            Text(
                text = smartRec.address,
                fontSize = 13.sp,
                color = Color(0xFF8A97A5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // 3. FUEL & PRICE
    val priceText = formatPrice(smartRec.price ?: 0.0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0x29F5A94E),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = smartRec.fuelType,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5A94E),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Text(
            text = priceText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF5F7FA)
        )
    }

    // 4. DISTANCE & QUEUE
    val distanceKm = smartRec.distanceKm ?: if (userLat != null && userLon != null && userLat != 0.0 && userLon != 0.0) {
        GeoUtils.calculateDistance(userLat, userLon, station.latitude, station.longitude)
    } else null

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        distanceKm?.let { dist ->
            val travelTime = smartRec.estimatedTravelMinutes?.let { " ~${it} мин" } ?: ""
            Text(
                text = "${formatDistance(dist)}$travelTime",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF5F7FA)
            )
        }

        if (smartRec.queueTimeMinutes != null && smartRec.queueTimeMinutes > 0) {
            Text(
                text = "Очередь: ~${smartRec.queueTimeMinutes} мин",
                fontSize = 13.sp,
                color = Color(0xFFF5A94E)
            )
        } else {
            Text(
                text = "Очередь: низкая",
                fontSize = 13.sp,
                color = Color(0xFF8A97A5)
            )
        }
    }

    // 5. TRIP COST ESTIMATE
    if (smartRec.estimatedTripCost != null && smartRec.estimatedTripCost > 0.0) {
        Text(
            text = "Поездка ≈ ${String.format(Locale.getDefault(), "%.0f ₽", smartRec.estimatedTripCost)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF5A94E)
        )
    } else {
        uiState.tripCostPrediction?.let { trip ->
            if (trip.predictedCost != null) {
                Text(
                    text = "Поездка ≈ ${String.format(Locale.getDefault(), "%.0f ₽", trip.predictedCost)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF5A94E)
                )
            } else {
                Text(
                    text = "Стоимость поездки не рассчитана",
                    fontSize = 12.sp,
                    color = Color(0xFF8A97A5)
                )
            }
        } ?: Text(
            text = "Стоимость поездки не рассчитана",
            fontSize = 12.sp,
            color = Color(0xFF8A97A5)
        )
    }

    // 6. AVAILABILITY & PERSONAL BADGE
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val availText = when (smartRec.availability) {
            FuelAvailabilityStatus.AVAILABLE -> "✓ Топливо есть"
            FuelAvailabilityStatus.UNKNOWN -> "Наличие не подтверждено"
            FuelAvailabilityStatus.UNAVAILABLE, FuelAvailabilityStatus.NO_FUEL -> "Нет топлива"
        }
        val availColor = when (smartRec.availability) {
            FuelAvailabilityStatus.AVAILABLE -> Color(0xFF3ECDB0)
            FuelAvailabilityStatus.UNKNOWN -> Color(0xFFF5A94E)
            FuelAvailabilityStatus.UNAVAILABLE, FuelAvailabilityStatus.NO_FUEL -> Color(0xFFFF6F61)
        }

        Text(
            text = availText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = availColor
        )

        val visitCount = smartRec.personalVisitCount ?: uiState.personalVisitCount
        if (visitCount != null && visitCount > 0) {
            Text(
                text = "✓ Вы заправлялись здесь $visitCount раз",
                fontSize = 12.sp,
                color = Color(0xFF3ECDB0),
                fontWeight = FontWeight.Medium
            )
        }

        // 7. EXPLANATION: WHY THIS STATION?
        if (smartRec.reasons.isNotEmpty()) {
            Text(
                text = "Почему:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF5F7FA),
                modifier = Modifier.padding(top = 2.dp)
            )
            smartRec.reasons.take(4).forEach { reason ->
                Text(
                    text = "• ${reason.description.lowercase()}",
                    fontSize = 12.sp,
                    color = Color(0xFF8A97A5)
                )
            }
        }

        // Confidence
        val confidenceText = formatConfidence(smartRec.confidence)
        val timestamp = station.fuelTypes.find { it.type == selectedFuelType }?.updatedAt ?: station.updatedAt
        val freshnessText = formatFreshness(timestamp, currentTimeMs)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = confidenceText,
                fontSize = 11.sp,
                color = Color(0xFF8A97A5)
            )
            Text(
                text = freshnessText,
                fontSize = 11.sp,
                color = Color(0xFF8A97A5)
            )
        }
    }

    // 8. CTAs
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Button(
            onClick = { onRouteClick(smartRec.stationId) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF5A94E),
                contentColor = Color(0xFF1A1205)
            ),
            shape = FueldeckShapes.Lg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "ПОСТРОИТЬ МАРШРУТ",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        if (onDetailClick != null) {
            OutlinedButton(
                onClick = { onDetailClick(smartRec.stationId) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF5F7FA)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3D8A97A5)),
                shape = FueldeckShapes.Lg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Подробнее",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BestStationContent(
    recommendation: StationRecommendation,
    uiState: BestStationUiState,
    selectedFuelType: String,
    userLat: Double?,
    userLon: Double?,
    onRouteClick: (Int) -> Unit,
    onDetailClick: ((Int) -> Unit)?,
    currentTimeMs: Long
) {
    val station = recommendation.station

    // 1. HEADER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ЛУЧШАЯ АЗС СЕЙЧАС",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3ECDB0),
            letterSpacing = 1.2.sp
        )
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verified recommendation",
            tint = Color(0xFF3ECDB0),
            modifier = Modifier.size(16.dp)
        )
    }

    // 2. STATION NAME & ADDRESS
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = station.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF5F7FA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (station.address.isNotBlank()) {
            Text(
                text = station.address,
                fontSize = 13.sp,
                color = Color(0xFF8A97A5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // 3. FUEL & PRICE
    val fuel = station.fuelTypes.find { it.type == selectedFuelType }
    val priceText = formatPrice(fuel?.price ?: 0.0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0x29F5A94E),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = selectedFuelType,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5A94E),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Text(
            text = priceText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF5F7FA)
        )
    }

    // 4. DISTANCE & QUEUE
    val distanceKm = if (userLat != null && userLon != null && userLat != 0.0 && userLon != 0.0) {
        GeoUtils.calculateDistance(userLat, userLon, station.latitude, station.longitude)
    } else null

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        distanceKm?.let { dist ->
            Text(
                text = formatDistance(dist),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF5F7FA)
            )
        }

        if (station.queueTime > 0) {
            Text(
                text = "~${station.queueTime} мин очереди",
                fontSize = 13.sp,
                color = Color(0xFFF5A94E)
            )
        } else {
            Text(
                text = "Без очереди",
                fontSize = 13.sp,
                color = Color(0xFF8A97A5)
            )
        }
    }

    // 5. AVAILABILITY, CONFIDENCE & FRESHNESS
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val availability = PriceReliabilityCalculator.calculateFuelAvailability(station, selectedFuelType, currentTimeMs)
        val availText = when (availability) {
            FuelAvailabilityStatus.AVAILABLE -> "✓ Топливо есть"
            FuelAvailabilityStatus.UNKNOWN -> "Наличие не подтверждено"
            FuelAvailabilityStatus.UNAVAILABLE, FuelAvailabilityStatus.NO_FUEL -> "Нет топлива"
        }
        val availColor = when (availability) {
            FuelAvailabilityStatus.AVAILABLE -> Color(0xFF3ECDB0)
            FuelAvailabilityStatus.UNKNOWN -> Color(0xFFF5A94E)
            FuelAvailabilityStatus.UNAVAILABLE, FuelAvailabilityStatus.NO_FUEL -> Color(0xFFFF6F61)
        }

        Text(
            text = availText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = availColor
        )

        // Phase G Personal Preference Tag
        if (uiState.personalVisitCount != null && uiState.personalVisitCount > 0) {
            Text(
                text = "✓ Вам обычно подходит (вы заправлялись здесь ${uiState.personalVisitCount} раз)",
                fontSize = 12.sp,
                color = Color(0xFF3ECDB0),
                fontWeight = FontWeight.Medium
            )
        }

        // Reasons
        val displayReasons = recommendation.reasons.take(4)
        displayReasons.forEach { reason ->
            if (reason != StationRecommendationReason.FUEL_AVAILABLE) {
                Text(
                    text = "✓ ${formatReason(reason)}",
                    fontSize = 12.sp,
                    color = Color(0xFF8A97A5)
                )
            }
        }

        // Confidence & Freshness
        val confidenceText = formatConfidence(recommendation.confidence)
        val timestamp = fuel?.updatedAt ?: station.updatedAt
        val freshnessText = formatFreshness(timestamp, currentTimeMs)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = confidenceText,
                fontSize = 11.sp,
                color = Color(0xFF8A97A5)
            )
            Text(
                text = freshnessText,
                fontSize = 11.sp,
                color = Color(0xFF8A97A5)
            )
        }

        // Phase G Trip Fuel Cost Prediction
        uiState.tripCostPrediction?.let { trip ->
            if (trip.predictedCost != null) {
                Text(
                    text = "Прогноз поездки: ${trip.explanation}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF5A94E),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } ?: recommendation.estimatedTotalCost?.let { cost ->
            if (cost > 0.0) {
                Text(
                    text = "Ориентировочная стоимость поездки: ${formatPrice(cost)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF5A94E),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // 6. CTAs
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Button(
            onClick = { onRouteClick(station.id) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF5A94E),
                contentColor = Color(0xFF1A1205)
            ),
            shape = FueldeckShapes.Lg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "ПОСТРОИТЬ МАРШРУТ",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        if (onDetailClick != null) {
            OutlinedButton(
                onClick = { onDetailClick(station.id) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF5F7FA)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3D8A97A5)),
                shape = FueldeckShapes.Lg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Подробнее",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SmartStationAlternativesList(
    alternatives: List<SmartStationRecommendation>,
    selectedFuelType: String,
    userLat: Double?,
    userLon: Double?,
    onStationClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Другие хорошие варианты",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8A97A5)
        )

        alternatives.take(2).forEach { altRec ->
            val altStation = altRec.station
            val altDistance = altRec.distanceKm ?: if (userLat != null && userLon != null && userLat != 0.0 && userLon != 0.0) {
                GeoUtils.calculateDistance(userLat, userLon, altStation.latitude, altStation.longitude)
            } else null

            val primaryReasonTag = altRec.reasons.firstOrNull { it != SmartRecommendationReason.FUEL_AVAILABLE }?.description

            Surface(
                onClick = { onStationClick(altRec.stationId) },
                color = Color(0xFF141D26),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = altRec.stationName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF5F7FA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedFuelType,
                                fontSize = 11.sp,
                                color = Color(0xFFF5A94E)
                            )
                            altDistance?.let { dist ->
                                Text(
                                    text = formatDistance(dist),
                                    fontSize = 11.sp,
                                    color = Color(0xFF8A97A5)
                                )
                            }
                            primaryReasonTag?.let { tag ->
                                Text(
                                    text = "• $tag",
                                    fontSize = 11.sp,
                                    color = Color(0xFF3ECDB0)
                                )
                            }
                        }
                    }

                    Text(
                        text = formatPrice(altRec.price ?: 0.0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF5F7FA)
                    )
                }
            }
        }
    }
}

@Composable
fun BestStationAlternativesList(
    alternatives: List<StationRecommendation>,
    selectedFuelType: String,
    userLat: Double?,
    userLon: Double?,
    onStationClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Другие хорошие варианты",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8A97A5)
        )

        alternatives.take(2).forEach { altRec ->
            val altStation = altRec.station
            val altFuel = altStation.fuelTypes.find { it.type == selectedFuelType }
            val altDistance = if (userLat != null && userLon != null && userLat != 0.0 && userLon != 0.0) {
                GeoUtils.calculateDistance(userLat, userLon, altStation.latitude, altStation.longitude)
            } else null

            Surface(
                onClick = { onStationClick(altStation.id) },
                color = Color(0xFF141D26),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = altStation.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF5F7FA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedFuelType,
                                fontSize = 11.sp,
                                color = Color(0xFFF5A94E)
                            )
                            altDistance?.let { dist ->
                                Text(
                                    text = formatDistance(dist),
                                    fontSize = 11.sp,
                                    color = Color(0xFF8A97A5)
                                )
                            }
                        }
                    }

                    Text(
                        text = formatPrice(altFuel?.price ?: 0.0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF5F7FA)
                    )
                }
            }
        }
    }
}

@Composable
fun BestStationLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Color(0xFFF5A94E),
            strokeWidth = 3.dp
        )
        Text(
            text = "Анализ вариантов АЗС...",
            fontSize = 13.sp,
            color = Color(0xFF8A97A5)
        )
    }
}

@Composable
fun BestStationEmptyCard(
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ЛУЧШАЯ АЗС СЕЙЧАС",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8A97A5)
        )
        Text(
            text = "Пока не удалось найти подходящую АЗС.\nПроверьте выбранное топливо или обновите данные.",
            fontSize = 13.sp,
            color = Color(0xFF8A97A5),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (onRefresh != null) {
            TextButton(
                onClick = onRefresh,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF5A94E))
            ) {
                Text("Обновить", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun BestStationErrorCard(
    errorMessage: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Не удалось обновить рекомендации",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFF6F61)
        )
        Text(
            text = errorMessage,
            fontSize = 12.sp,
            color = Color(0xFF8A97A5),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF5A94E),
                    contentColor = Color(0xFF1A1205)
                ),
                shape = FueldeckShapes.Md
            ) {
                Text("Повторить", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatPrice(price: Double): String {
    if (price <= 0.0) return "Цена неизвестна"
    return String.format(Locale.getDefault(), "%.2f ₽", price).replace('.', ',')
}

private fun formatDistance(distanceKm: Double): String {
    return if (distanceKm < 1.0) {
        "${(distanceKm * 1000).toInt()} м"
    } else {
        String.format(Locale.getDefault(), "%.1f км", distanceKm).replace('.', ',')
    }
}

private fun formatConfidence(confidence: RecommendationConfidence): String {
    return when (confidence) {
        RecommendationConfidence.HIGH -> "Данные: высокая уверенность"
        RecommendationConfidence.MEDIUM -> "Данные: средняя уверенность"
        RecommendationConfidence.LOW -> "Данные: низкая уверенность"
        RecommendationConfidence.UNKNOWN -> "Данные: уверенность неизвестна"
    }
}

private fun formatFreshness(timestampMs: Long, currentTimeMs: Long): String {
    if (timestampMs <= 0L) return "Время обновления неизвестно"
    val ageMin = maxOf(0L, (currentTimeMs - timestampMs) / 60000L)
    return if (ageMin < 1) "Обновлено только что" else "Обновлено $ageMin мин назад"
}

private fun formatReason(reason: StationRecommendationReason): String {
    return when (reason) {
        StationRecommendationReason.FUEL_AVAILABLE -> "Топливо есть"
        StationRecommendationReason.LOW_PRICE -> "Цена ниже средней"
        StationRecommendationReason.SHORT_DISTANCE -> "Близко"
        StationRecommendationReason.SHORT_QUEUE -> "Очередь небольшая"
        StationRecommendationReason.HIGH_RELIABILITY -> "Высокая надёжность"
        StationRecommendationReason.FRESH_DATA -> "Данные свежие"
        StationRecommendationReason.LOW_TOTAL_COST -> "Выгодная поездка"
    }
}
