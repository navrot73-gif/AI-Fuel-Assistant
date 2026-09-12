# Архитектура AI Fuel Assistant

> Синхронизировано с `GetBestStationsUseCase.kt` (ScoringWeights). При изменении формулы — обновляйте и этот файл.

## Структура

```
app/src/main/java/com/navrot/aifuelassistant/
├── ai/              # AiRouter (fallback), провайдеры (DeepSeek/Qwen/GigaChat/YandexGPT/HuggingFace), FuelAnalysisPromptBuilder
├── data/            # Room (DAO/Entity/DB), репозитории (Gas/FuelRecord/Vehicle/UserPrice), модели (GasStation, FuelPrice)
├── domain/          # use cases (GetBestStationsUseCase — скоринг), reliability (PriceReliabilityCalculator, FuelAvailabilityStatus)
├── di/              # Hilt-модули (AppModule, NetworkModule, ...)
├── features/
│   └── dashboard/   # Главный экран: AI-телеметрия + чат (DashboardViewModel + AiChatDelegate + DashboardMetricsDelegate + StationRecommendationDelegate)
├── geo/             # NominatimGeocodingProvider, GeoUtils (Haversine, hardcoded city fallback)
├── location/        # FusedLocationProvider wrapper
├── network/         # OkHttp interceptors, сетевые утилиты
├── ui/
│   ├── map/         # Карта (MapLibreView + osmdroid fallback), фильтры, карточка АЗС, маршруты (MapViewModel + 3 делегата)
│   ├── garage/      # Гараж, карточка авто, BrandPhotoMapper
│   ├── fuel/        # Экраны заправок
│   ├── reports/     # Отчёты, фильтры (7д/30д/90д/год), CSV-экспорт
│   ├── vehicles/    # Управление ТС
│   ├── common/      # Переиспользуемые UI-блоки
│   ├── components/  # Базовые компоненты
│   └── theme/       # Material 3 тема, цвета, типографика
└── util/            # Утилиты (Timber tree, форматирование)
```

## Поток данных

```
Screen (Compose)
   ↓ StateFlow
ViewModel (+ делегаты)
   ↓ UseCase
Repository
   ↓
Room DAO  /  Remote API (Benzonavt / Overpass / Russiabase через фиче-флаги)
```

## Единый источник координат (КРИТИЧНО)

**`StationRegistry` (`assets/stations.json`, 100+ АЗС) — единственный источник координат.**
- Внешние источники (Benzonavt / Russiabase / Overpass) могут обогащать ТОЛЬКО цены и статус наличия топлива.
- Ни один внешний источник **не имеет права** менять координаты или создавать автономные точки.
- Все внешние провайдеры изолированы фиче-флагами в DataStore: `src_benzonavt`, `src_russiabase`, `src_overpass` (по умолчанию **выключены**).

## AI-роутер

**Fallback chain (последовательный):**
```
DeepSeek → HuggingFace (Qwen) → GigaChat → YandexGPT
```
- `AiRouter.ask()` — кэш (TTL 15 мин для АЗС-запросов, 24 ч для общих) + per-provider timeout 8 с
- Кэш-ключ включает `lat`/`lon`/`prompt` (MD5)

**Прокси-принцип:** основной прокси — Cloudflare Worker `ai-fuel-proxy.navrot73.workers.dev`. Прокси сам делает fallback внутри, клиент **не дублирует** параллельную гонку.

## Навигация

- **Текущее состояние:** строковые маршруты в `ui/AppNavigation.kt`
- **План (ТЗ №3):** type-safe navigation через `kotlinx.serialization` (`androidx.navigation-compose 2.8+`)

## Скоринг АЗС

> Полная документация: [`docs/SCORING.md`](docs/SCORING.md)

### Формула (актуальная — соответствует `GetBestStationsUseCase.kt`)

```
score = fuelPrice * PRICE_WEIGHT
      + queueTime * QUEUE_WEIGHT
      + (100 - reliability) * RELIABILITY_WEIGHT
      + distanceKm * DISTANCE_WEIGHT
      + (NO_FUEL_PENALTY if availability == NO_FUEL else 0)
```

| Компонент | Вес | Смысл |
|-----------|-----|-------|
| `PRICE_WEIGHT` | `1.0` | Базовая цена за литр, руб. |
| `QUEUE_WEIGHT` | `0.5` | 1 минута очереди ≈ 0.5 ₽ штрафа |
| `RELIABILITY_WEIGHT` | `0.2` | 1 пункт ненадёжности ≈ 0.2 ₽ штрафа |
| `DISTANCE_WEIGHT` | `1.2` | 1 км от пользователя ≈ 1.2 ₽ штрафа |
| `NO_FUEL_PENALTY` | `1000.0` | Жёсткий штраф за `NO_FUEL` (станция не скрывается, но уходит в конец) |

**Чем меньше score — тем лучше АЗС.**

### Пример расчёта

АЗС с ценой 65 ₽/л, очередь 3 мин, надёжность 80%, расстояние 2 км, `AVAILABLE`:
```
score = 65*1.0 + 3*0.5 + (100-80)*0.2 + 2*1.2 + 0
      = 65 + 1.5 + 4 + 2.4
      = 72.9
```

Тот же набор, но `NO_FUEL`:
```
score = 72.9 + 1000 = 1072.9   ← уходит в конец списка, но не скрыта
```

### Где считается
- `domain/usecase/GetBestStationsUseCase.calculateScore()` — единая точка
- Используется в `data/GasStationRepository.getBestStations()` и в `features/dashboard/StationRecommendationDelegate`

## Карты и геолокация

- **MapLibre GL Android SDK 11.5.0** — основной векторный движок (OpenFreeMap → VersaTiles → OSM raster fallback)
- **osmdroid 6.1.17** — аварийный растровый фолбэк при сбоях MapLibre
- **FusedLocationProvider** — геолокация
- Маршруты строятся через **Cloudflare Worker `/route`** (OSRM под капотом)

## Хранение и шифрование

| Данные | Хранилище |
|--------|-----------|
| АЗС, ТС, заправки | Room |
| Пользовательские настройки (включая фиче-флаги) | Jetpack DataStore |
| История AI-чата | EncryptedSharedPreferences (AES256) |
| API-ключи | `local.properties` → BuildConfig (НЕ коммитится; в release пустые строки) |

## CI и метрики

- **Trufflehog scan** — проверка секретов при каждом PR
- **Android CI** — lint + assembleDebug на Ubuntu
- **MapDiagnosticsTracker** — тайминги эмиссии карты, размер реестра, активные источники (экспорт обязателен в каждом PR)

## Команды

```bash
./gradlew assembleDebug           # сборка
./gradlew testDebugUnitTest       # юнит-тесты
./gradlew lintDebug               # линтер
./gradlew connectedAndroidTest    # интеграционные на устройстве
```
