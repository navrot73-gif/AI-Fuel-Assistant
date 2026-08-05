package com.navrot.aifuelassistant.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.navrot.aifuelassistant.R
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.theme.AIFuelAssistantTheme

@Composable
fun StationDetailCard(
    station: GasStation,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = station.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = station.address, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            station.fuelPrices.forEach { fuelPrice ->
                FuelPriceItem(fuelPrice = fuelPrice)
            }
        }
    }
}

@Composable
private fun FuelPriceItem(fuelPrice: FuelPrice) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = fuelPrice.type,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${fuelPrice.price} ₽",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StationDetailCardPreview() {
    AIFuelAssistantTheme {
        val station = GasStation(
            id = 1,
            name = "АЗС №1",
            address = "ул. Ленина, 1",
            fuelPrices = listOf(
                FuelPrice(type = "АИ-92", price = 50.0),
                FuelPrice(type = "АИ-95", price = 55.0),
                FuelPrice(type = "ДТ", price = 60.0)
            )
        )
        StationDetailCard(station = station)
    }
}