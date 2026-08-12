package com.navrot.aifuelassistant.ui.fuel

import androidx.lifecycle.ViewModel
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GasStationDetailViewModel @Inject constructor(
    val repository: GasStationRepositoryInterface
) : ViewModel()