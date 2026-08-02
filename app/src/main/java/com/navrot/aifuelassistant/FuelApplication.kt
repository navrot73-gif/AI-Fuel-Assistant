package com.navrot.aifuelassistant

import android.app.Application
import com.navrot.aifuelassistant.data.GasStationRepository
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FuelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GasStationRepository.init(this)
    }
}