package com.navrot.aifuelassistant.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. Создаем новую таблицу со строгим соответствием схеме v3
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `fuel_records_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `vehicleId` INTEGER NOT NULL,
                    `date` INTEGER NOT NULL,
                    `mileage` REAL NOT NULL,
                    `fuelAmount` REAL NOT NULL,
                    `pricePerLiter` REAL NOT NULL,
                    `totalCost` REAL NOT NULL,
                    `fuelType` TEXT NOT NULL,
                    `stationName` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `latitude` REAL,
                    `longitude` REAL,
                    FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // 2. Переносим данные из старой таблицы
            database.execSQL(
                """
                INSERT INTO `fuel_records_new` (
                    `id`, `vehicleId`, `date`, `mileage`, `fuelAmount`, `pricePerLiter`,
                    `totalCost`, `fuelType`, `stationName`, `notes`, `latitude`, `longitude`
                )
                SELECT `id`, `vehicleId`, `date`, `mileage`, `fuelAmount`, `pricePerLiter`,
                       `totalCost`, `fuelType`, `stationName`, `notes`, `latitude`, `longitude`
                FROM `fuel_records`
                """.trimIndent()
            )

            // 3. Удаляем старую таблицу
            database.execSQL("DROP TABLE `fuel_records` ")

            // 4. Переименовываем новую таблицу обратно в fuel_records
            database.execSQL("ALTER TABLE `fuel_records_new` RENAME TO `fuel_records` ")

            // 5. Создаем индекс для vehicleId (он обязателен для связи таблиц в Room)
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_fuel_records_vehicleId` ON `fuel_records` (`vehicleId`)"
            )
        }
    }
}
