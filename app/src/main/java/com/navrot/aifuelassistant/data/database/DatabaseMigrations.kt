package com.navrot.aifuelassistant.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    /**
     * Миграция 1 → 2: удаление ForeignKey `fuel_records.vehicleId → vehicles.id`.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
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
                    `longitude` REAL
                )
                """.trimIndent()
            )

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

            database.execSQL("DROP TABLE `fuel_records`")
            database.execSQL("ALTER TABLE `fuel_records_new` RENAME TO `fuel_records`")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
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

            database.execSQL("DROP TABLE `fuel_records` ")
            database.execSQL("ALTER TABLE `fuel_records_new` RENAME TO `fuel_records` ")
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_fuel_records_vehicleId` ON `fuel_records` (`vehicleId`)"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `vehicles` ADD COLUMN `photo_url` TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `fuel_records` ADD COLUMN `stationId` INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE `fuel_records` ADD COLUMN `fullTank` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Полный список зарегистрированных миграций. Используется в [com.navrot.aifuelassistant.di.AppModule.provideDatabase]. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
