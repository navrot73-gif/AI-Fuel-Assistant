# Changelog

Все значимые изменения в проекте документируются здесь. Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/), проект придерживается [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]

### Added
- Type-safe navigation routes via `kotlinx.serialization` in `ui/NavigationRoutes.kt` (`NavRoute` sealed interface).
- Unit tests for navigation route serialization and deserialization in `NavigationRoutesTest.kt`.
- Документация: `docs/SCORING.md` с полной формулой скоринга и примерами
- Документация: синхронизирован `ARCHITECTURE.md` с реальной формулой из `GetBestStationsUseCase.kt`

### Changed
- Refactored `AppNavigation.kt` and `DashboardScreen.kt` from legacy string-based navigation routes to type-safe Compose Navigation 2.8+ API (`composable<NavRoute.X>` and `navController.navigate(NavRoute.X)`).
- Updated `ARCHITECTURE.md` to reflect completed type-safe navigation implementation.
- Производительность: устранён двойной расчёт Haversine в `StationFilterAndSorterImpl.getStationsNearLocation`
- Производительность: добавлен кеш `PriceReliabilityCalculator` (инвалидация по `stationId + fuelType + updatedAt`)

### Removed
- `app/README.md` (дубль корневого `README.md`, содержал устаревший список AI-провайдеров без Qwen)
- `classes.jar` (build-артефакт, случайно закоммиченный в репозиторий)
- Дополнительные конструкторы в `GasStationRepository` (помечены как устаревшие)

### Security
- `.gitignore` ужесточён: добавлены правила для `*.jar`, `*.aar`, `classes.jar`

## [1.0.0] — Phase 1 Complete (2026-08-30)

### Added
- Phase 1 закрыта: `StationRegistry` как единственный источник координат
- 3-этапная эмиссия карты: cache/assets → registry → enrichment (бюджет ≤6 с)
- Автоматический fallback карт: MapLibre (OpenFreeMap → VersaTiles → OSM raster) → osmdroid
- `MapDiagnosticsTracker` с экспортом таймингов и метрик реестра
- Завершены 5 бэклогов: отчёты (CSV + share), HorizontalPager (Карта↔AI↔Гараж), карточка АЗС (надёжность + «Сообщить о цене»), оффлайн-индикатор, Гараж v2 (фото)
- Рефакторинг god-objects: `GasStationRepository` → 4 компонента + фасад, `MapViewModel` → 3 делегата, `DashboardViewModel` → 3 делегата

### Security
- Скомпрометированный токен `fuel-2026-secret` удалён и ротирован
- Деструктивная миграция Room `.fallbackToDestructiveMigration()` удалена
- API-ключи провайдеров НЕ инжектятся в release BuildConfig
- История AI-чата шифруется через EncryptedSharedPreferences (AES256)

### Tests
- 100% зелёные гейты T1–T8 (SingleRegistryContractTest, DeviceScenarioTest, SverdlovskyTest)
- Миграционные тесты Room 1→2→3
- CsvBackupManager round-trip тесты
- Hilt-тесты DI-графа AppModule

## [0.x] — до Phase 1

- Первоначальная разработка (см. git log)
