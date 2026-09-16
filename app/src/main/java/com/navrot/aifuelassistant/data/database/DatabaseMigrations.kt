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

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `prediction_outcomes` (
                    `predictionId` TEXT NOT NULL,
                    `vehicleId` INTEGER NOT NULL,
                    `predictedValue` REAL NOT NULL,
                    `actualValue` REAL NOT NULL,
                    `absoluteError` REAL NOT NULL,
                    `relativeError` REAL NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`predictionId`)
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recommendation_feedbacks` (
                    `id` TEXT NOT NULL,
                    `recommendationId` TEXT NOT NULL,
                    `recommendedStationId` INTEGER NOT NULL,
                    `chosenStationId` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `routeStarted` INTEGER NOT NULL,
                    `routeCompleted` INTEGER NOT NULL,
                    `refuelCompleted` INTEGER NOT NULL,
                    `signal` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `personal_model_metadata` (
                    `vehicleId` INTEGER NOT NULL,
                    `modelVersion` INTEGER NOT NULL,
                    `trainedSamples` INTEGER NOT NULL,
                    `biasAdjustment` REAL NOT NULL,
                    `lastUpdatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`vehicleId`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `fuelType` TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `action` TEXT NOT NULL DEFAULT 'VIEWED'")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `outcome` TEXT NOT NULL DEFAULT 'UNKNOWN'")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `predictedAvailability` TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `actualAvailability` TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `predictedPrice` REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `actualPrice` REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `predictedQueue` INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `actualQueue` INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `dataConfidence` TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `userConfirmed` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'USER_CONFIRMED'")
            database.execSQL("ALTER TABLE `recommendation_feedbacks` ADD COLUMN `notes` TEXT DEFAULT NULL")
        }
    }

    /** Полный список зарегистрированных миграций. Используется в [com.navrot.aifuelassistant.di.AppModule.provideDatabase]. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7
    )
}
