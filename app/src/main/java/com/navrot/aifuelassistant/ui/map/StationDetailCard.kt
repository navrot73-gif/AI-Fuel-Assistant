package com.navrot.aifuelassistant.ui.map

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.navrot.aifuelassistant.BuildConfig
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.components.NetworkImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import android.util.Base64

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
    var showPriceDialog by remember { mutableStateOf(false) }
    var showOcrLoading by remember { mutableStateOf(false) }
    var ocrError by remember { mutableStateOf<String?>(null) }
    var ocrResults by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var showOcrResultDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    // Camera launcher
    val takePictureLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempPhotoFile != null) {
            scope.processOcrPhoto(
                photoFile = tempPhotoFile!!,
                onLoading = { showOcrLoading = it },
                onError = { ocrError = it },
                onResults = { results ->
                    ocrResults = results
                    showOcrResultDialog = true
                },
                onEmpty = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Не удалось распознать цены, введите вручную")
                    }
                }
            )
        } else if (!success) {
            scope.launch {
                snackbarHostState.showSnackbar("Фото не было сделано")
            }
        }
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
                            text = String.format("%.2f", fuel.price),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== Очередь и надёжность =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Очередь: ${station.queueTime} мин",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Надёжность: ${station.reliability}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ===== Кнопка: сообщить цену (нижняя часть карточки) =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showPriceDialog = true },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("💰 Сообщить цену")
                }
                OutlinedButton(
                    onClick = {
                        // Create temp file and launch camera
                        val cacheDir = context.cacheDir
                        val photoFile = File(cacheDir, "stela_photo_${System.currentTimeMillis()}.jpg")
                        tempPhotoFile = photoFile
                        val photoUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        takePictureLauncher.launch(photoUri)
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !showOcrLoading
                ) {
                    Text("📷 Фото стеллы")
                }
            }

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

    if (showOcrResultDialog) {
        OcrResultDialog(
            station = station,
            results = ocrResults,
            onDismiss = { showOcrResultDialog = false },
            onSaveAll = { results ->
                results.forEach { (fuelType, price) ->
                    onReportPrice(station.id, fuelType, price)
                }
                showOcrResultDialog = false
            }
        )
    }

    if (showOcrLoading) {
        OcrLoadingOverlay()
    }

    SnackbarHost(hostState = snackbarHostState)
}

// Process OCR photo - helper function
fun CoroutineScope.processOcrPhoto(
    photoFile: File,
    onLoading: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onResults: (Map<String, Double>) -> Unit,
    onEmpty: () -> Unit
) {
    onLoading(true)
    onError(null)

    launch {
        try {
            // Read file and convert to Base64
            val bytes = photoFile.readBytes()
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

            // Call OCR API
            val results = withContext(Dispatchers.IO) {
                callOcrApi(base64Image)
            }

            onLoading(false)
            if (results.isNotEmpty()) {
                onResults(results)
            } else {
                onEmpty()
            }
        } catch (e: Exception) {
            onLoading(false)
            onError(e.message ?: "Ошибка распознавания")
            onEmpty()
        }
    }
}

// Call OCR API - helper function
suspend fun callOcrApi(base64Image: String): Map<String, Double> {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val jsonBody = JSONObject().put("image", base64Image).toString()
    val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

    val request = Request.Builder()
        .url("https://ai-fuel-proxy.navrot73.workers.dev/ocr-stela")
        .addHeader("X-Proxy-Token", BuildConfig.PROXY_TOKEN)
        .addHeader("Content-Type", "application/json")
        .post(requestBody)
        .build()

    val response = client.newCall(request).execute()
    val responseBody = response.body?.string() ?: throw Exception("Empty response")

    if (!response.isSuccessful) {
        throw Exception("API error: ${response.code} - $responseBody")
    }

    val json = JSONObject(responseBody)
    val pricesJson = json.getJSONObject("prices")
    val results = mutableMapOf<String, Double>()
    val iterator = pricesJson.keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        val value = pricesJson.getDouble(key)
        results[key] = value
    }
    return results
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
    val parsedPrice = priceText.toDoubleOrNull()
    val priceValid = parsedPrice != null && parsedPrice > 0 && parsedPrice <= 200

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
                        val normalized = newValue.replace(',', '.')
                        val filtered = normalized.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) priceText = filtered
                    },
                    label = { Text("Новая цена, ₽") },
                    placeholder = { Text("65.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = priceText.isNotEmpty() && !priceValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (priceText.isNotEmpty() && !priceValid) {
                    Text("Цена должна быть от 0 до 200 ₽", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error)
                }
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
                enabled = priceValid && selectedFuelType.isNotEmpty()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// OCR Result Dialog
@Composable
private fun OcrResultDialog(
    station: GasStation,
    results: Map<String, Double>,
    onDismiss: () -> Unit,
    onSaveAll: (Map<String, Double>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Распознанные цены") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("АЗС: ${station.brand}", style = MaterialTheme.typography.bodySmall)
                Text("Проверьте и сохраните:", fontWeight = FontWeight.SemiBold)

                results.entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "АИ-${entry.key}: ${String.format("%.1f", entry.value)} ₽",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Распознано",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSaveAll(results) }) {
                Text("Сохранить все")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// Loading indicator
@Composable
private fun OcrLoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Распознаю цены...",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}