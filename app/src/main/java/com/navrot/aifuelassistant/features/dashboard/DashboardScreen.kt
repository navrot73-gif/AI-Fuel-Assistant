package com.navrot.aifuelassistant.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navrot.aifuelassistant.FuelApplication
import com.navrot.aifuelassistant.ai.AiRouterFactory
import com.navrot.aifuelassistant.data.FuelRecordRepositoryImpl

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val repository = remember {
        FuelRecordRepositoryImpl(FuelApplication.instance.database.fuelRecordDao())
    }
    val aiRouter = remember { AiRouterFactory.create() }
    val factory = remember(repository, aiRouter) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return DashboardViewModel(repository, aiRouter) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val analysis by viewModel.analysis.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AI Fuel Assistant",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Добро пожаловать! Здесь можно получить анализ ваших записей о заправках.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = viewModel::askAi,
            enabled = !isAnalyzing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator()
            } else {
                Text(text = "Спросить AI")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        analysis?.let {
            Text(
                text = "AI-анализ",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
