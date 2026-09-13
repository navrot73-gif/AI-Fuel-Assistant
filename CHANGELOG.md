# Changelog

Все значимые изменения в проекте документируются здесь. Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/), проект придерживается [Semantic Versioning](https://semver.org/lang/ru/).

## [Unreleased]

### Added
- Документация: `docs/SCORING.md` с полной формулой скоринга и примерами
- Документация: синхронизирован `ARCHITECTURE.md` с реальной формулой из `GetBestStationsUseCase.kt`

### Changed
- Производительность: устранён двойной расчёт Haversine в `StationFilterAndSorterImpl.getStationsNearLocation`
- Производительность: добавлен кеш `PriceReliabilityCalculator` (инвалидация по `stationId + fuelType + updatedAt`)

### Fixed
- **Карта (P0):** корневая причина «undead map» — `MapLibre.setStyle()` визуально удалял маркеры, но трекеры `activeStationMarkers`/`markerStationMap` не сбрасывались, из-за чего `MarkerDiffCalculator` пропускал повторное добавление пинов после каждого fallback тайлов. Добавлен `resetMarkerTrackers()` в `MapLibreView`.
- **Карта (P0):** 6-секундный таймер ошибочно трактовал оффлайн (`tilesLoadedCount==0`) как аварию и переключал источник. Теперь переключение происходит только при явном `onDidFailLoadingMap`. Таймаут увеличен до 10 с.
- **Карта (P0):** `map_style_local.json` — добавлены `name` и `metadata` для spec-совместимости, фон смягчён с `#f5f5f5` до `#E8EAEC`.
- **Карта (P2):** `MapLibreView` теперь принимает `focusPoint` — паритет с `OsmMapView` (синий пин найденного адреса).
- **Документация:** `CONTEXT.md`, `ARCHITECTURE.md`, `PROJECT_GUIDE.md`, `AGENTS.md` синхронизированы с реальным дефолтом `src_benzonavt=true` (включён в PR #176, но не отражён в доках).

### Removed
- `app/README.md` (дубль корневого `README.md`, содержал устаревший список AI-провайдеров без Qwen) — **фактически удалён** (CHANGELOG обещал удаление в `da0b926`, но файл оставался в репозитории до этого PR)
- `classes.jar` (build-артефакт, случайно закоммиченный в `c09ee8b`) — **фактически удалён** (та же история)
- Дополнительные конструкторы в `GasStationRepository` (помечены как устаревшие)

### Security
- `.gitignore` ужесточён: добавлены правила для `*.jar`, `*.aar` с regression-guard комментарием, ссылающимся на коммит `c09ee8b` (предотвращает повторное попадание `classes.jar`)

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
