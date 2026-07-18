package com.navrot.aifuelassistant.app

import android.app.Application
import androidx.room.Room
import com.navrot.aifuelassistant.data.database.AppDatabase
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FuelApplication : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "fuel_database"
        )
            .fallbackToDestructiveMigration() // Удалит старую БД и создаст новую (только для разработки!)
            .build()

        // Добавляем тестовые данные при первом запуске
        addTestData()
    }

    private fun addTestData() {
        CoroutineScope(Dispatchers.IO).launch {
            val existingVehicles = database.vehicleDao().getAll()

            val currentList = mutableListOf<VehicleEntity>()
            existingVehicles.collect { list ->
                if (list.isEmpty() && currentList.isEmpty()) {
                    currentList.addAll(list)

                    database.vehicleDao().insert(
                        VehicleEntity(
                            name = "Моя Toyota",
                            brand = "Toyota",
                            model = "Camry",
                            year = 2020,
                            fuelType = "Бензин АИ-95",
                            tankCapacity = 60.0,
                            currentMileage = 45000.0
                        )
                    )

                    database.vehicleDao().insert(
                        VehicleEntity(
                            name = "Рабочий Газель",
                            brand = "ГАЗ",
                            model = "Газель Next",
                            year = 2019,
                            fuelType = "Дизель",
                            tankCapacity = 70.0,
                            currentMileage = 120000.0
                        )
                    )

                    database.vehicleDao().insert(
                        VehicleEntity(
                            name = "Семейный Kia",
                            brand = "Kia",
                            model = "Sportage",
                            year = 2022,
                            fuelType = "Бензин АИ-92",
                            tankCapacity = 55.0,
                            currentMileage = 15000.0
                        )
                    )
                }
            }
        }
    }

    companion object {
        lateinit var instance: FuelApplication
            private set
    }
}