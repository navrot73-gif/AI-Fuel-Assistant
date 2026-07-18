package com.navrot.aifuelassistant.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.navrot.aifuelassistant.data.VehicleCatalog
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    onBack: () -> Unit,
    viewModel: VehicleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var name by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var selectedFuelType by remember { mutableStateOf("") }
    var tankCapacity by remember { mutableStateOf("") }
    var currentMileage by remember { mutableStateOf("") }

    // Состояния для выпадающих списков
    var brandExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var fuelTypeExpanded by remember { mutableStateOf(false) }

    // Получаем список моделей для выбранной марки
    val models = remember(selectedBrand) {
        VehicleCatalog.getModels(selectedBrand)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить автомобиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Название
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                placeholder = { Text("Например: Моя Toyota") },
                modifier = Modifier.fillMaxWidth()
            )

            // Выпадающий список марок
            ExposedDropdownMenuBox(
                expanded = brandExpanded,
                onExpandedChange = { brandExpanded = !brandExpanded }
            ) {
                OutlinedTextField(
                    value = selectedBrand,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Марка") },
                    placeholder = { Text("Выберите марку") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = brandExpanded,
                    onDismissRequest = { brandExpanded = false }
                ) {
                    VehicleCatalog.brands.forEach { brand ->
                        DropdownMenuItem(
                            text = { Text(brand) },
                            onClick = {
                                selectedBrand = brand
                                selectedModel = "" // Сбрасываем модель при смене марки
                                brandExpanded = false
                            }
                        )
                    }
                }
            }

            // Выпадающий список моделей (активен только если выбрана марка)
            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = {
                    if (selectedBrand.isNotEmpty()) {
                        modelExpanded = !modelExpanded
                    }
                }
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Модель") },
                    placeholder = {
                        Text(
                            if (selectedBrand.isEmpty()) "Сначала выберите марку"
                            else "Выберите модель"
                        )
                    },
                    enabled = selectedBrand.isNotEmpty(),
                    trailingIcon = {
                        if (selectedBrand.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                if (selectedBrand.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    selectedModel = model
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Год
            OutlinedTextField(
                value = year,
                onValueChange = { year = it },
                label = { Text("Год выпуска") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Выпадающий список типов топлива
            ExposedDropdownMenuBox(
                expanded = fuelTypeExpanded,
                onExpandedChange = { fuelTypeExpanded = !fuelTypeExpanded }
            ) {
                OutlinedTextField(
                    value = selectedFuelType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип топлива") },
                    placeholder = { Text("Выберите тип топлива") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelTypeExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = fuelTypeExpanded,
                    onDismissRequest = { fuelTypeExpanded = false }
                ) {
                    VehicleCatalog.fuelTypes.forEach { fuelType ->
                        DropdownMenuItem(
                            text = { Text(fuelType) },
                            onClick = {
                                selectedFuelType = fuelType
                                fuelTypeExpanded = false
                            }
                        )
                    }
                }
            }

            // Объём бака
            OutlinedTextField(
                value = tankCapacity,
                onValueChange = { tankCapacity = it },
                label = { Text("Объём бака (л)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Пробег
            OutlinedTextField(
                value = currentMileage,
                onValueChange = { currentMileage = it },
                label = { Text("Текущий пробег (км)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotBlank() && selectedBrand.isNotBlank() && selectedModel.isNotBlank()) {
                        viewModel.addVehicle(
                            VehicleEntity(
                                name = name,
                                brand = selectedBrand,
                                model = selectedModel,
                                year = year.toIntOrNull() ?: 2024,
                                fuelType = selectedFuelType.ifBlank { "Бензин" },
                                tankCapacity = tankCapacity.toDoubleOrNull() ?: 50.0,
                                currentMileage = currentMileage.toDoubleOrNull() ?: 0.0
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && selectedBrand.isNotBlank() && selectedModel.isNotBlank()
            ) {
                Text("Сохранить")
            }
        }
    }
}