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
    val id: Long,
    val name: String,
    val brand: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val fuelTypes: List<FuelType>,  // НЕ fuelPrices!
    val queueTime: Int,
    val reliability: Int
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
