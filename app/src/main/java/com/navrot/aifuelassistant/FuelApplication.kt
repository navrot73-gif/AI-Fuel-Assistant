package com.navrot.aifuelassistant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FuelApplication : Application() {

    // Единый экземпляр базы данных для всего приложения.
    // Сейчас используется в AppDatabase.getInstance() — Hilt его не трогает.
    // Оставляем поле для обратной совместимости, но ViewModel'ы получают DAO через Hilt.
    lateinit var database: com.navrot.aifuelassistant.data.database.AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = com.navrot.aifuelassistant.data.database.AppDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: FuelApplication
            private set
    }
}