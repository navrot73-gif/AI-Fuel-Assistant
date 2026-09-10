# 🚗 AI Fuel Assistant — руководство по проекту

> Обновлено: 30.08.2026. Этот файл — живой контекст проекта.
> В начале нового чата с ИИ отправь ему раздел «🤖 Промпт для восстановления контекста» — и работа продолжится без раскачки.

## 📱 О проекте
Android-приложение для поиска АЗС, сравнения цен и AI-аналитики расхода топлива.
Пользователь видит карту с АЗС, получает AI-подбор лучшей станции, строит маршрут,
ведёт гараж (авто, заправки, ТО) и получает AI-рекомендации по расходу.

## 📌 Архитектурные правила и процесс разработки

### 1. Единый источник координат
- **Реестр (`StationRegistry`) — единственный источник координат АЗС.**
- Базовый детерминированный слой станций загружается из `assets/stations.json` (100+ АЗС).
- Внешние источники (Benzonavt, Russiabase, Overpass и др.) могут обогащать ТОЛЬКО цены и статус наличия топлива. Ни один внешний источник не имеет права изменять координаты или создавать автономные точки на карте.

### 2. Внешние источники и фиче-флаги
- Все внешние провайдеры обогащения закрыты фиче-флагами в DataStore: `src_benzonavt`, `src_russiabase`, `src_overpass` (по умолчанию `false`).
- Внешние запросы выполняются ТОЛЬКО при явно включенном флаге.
- Любые изменения или подключение внешних источников требуют **device-доказательств** (скриншоты, логи или успешные DeviceScenarioTest).

### 3. Требования к PR (диагностика и контрольные показатели)
- **PR без экспорта диагностики и ожидаемых значений НЕ принимается.**
- В описании каждого Pull Request обязателен текстовый экспорт из `MapDiagnosticsTracker.exportDiagnosticsText()` с указанием контрольных значений (timing, count, active sources).

## 🛠 Стек
- Kotlin, Jetpack Compose, Material 3 (тёмная тема поддерживается)
- MVVM + StateFlow, Hilt (DI), Room
- Карты: MapLibre GL Android SDK (основной векторный движок) + osmdroid (офлайн/аварийный фолбэк); геолокация: FusedLocationProvider
- AI-роутер с fallback: DeepSeek → HuggingFace (Qwen) → GigaChat → YandexGPT
- Сеть: OkHttp (единый клиент через DI, таймауты 30 с; для роутинга 8 с)
- Хранение настроек: Jetpack DataStore; шифрование истории чата: EncryptedSharedPreferences (AES256)

## 🏁 Статус Фазы 1: Завершена ✅
Фаза 1 полностью завершена. Все контрольные гейты (T1–T8) пройдены и покрыты автоматизированными тестами (`SingleRegistryContractTest`, `DeviceScenarioTest`, `SverdlovskyTest`).

### Критерии выхода в Фазу 2:
1. **100% зелёные гейты (T1–T8):** Тесты на целостность единого реестра, корректность координат и таймингов эмиссии пройдены.
2. **Неприкосновенность координат:** Недопущение смещения координат или появление фантомных точек (`mergeConflicts = 0`).
3. **Изоляция источников под флагами:** Внешние провайдеры по умолчанию отключены (`src_* = false`).
4. **Диагностические доказательства:** Поставка каждого изменения сопровождается экспортом `MapDiagnosticsTracker`.

## 🗂 Структура
- `ai/` — AI-провайдеры, AiRouter (fallback), FuelAnalysisPromptBuilder
- `data/` — GasStationRepository (фасад + компоненты StationLoader, StationCache, StationPriceApplier, StationFilterAndSorter, StationJsonParser, OverpassFuelProvider), Vehicle/FuelRecord репозитории, Room
- `features/dashboard/` — экран AI-телеметрии расхода и чата (DashboardViewModel + делегаты AiChatDelegate, DashboardMetricsDelegate, StationRecommendationDelegate)
- `ui/map/` — карта (MapLibreView + osmdroid fallback), фильтры bottom sheet, карточка АЗС, маршруты (MapViewModel + делегаты MapSearchDelegate, MapRouteDelegate, MapFilterDelegate), диагностический диалог
- `ui/garage/` — гараж, карточка авто, BrandPhotoMapper
- `domain/` — NearestStationFinder, StationQueryFacade
- `geo/` — NominatimGeocodingProvider (поиск адреса с кэшем 30 мин), GeoUtils (Haversine). Маршруты строятся через Cloudflare Worker `/route` (OSRM под капотом).
- `di/` — AppModule (Room, репозитории, OkHttpClient, AiRouter)

## 🔑 API-ключи (local.properties — НЕ коммитится!)
- DEEPSEEK_API_KEY, HUGGINGFACE_TOKEN
- GIGACHAT_AUTHORIZATION_KEY (или CLIENT_ID + CLIENT_SECRET)
- YANDEX_API_KEY + YANDEX_FOLDER_ID

## 🧪 Тестирование и команды
- Юнит-тесты: `./gradlew testDebugUnitTest`
- Сборка: `./gradlew assembleDebug`
- Линтер: `./gradlew lintDebug`
- Интеграционные тесты на устройстве/эмуляторе: `./gradlew connectedAndroidTest`

## 🤖 Промпт для восстановления контекста в новом чате
«Привет! Продолжаем проект AI Fuel Assistant (Kotlin, Compose, Hilt, MapLibre GL, OSMDroid, MVVM).
Контекст и правила процесса лежат в CONTEXT.md, AGENTS.md и PROJECT_GUIDE.md.
Текущее состояние: Фаза 1 успешно завершена ✅. Реестр StationRegistry является единственным источником координат. Все внешние источники закрыты фиче-флагами src_benzonavt, src_russiabase, src_overpass (default=false). Все PR обязаны содержать экспорт диагностики MapDiagnosticsTracker и ожидаемые значения контрольных метрик. Код без одобрения и проверок НЕ трогаем.»
