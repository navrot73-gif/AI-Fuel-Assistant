package com.navrot.aifuelassistant.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navrot.aifuelassistant.FuelApplication
import com.navrot.aifuelassistant.data.VehicleRepositoryImpl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ИСПРАВЛЕНО: Инициализируем репозиторий через базу данных из FuelApplication
    val viewModel: VehicleViewModel = viewModel(
        factory = VehicleViewModelFactory(
            repository = VehicleRepositoryImpl(FuelApplication.instance.database.vehicleDao())
        )
    )

    // Состояния для текстовых полей ввода
    var name by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var selectedFuelType by remember { mutableStateOf("") }
    var tankCapacity by remember { mutableStateOf("") }
    var currentMileage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Добавить автомобиль") })
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(value = name, onValueChange = { name = it }, label = { Text("Название") })
            TextField(value = selectedBrand, onValueChange = { selectedBrand = it }, label = { Text("Марка") })
            TextField(value = selectedModel, onValueChange = { selectedModel = it }, label = { Text("Модель") })
            TextField(value = year, onValueChange = { year = it }, label = { Text("Год") })
            TextField(value = selectedFuelType, onValueChange = { selectedFuelType = it }, label = { Text("Тип топлива") })
            TextField(value = tankCapacity, onValueChange = { tankCapacity = it }, label = { Text("Объем бака") })
            TextField(value = currentMileage, onValueChange = { currentMileage = it }, label = { Text("Пробег") })

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.addVehicle(
                        name = name,
                        brand = selectedBrand,
                        model = selectedModel,
                        year = year.toIntOrNull() ?: 2026,
                        fuelType = selectedFuelType.ifBlank { "Бензин" },
                        tankCapacity = tankCapacity.toDoubleOrNull() ?: 50.0,
                        currentMileage = currentMileage.toDoubleOrNull() ?: 0.0
                    )

                    onNavigateBack() // Возвращаемся назад после сохранения
                }
            ) {
                Text("Сохранить")
            }
        }
    }
}