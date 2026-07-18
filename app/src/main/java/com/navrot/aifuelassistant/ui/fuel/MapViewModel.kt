package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navrot.aifuelassistant.data.GasStationRepository
import com.navrot.aifuelassistant.data.model.GasStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {

    private val repository = GasStationRepository

    private val _stations = MutableStateFlow<List<GasStation>>(emptyList())
    val stations: StateFlow<List<GasStation>> = _stations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ===== МНОЖЕСТВЕННЫЙ ВЫБОР ТОПЛИВА =====
    private val _selectedFuelTypes = MutableStateFlow<Set<String>>(setOf("АИ-95"))
    val selectedFuelTypes: StateFlow<Set<String>> = _selectedFuelTypes.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.BEST)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _cheapestStation = MutableStateFlow<GasStation?>(null)
    val cheapestStation: StateFlow<GasStation?> = _cheapestStation.asStateFlow()

    private val _bestStation = MutableStateFlow<GasStation?>(null)
    val bestStation: StateFlow<GasStation?> = _bestStation.asStateFlow()

    enum class SortMode {
        BEST, PRICE_ASC, PRICE_DESC, NEARBY, QUEUE
    }

    fun loadNearbyStations(lat: Double, lon: Double, radiusKm: Double = 50.0) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _stations.value = repository.getNearbyStations(lat, lon, radiusKm)
                updateBestAndCheapest(lat, lon, radiusKm)
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadStationsByCity(city: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _stations.value = repository.getStationsByCity(city)
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchStations(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _stations.value = repository.searchStations(query)
            } catch (e: Exception) {
                _error.value = "Ошибка поиска: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ===== ПЕРЕКЛЮЧЕНИЕ ТИПА ТОПЛИВА (множественный выбор) =====
    fun toggleFuelType(fuelType: String) {
        val current = _selectedFuelTypes.value.toMutableSet()
        if (current.contains(fuelType)) {
            // Не удаляем последний элемент — должен быть хоть один выбран
            if (current.size > 1) {
                current.remove(fuelType)
            }
        } else {
            current.add(fuelType)
        }
        _selectedFuelTypes.value = current
        viewModelScope.launch {
            updateBestAndCheapest(55.1644, 61.4368, 50.0)
        }
    }

    // ===== УСТАНОВИТЬ ОДИН ТИП (для совместимости) =====
    fun setFuelType(fuelType: String) {
        _selectedFuelTypes.value = setOf(fuelType)
        viewModelScope.launch {
            updateBestAndCheapest(55.1644, 61.4368, 50.0)
        }
    }

    fun setSortMode(mode: SortMode, lat: Double? = null, lon: Double? = null) {
        _sortMode.value = mode
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _stations.value = when (mode) {
                    SortMode.BEST -> repository.getBestStations(
                        _selectedFuelTypes.value.first(), lat, lon, 50.0
                    )
                    SortMode.PRICE_ASC -> repository.getStationsSortedByPriceAsc(
                        _selectedFuelTypes.value.first(), lat, lon, 50.0
                    )
                    SortMode.PRICE_DESC -> repository.getStationsSortedByPriceDesc(
                        _selectedFuelTypes.value.first(), lat, lon, 50.0
                    )
                    SortMode.NEARBY -> if (lat != null && lon != null) {
                        repository.getNearbyStations(lat, lon, 50.0)
                    } else _stations.value
                    SortMode.QUEUE -> repository.getStationsByQueue(
                        _selectedFuelTypes.value.first(), lat, lon, 50.0
                    )
                }
            } catch (e: Exception) {
                _error.value = "Ошибка сортировки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun findCheapest(lat: Double? = null, lon: Double? = null, radiusKm: Double = 50.0) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _cheapestStation.value = repository.getCheapestStation(
                    _selectedFuelTypes.value.first(), lat, lon, radiusKm
                )
            } catch (e: Exception) {
                _error.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateBestAndCheapest(lat: Double, lon: Double, radiusKm: Double) {
        _bestStation.value = repository.getBestStations(
            _selectedFuelTypes.value.first(), lat, lon, radiusKm
        ).firstOrNull()
        _cheapestStation.value = repository.getCheapestStation(
            _selectedFuelTypes.value.first(), lat, lon, radiusKm
        )
    }

    fun clearError() { _error.value = null }
}