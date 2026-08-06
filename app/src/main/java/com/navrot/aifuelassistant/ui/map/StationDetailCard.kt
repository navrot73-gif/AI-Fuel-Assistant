package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navrot.aifuelassistant.data.model.GasStation

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
    var expanded by remember { mutableStateOf(false) }
    var showPriceDialog by remember { mutableStateOf(false) }

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
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
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
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }

            // ===== Раскрывающаяся часть: цены и подробности =====
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                station.fuelTypes.forEach { fuel ->
                    val isSelected = selectedFuelTypes.contains(fuel.type)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (fuel.available) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = fuel.type,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = String.format("%.2f ₽", fuel.price),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "Очередь: ${station.queueTime} мин",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "Надёжность: ${station.reliability}%",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ===== Переключатель подробностей =====
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Скрыть подробности ▴" else "Цены и подробности ▾")
            }

            // ===== Кнопка "Сообщить цену" =====
            OutlinedButton(
                onClick = { showPriceDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("💬 Сообщить цену")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== Маршрут =====
            Button(
                onClick = onBuildRoute,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRouting) "Уточняем по дорогам..." else "Построить маршрут")
            }

            if (routeText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openMapsRoute(context, station.latitude, station.longitude, station.brand) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Открыть в навигаторе")
                }
            }
        }
    }

    if (showPriceDialog) {
        ReportPriceDialog(
            station = station,
            onDismiss = { showPriceDialog = false },
            onConfirm = { fuelType, price ->
                onReportPrice(station.id, fuelType, price)
                showPriceDialog = false
            }
        )
    }
}

@Composable
private fun ReportPriceDialog(
    station: GasStation,
    onDismiss: () -> Unit,
    onConfirm: (fuelType: String, price: Double) -> Unit
) {
    var selectedFuelType by remember {
        mutableStateOf(station.fuelTypes.firstOrNull()?.type ?: "")
    }
    var priceText by remember { mutableStateOf("") }
    val priceError = priceText.toDoubleOrNull()?.let { it <= 0 || it > 200 } ?: priceText.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сообщить цену") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("АЗС: ${station.brand}", style = MaterialTheme.typography.bodySmall)
                Text("Выберите топливо:", fontWeight = FontWeight.SemiBold)
                station.fuelTypes.forEach { fuel ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFuelType == fuel.type,
                            onClick = { selectedFuelType = fuel.type }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(fuel.type, modifier = Modifier.weight(1f))
                        Text(
                            String.format("%.2f ₽", fuel.price),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { newValue ->
                        // Разрешаем цифры и одну точку
                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) priceText = filtered
                    },
                    label = { Text("Новая цена, ₽") },
                    placeholder = { Text("65.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = priceError,
                    supportingText = {
                        if (priceError) Text("Цена должна быть от 0 до 200 ₽")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (price != null && price > 0 && price <= 200 && selectedFuelType.isNotEmpty()) {
                        onConfirm(selectedFuelType, price)
                    }
                },
                enabled = priceText.toDoubleOrNull()?.let { it > 0 && it <= 200 } == true
                        && selectedFuelType.isNotEmpty()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
