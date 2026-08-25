package com.navrot.aifuelassistant.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navrot.aifuelassistant.data.model.FuelReport
import com.navrot.aifuelassistant.data.model.ReportPeriod
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import com.navrot.aifuelassistant.ui.theme.FueldeckShapes
import java.util.Locale

private data class MetricItem(
    val title: String,
    val valueFormatted: String,
    val unit: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Отчёты по заправкам",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FueldeckColors.Ink
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = FueldeckColors.Ink
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FueldeckColors.Bg1
                )
            )
        },
        containerColor = FueldeckColors.Bg1
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(FueldeckColors.Bg1)
        ) {
            PeriodFilterRow(
                selectedPeriod = state.selectedPeriod,
                onPeriodSelected = viewModel::onPeriodSelected
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = FueldeckColors.Amber
                        )
                    }
                }
                state.report == null || state.report?.totalRefuels == 0 -> {
                    EmptyReportState()
                }
                else -> {
                    ReportMetricsGrid(report = state.report!!)
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(
    selectedPeriod: ReportPeriod,
    onPeriodSelected: (ReportPeriod) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReportPeriod.entries.forEach { period ->
            val isSelected = selectedPeriod == period
            FilterChip(
                selected = isSelected,
                onClick = { onPeriodSelected(period) },
                label = {
                    Text(
                        text = period.getLabel(),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = FueldeckColors.Surface,
                    labelColor = FueldeckColors.InkDim,
                    selectedContainerColor = FueldeckColors.Amber,
                    selectedLabelColor = Color(0xFF1A1205)
                ),
                shape = FueldeckShapes.Pill
            )
        }
    }
}

@Composable
private fun EmptyReportState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Assessment,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = FueldeckColors.InkFaint
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Нет заправок за выбранный период",
                color = FueldeckColors.InkDim,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ReportMetricsGrid(report: FuelReport) {
    val locale = Locale("ru")
    val metrics = listOf(
        MetricItem(
            title = "Расход",
            valueFormatted = String.format(locale, "%.1f", report.averageConsumptionPer100Km),
            unit = "л/100 км"
        ),
        MetricItem(
            title = "Стоимость",
            valueFormatted = String.format(locale, "%.1f", report.costPerKm),
            unit = "₽/км"
        ),
        MetricItem(
            title = "Средняя цена",
            valueFormatted = String.format(locale, "%.1f", report.averagePricePerLiter),
            unit = "₽/л"
        ),
        MetricItem(
            title = "Всего потрачено",
            valueFormatted = String.format(locale, "%.1f", report.totalCost),
            unit = "₽"
        ),
        MetricItem(
            title = "Заправлено",
            valueFormatted = String.format(locale, "%.1f", report.totalLiters),
            unit = "л"
        ),
        MetricItem(
            title = "Пробег",
            valueFormatted = String.format(locale, "%.1f", report.totalDistanceKm),
            unit = "км"
        ),
        MetricItem(
            title = "Заправок",
            valueFormatted = "${report.totalRefuels}",
            unit = "шт"
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(metrics) { item ->
            MetricCard(metric = item)
        }
    }
}

@Composable
private fun MetricCard(metric: MetricItem) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = FueldeckColors.Surface
        ),
        shape = FueldeckShapes.Md,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = metric.title,
                fontSize = 12.sp,
                color = FueldeckColors.InkDim,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = metric.valueFormatted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FueldeckColors.Ink
                )
                Text(
                    text = metric.unit,
                    fontSize = 12.sp,
                    color = FueldeckColors.Mint,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

private fun ReportPeriod.getLabel(): String {
    return when (this) {
        ReportPeriod.LAST_7_DAYS -> "7д"
        ReportPeriod.LAST_30_DAYS -> "30д"
        ReportPeriod.LAST_90_DAYS -> "90д"
        ReportPeriod.LAST_YEAR -> "1 год"
        ReportPeriod.ALL_TIME -> "Всё время"
    }
}
