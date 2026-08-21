package com.navrot.aifuelassistant.ui.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navrot.aifuelassistant.ui.components.VehicleCard
import com.navrot.aifuelassistant.ui.theme.FueldeckColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicleId: Long,
    vehicleName: String,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: VehicleViewModel = hiltViewModel()
) {
    val vehicles by viewModel.vehiclesWithStats.collectAsStateWithLifecycle()
    val activeVehicleId by viewModel.activeVehicleId.collectAsStateWithLifecycle()
    
    val vehicle = vehicles.find { it.id == vehicleId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "ВАШ ПАРК",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = FueldeckColors.Mint,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Start,
                            )
                            Text(
                                text = vehicleName,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = FueldeckColors.Ink,
                                textAlign = TextAlign.Start,
                            )
                        }
                        Text(
                            text = "${vehicles.size} авто",
                            fontSize = 14.sp,
                            color = Color(0xFF8A949E),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditClick(vehicleId) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Редактировать автомобиль",
                            tint = FueldeckColors.Ink
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = FueldeckColors.Bg1,
                ),
            )
        },
        containerColor = FueldeckColors.Bg1,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = FueldeckColors.Amber,
                contentColor = Color(0xFF1A1205),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить заправку")
            }
        },
    ) { padding ->
        if (vehicle != null) {
            val isActive = activeVehicleId == vehicle.id
            VehicleCard(
                state = vehicle,
                isActive = isActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        } else {
            Box(
                modifier = modifier.padding(padding).fillMaxSize().background(FueldeckColors.Bg1),
                contentAlignment = Alignment.Center,
            ) {
                Text("Автомобиль не найден", color = FueldeckColors.InkDim)
            }
        }
    }
}