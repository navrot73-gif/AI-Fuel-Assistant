# 📘 AI FUEL ASSISTANT — ПАСПОРТ ПРОЕКТА (30.08.2026)

## 🏗 Что это
Android-приложение «Где бензин?» (Россия): карта АЗС (osmdroid), цены из 4+ источников, 
AI-помощник (голос+чат), маршруты OSRM, гараж с заправками, метриками и фото.
- **Репозиторий:** github.com/navrot73-gif/AI-Fuel-Assistant (публичный, main)
- **Пакет:** com.navrot.aifuelassistant
- **Стек:** Kotlin, Compose, Hilt, MVVM, Room, osmdroid, Coroutines/Flow, Timber, 
  Jetpack DataStore, EncryptedSharedPreferences, JaCoCo
- **Worker:** Cloudflare `ai-fuel-proxy` (navrot73.workers.dev)

## 🚨 БЕЗОПАСНОСТЬ — ВСЕ CRITICAL ЗАКРЫТЫ ✅
- Скомпрометированный токен `fuel-2026-secret` — УДАЛЁН из кода и ротирован
- Деструктивная миграция `.fallbackToDestructiveMigration()` — УДАЛЕНА
- API-ключи провайдеров в release-сборке НЕ инжектятся в BuildConfig
- История AI-чата шифруется через EncryptedSharedPreferences (AES256)
- Валидация сборки: без PROXY_TOKEN не собирается

## 🎉 ВСЕ 5 БЭКЛОГОВ ИЗ ПАСПОРТА ЗАКРЫТЫ ✅
- **№1 Отчёты:** фильтры (7д/30д/90д/год), экран отчётов, экспорт в CSV + share
- **№2 Свайпы:** HorizontalPager Карта↔AI↔Гараж
- **№3 Карточка АЗС:** индикатор надёжности цены + «Сообщить о цене»
- **№4 Оффлайн-режим:** индикатор, понятные ошибки, персистентный кэш тайлов карты
- **№5 Гараж v2:** фото авто (стоковые по бренду + загрузка через пикер)

## 🔨 РЕФАКТОРИНГ GOD-ОБЪЕКТОВ ЗАКРЫТ ✅
- `GasStationRepository` (461 стр.) → 4 компонента + фасад
- `MapViewModel` (571 стр.) → 3 делегата + фасад
- `DashboardViewModel` (571 стр.) → 3 делегата + фасад

## 📋 ПЛАН «ПО АЛФАВИТУ» ЗАКРЫТ ✅ (PR #130-134)
- **A.** Удалён мёртвый demo-код `domain/fuel/` (PR #130)
- **B.** Добавлены Hilt-тесты DI-графа AppModule (PR #131)
- **C.** Убран hardcoded "chelyabinsk", динамическое определение города через 
  Nominatim с кэшем 30 мин и ручным пикером как фолбэк (PR #132)
- **D.** Миграция настроек с SharedPreferences на Jetpack DataStore (PR #133)
- **E.** Шифрование истории чата через EncryptedSharedPreferences AES256 (PR #134)

## ✅ ЗАКРЫТЫЙ ТЕХДОЛГ
- Миграционные тесты Room 1→2→3
- Тесты CsvBackupManager (round-trip) + FuelApiImpl (MockWebServer)
- Переход с android.util.Log на Timber
- Убрано дублирование формулы скоринга
- Стабилизирован CI-эмулятор (api-level 34, non-blocking gate)

## 🤖 Агенты
- **Jules (jules.google)** — ЕДИНСТВЕННЫЙ, кто пушит в GitHub и открывает PR
- **Правило:** задача НЕ считается закрытой, пока нет номера смерженного PR
- Стиль промтов: точечные фиксы + «НЕ ТРОГАТЬ» + тесты + финальный блок 
  про обязательный PR + «Сборка зелёная → создай PR»

## 📋 ЧТО ОСТАЛОСЬ (опционально, не блокирует)
- Обновление зависимостей (Compose BOM, Kotlin, Room, AGP)
- Дополнительное тестовое покрытие (detectIntent и др.)
- AI Router Race (параллельный запрос провайдерам)

## ⚙️ Команды
```powershell
cd C:\projects\AI-Fuel-Assistant
git pull origin main
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
