package com.navrot.aifuelassistant.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CsvBackupManagerTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var fuelRecordRepository: FuelRecordRepository
    private lateinit var csvBackupManager: CsvBackupManager

    private val vehiclesList = mutableListOf<VehicleEntity>()
    private val recordsList = mutableListOf<FuelRecordEntity>()

    @Before
    fun setup() {
        context = mock()
        contentResolver = mock()
        vehicleRepository = mock()
        fuelRecordRepository = mock()

        vehiclesList.clear()
        recordsList.clear()

        whenever(context.contentResolver).thenReturn(contentResolver)
        whenever(vehicleRepository.getAllVehicles()).thenAnswer { MutableStateFlow(vehiclesList.toList()) }
        whenever(fuelRecordRepository.getAll()).thenAnswer { MutableStateFlow(recordsList.toList()) }

        runTest {
            doAnswer { invocation ->
                val v = invocation.getArgument<VehicleEntity>(0)
                vehiclesList.add(v)
                Unit
            }.whenever(vehicleRepository).insertVehicle(any())

            doAnswer { invocation ->
                val f = invocation.getArgument<FuelRecordEntity>(0)
                recordsList.add(f)
                Unit
            }.whenever(fuelRecordRepository).insert(any())
        }

        csvBackupManager = CsvBackupManager(context, fuelRecordRepository, vehicleRepository)
    }

    @Test
    fun roundTrip_exportAndImport_restoresIdenticalData() = runTest {
        val uri = mock<Uri>()
        val outputStream = ByteArrayOutputStream()

        whenever(contentResolver.openOutputStream(eq(uri), eq("w"))).thenReturn(outputStream)

        val initialVehicles = listOf(
            VehicleEntity(1L, "Мое Авто 1", "Лада", "Веста", 2021, "АИ-95", 55.0, 45000.0),
            VehicleEntity(2L, "Мое Авто 2", "Toyota", "Camry", 2020, "АИ-98", 60.0, 80000.0)
        )
        val initialRecords = listOf(
            FuelRecordEntity(10L, 1L, 1700000000000L, 44000.0, 40.0, 0.0, 2100.0, "АИ-95", "Газпромнефть №10"),
            FuelRecordEntity(11L, 1L, 1700500000000L, 44500.0, 42.0, 0.0, 2226.0, "АИ-95", "Лукойл Центр"),
            FuelRecordEntity(12L, 2L, 1701000000000L, 79500.0, 50.0, 0.0, 3000.0, "АИ-98", "Роснефть")
        )

        vehiclesList.addAll(initialVehicles)
        recordsList.addAll(initialRecords)

        val exportedCount = csvBackupManager.exportToUri(uri)
        assertEquals(3, exportedCount)

        val exportedBytes = outputStream.toByteArray()

        whenever(contentResolver.openInputStream(eq(uri))).doAnswer {
            ByteArrayInputStream(exportedBytes)
        }

        vehiclesList.clear()
        recordsList.clear()

        val importResult = csvBackupManager.importFromUri(uri)

        val importedVehicles = vehiclesList.toList()
        val importedRecords = recordsList.toList()

        assertEquals(3, importResult.imported)
        assertEquals(0, importResult.skipped)

        assertEquals(2, importedVehicles.size)
        assertEquals(3, importedRecords.size)

        assertEquals(initialVehicles[0], importedVehicles[0])
        assertEquals(initialVehicles[1], importedVehicles[1])

        assertEquals(initialRecords[0], importedRecords[0])
        assertEquals(initialRecords[1], importedRecords[1])
        assertEquals(initialRecords[2], importedRecords[2])
    }

    @Test
    fun exportAndImport_specialCharactersAndCyrillic_escapesAndRestoresCorrectly() = runTest {
        val uri = mock<Uri>()
        val outputStream = ByteArrayOutputStream()

        whenever(contentResolver.openOutputStream(eq(uri), eq("w"))).thenReturn(outputStream)

        val vehicleWithSpecial = VehicleEntity(
            id = 5L,
            name = "Машина; \"Супер\"\nВторая строка",
            brand = "Бренд; с точки запятой",
            model = "Модель \"Кавычки\"",
            year = 2022,
            fuelType = "ДТ",
            tankCapacity = 70.0,
            currentMileage = 12000.0
        )
        val recordWithSpecial = FuelRecordEntity(
            id = 50L,
            vehicleId = 5L,
            date = 1702000000000L,
            mileage = 11000.0,
            fuelAmount = 60.0,
            pricePerLiter = 0.0,
            totalCost = 3600.0,
            fuelType = "ДТ Premium",
            stationName = "АЗС \"Лукойл; №99\"\nУлица Ленина"
        )

        vehiclesList.add(vehicleWithSpecial)
        recordsList.add(recordWithSpecial)

        csvBackupManager.exportToUri(uri)

        val exportedBytes = outputStream.toByteArray()
        whenever(contentResolver.openInputStream(eq(uri))).doAnswer {
            ByteArrayInputStream(exportedBytes)
        }

        vehiclesList.clear()
        recordsList.clear()

        val result = csvBackupManager.importFromUri(uri)

        val importedVehicle = vehiclesList.first()
        val importedRecord = recordsList.first()

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)

        assertEquals(vehicleWithSpecial, importedVehicle)
        assertEquals(recordWithSpecial, importedRecord)
    }

    @Test
    fun importEmptyFile_returnsZeroResultWithoutExceptions() = runTest {
        val uri = mock<Uri>()
        whenever(contentResolver.openInputStream(eq(uri))).doAnswer {
            ByteArrayInputStream(ByteArray(0))
        }

        val result = csvBackupManager.importFromUri(uri)

        assertEquals(0, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(0, vehiclesList.size)
        assertEquals(0, recordsList.size)
    }

    @Test
    fun importFileWithMalformedLine_skipsBadLineAndImportsValidOnes() = runTest {
        val uri = mock<Uri>()
        val content = "# AI-FUEL-BACKUP v1\n" +
                "# VEHICLES\n" +
                "id;name;brand;model;year;fuelType;tankCapacity;currentMileage\n" +
                "1;Lada;Vesta;Sedan;2020;АИ-95;50.0;30000.0\n" +
                "bad_vehicle_line_insufficient_columns\n" +
                "# FILLS\n" +
                "id;vehicleId;date;mileage;fuelAmount;totalCost;fuelType;stationName\n" +
                "10;1;1700000000000;29000.0;40.0;2000.0;АИ-95;Gazprom\n" +
                "corrupted_fill_line\n" +
                "11;1;1700100000000;30000.0;45.0;2250.0;АИ-95;Lukoil\n"

        whenever(contentResolver.openInputStream(eq(uri))).doAnswer {
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }

        val result = csvBackupManager.importFromUri(uri)

        assertEquals(2, result.imported)
        assertEquals(2, result.skipped)
        assertEquals(1, vehiclesList.size)
        assertEquals(2, recordsList.size)
    }
}
