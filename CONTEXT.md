# AI Fuel Assistant — Контекст проекта

## Архитектура

Android-приложение для управления топливом (Kotlin, Jetpack Compose, Hilt, Room, OSMDroid).

### Основные модули

- **ui/map/** — карта с АЗС, маршруты, рекомендации
- **features/dashboard/** — AI-экран с аналитикой
- **ui/fuel/** — управление заправками
- **ui/vehicles/** — гараж (список автомобилей)
- **ai/** — AI-роутер для запросов к LLM
- **data/** — Room БД, репозитории, модели

### Ключевые модели данных

```kotlin
// Газовая станция
data class GasStation(
    val id: Int,
    val name: String,
    val brand: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val fuelTypes: List<FuelPrice>,   // НЕ FuelType!
    val queueTime: Int,
    val reliability: Int,
    val dataSources: Set<FuelDataSource> = emptySet(),
    val updatedAt: Long = 0L,
    val confidence: Int = 0,
    val photoEvidence: List<PhotoEvidence> = emptyList(),
    val monumentPhotoUrl: String? = null,
    val entrancePhotoUrl: String? = null
)

data class FuelPrice(
    val type: String,
    val price: Double,
    val available: Boolean,
    val source: FuelDataSource = FuelDataSource.DEMO,
    val updatedAt: Long = 0L,
    val confidence: Int = 0,
    val photoEvidence: PhotoEvidence? = null
)

// Автомобиль
@Entity
data class VehicleEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val fuelType: String,
    val tankCapacity: Double
)

// Заправка
@Entity
data class FuelRecordEntity(
    @PrimaryKey val id: Long,
    val vehicleId: Long,
    val date: Long,
    val mileage: Double,
    val liters: Double,
    val totalCost: Double,
    val fuelType: String
)
