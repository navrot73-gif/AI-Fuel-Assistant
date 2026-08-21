package com.navrot.aifuelassistant.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navrot.aifuelassistant.data.VehicleCatalog

private val YEARS: List<String> = (2026 downTo 1990).map { it.toString() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    vehicleId: Long? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf(VehicleCatalog.fuelTypes.first()) }
    var tankCapacity by remember { mutableStateOf("") }
    var currentMileage by remember { mutableStateOf("") }
    var attemptedSave by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(vehicleId) {
        if (vehicleId != null && vehicleId > 0L) {
            val entity = viewModel.getVehicleEntity(vehicleId)
            if (entity != null) {
                name = entity.name
                brand = entity.brand
                model = entity.model
                year = entity.year.toString()
                fuelType = entity.fuelType.ifBlank { VehicleCatalog.fuelTypes.first() }
                tankCapacity = if (entity.tankCapacity % 1.0 == 0.0) entity.tankCapacity.toInt().toString() else entity.tankCapacity.toString()
                currentMileage = if (entity.currentMileage % 1.0 == 0.0) entity.currentMileage.toLong().toString() else entity.currentMileage.toString()
            }
        }
    }

    val modelOptions = if (brand.isBlank()) emptyList() else VehicleCatalog.getModels(brand)

    // Валидация
    val nameError = attemptedSave && name.isBlank()
    val brandError = attemptedSave && brand.isBlank()
    val yearError = attemptedSave && year.isBlank()
    val tankError = attemptedSave && (tankCapacity.isBlank() || (tankCapacity.toDoubleOrNull() ?: 0.0) <= 0)
    val mileageError = attemptedSave && (currentMileage.isBlank() || (currentMileage.toDoubleOrNull() ?: 0.0) < 0)
    val isFormValid = name.isNotBlank() && brand.isNotBlank() && year.isNotBlank()
            && (tankCapacity.toDoubleOrNull() ?: 0.0) > 0
            && (currentMileage.toDoubleOrNull() ?: 0.0) >= 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (vehicleId != null && vehicleId > 0L) "Редактировать автомобиль" else "Добавить автомобиль")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = nameError,
                supportingText = if (nameError) {{ Text("Укажите название") }} else null
            )

            EditableDropdownField(
                label = "Марка",
                value = brand,
                options = VehicleCatalog.brands,
                onValueChange = { brand = it; model = "" },
                isError = brandError,
                errorText = "Выберите марку"
            )

            EditableDropdownField(
                label = "Модель",
                value = model,
                options = modelOptions,
                onValueChange = { model = it }
            )

            ReadOnlyDropdownField(
                label = "Год выпуска",
                value = year,
                options = YEARS,
                onValueChange = { year = it },
                isError = yearError,
                errorText = "Выберите год"
            )

            EditableDropdownField(
                label = "Тип топлива",
                value = fuelType,
                options = VehicleCatalog.fuelTypes,
                onValueChange = { fuelType = it }
            )

            OutlinedTextField(
                value = tankCapacity,
                onValueChange = { tankCapacity = it },
                label = { Text("Объём бака (л)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = tankError,
                supportingText = if (tankError) {{ Text("Укажите объём больше 0") }} else null
            )

            OutlinedTextField(
                value = currentMileage,
                onValueChange = { currentMileage = it },
                label = { Text("Пробег (км)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = mileageError,
                supportingText = if (mileageError) {{ Text("Укажите корректный пробег") }} else null
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !attemptedSave || isFormValid,
                onClick = {
                    attemptedSave = true
                    if (isFormValid) {
                        if (vehicleId != null && vehicleId > 0L) {
                            viewModel.updateVehicle(
                                id = vehicleId,
                                name = name,
                                brand = brand,
                                model = model,
                                year = year.toIntOrNull() ?: 2026,
                                fuelType = fuelType.ifBlank { VehicleCatalog.fuelTypes.first() },
                                tankCapacity = tankCapacity.toDoubleOrNull() ?: 50.0,
                                currentMileage = currentMileage.toDoubleOrNull() ?: 0.0
                            )
                        } else {
                            viewModel.addVehicle(
                                name = name,
                                brand = brand,
                                model = model,
                                year = year.toIntOrNull() ?: 2026,
                                fuelType = fuelType.ifBlank { VehicleCatalog.fuelTypes.first() },
                                tankCapacity = tankCapacity.toDoubleOrNull() ?: 50.0,
                                currentMileage = currentMileage.toDoubleOrNull() ?: 0.0
                            )
                        }
                        onNavigateBack()
                    }
                }
            ) {
                Text("Сохранить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    errorText: String = ""
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, options) {
        if (value.isBlank()) options
        else options.filter { it.contains(value, ignoreCase = true) }.take(50)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            isError = isError,
            supportingText = if (isError) {{ Text(errorText) }} else null,
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onValueChange(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadOnlyDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    errorText: String = ""
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            isError = isError,
            supportingText = if (isError) {{ Text(errorText) }} else null,
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onValueChange(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}