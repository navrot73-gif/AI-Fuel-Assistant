package com.navrot.aifuelassistant.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.ai.AiRouter
import com.navrot.aifuelassistant.ai.FuelAnalysisPromptBuilder
import com.navrot.aifuelassistant.data.FuelRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: FuelRecordRepository,
    private val aiRouter: AiRouter
) : ViewModel() {

    private val _analysis = MutableStateFlow<String?>(null)
    val analysis: StateFlow<String?> = _analysis.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun askAi() {
        if (_isAnalyzing.value) return

        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null

            try {
                val records = repository.getAll().first()
                val prompt = FuelAnalysisPromptBuilder.build(records)
                _analysis.value = aiRouter.ask(prompt)
            } catch (error: Throwable) {
                _error.value = error.message ?: "Не удалось получить AI-анализ"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}
