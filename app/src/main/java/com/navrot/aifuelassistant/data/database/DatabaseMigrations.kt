package com.navrot.aifuelassistant.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    /**
     * Миграция 1 → 2: удаление ForeignKey `fuel_records.vehicleId → vehicles.id`.
     *
     * В схеме v1 у `fuel_records` был FK с `ON DELETE CASCADE`. В v2 было решено
     * убрать его (видимо, чтобы упростить вставку записей без проверки родителя).
     * Та же структура полей, только снимается FK-ограничение.
     *
     * SQLite не умеет удалять FK через ALTER TABLE — нужно пересоздать таблицу:
     *   1) создать новую `fuel_records_new` без FK
     *   2) скопировать все строки
     *   3) дропнуть старую
     *   4) переименовать новую в `fuel_records`
     *
     * Порядок столбцов в `CREATE TABLE` совпадает со схемой v1/v2 (см.
     * app/schemas/.../{1,2}.json — идентичны по полям).
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

    /** Полный список зарегистрированных миграций. Используется в [com.navrot.aifuelassistant.di.AppModule.provideDatabase]. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}

