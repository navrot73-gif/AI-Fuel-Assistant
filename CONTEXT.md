# 📘 AI FUEL ASSISTANT — ПАСПОРТ ПРОЕКТА (28.08.2026)

## 🏗 Что это
Android-приложение «Где бензин?» (Челябинск): карта АЗС (osmdroid), цены из 4 источников, 
AI-помощник (голос+чат), маршруты OSRM, гараж с заправками и метриками.
- **Репозиторий:** github.com/navrot73-gif/AI-Fuel-Assistant (публичный, main)
- **Пакет:** com.navrot.aifuelassistant
- **Стек:** Kotlin, Compose, Hilt, MVVM, Room, osmdroid, Coroutines/Flow, Timber, JaCoCo
- **Worker:** Cloudflare `ai-fuel-proxy` (navrot73.workers.dev)

## 🚨 БЕЗОПАСНОСТЬ — ВСЕ CRITICAL ЗАКРЫТЫ ✅
- Скомпрометированный токен `fuel-2026-secret` — УДАЛЁН из кода и ротирован
- Деструктивная миграция `.fallbackToDestructiveMigration()` — УДАЛЕНА
- API-ключи провайдеров в release-сборке НЕ инжектятся в BuildConfig
- Валидация: сборка падает без PROXY_TOKEN

## 🎉 ВСЕ 5 БЭКЛОГОВ ИЗ ПАСПОРТА ЗАКРЫТЫ ✅
**Бэклог №1 (Отчёты):** бэкенд фильтрации, экран отчётов с фильтрами 
  (7д/30д/90д/год), экспорт в CSV + share intent
**Бэклог №2 (Свайпы):** HorizontalPager Карта↔AI↔Гараж
**Бэклог №3 (Карточка АЗС):** индикатор надёжности цены, «Сообщить о цене»
**Бэклог №4 (Оффлайн-режим):** индикатор офлайн-статуса, обработка ошибок 
  с сообщениями, персистентный кэш тайлов карты (SqliteArchiveTileWriter)
**Бэклог №5 (Гараж):** фото авто (поле + миграция, стоковые по бренду, 
  загрузка через системный пикер)

## ✅ ЧТО ЗАКРЫТО ЗА СЕССИИ 27-28.08.2026 (22 задачи)
**Этап 0 (Безопасность):** удаление токена, деструктивной миграции, защита ключей
**Этап 1 (Стабильность):** миграционные тесты Room, типизированные `catch`, 
  покрытие JaCoCo
**Все 5 бэклогов** (см. выше)
**Фикс CI:** эмулятор стабилизирован, добавлен non-blocking quality gate
**Техдолг:** тесты на CsvBackup + FuelApi, переход на Timber, 
  убрано дублирование скоринга

## 🤖 Агенты (ВАЖНО!)
- **Jules (jules.google)** — ЕДИНСТВЕННЫЙ, кто реально пушит в GitHub и открывает PR
- Стиль промтов: точечные фиксы + «НЕ ТРОГАТЬ: …» + тесты + «Сборка зелёная → PR»

## 📋 ОСТАЛОСЬ ИЗ АУДИТОВ (не блокирует релиз)
**Этап 2 (Тесты и CI):** 2.4 Hilt-тесты, 2.5 покрытие detectIntent(), 
  2.6 lint quality gate
**Этап 3 (Качество):** 3.5 перегенерировать lint-baseline, 
  3.6 разбить GasStationRepository, 3.7 удалить DemoFuelStations
**Этап 4 (Архитектура):** WorkManager для фонового обновления, 
  убрать "chelyabinsk", DataStore вместо SharedPreferences
**Этап 5 (Долгосрочно):** AI Router Race (параллельный запрос провайдерам)
**Этап 6 (Зависимости):** обновление зависимостей, compileSdk 35

## 📒 СЛЕДУЮЩИЕ ШАГИ (приоритет)
1. 🔧 Рефакторинг: разбить GasStationRepository, MapViewModel, DashboardViewModel
2. 📡 WorkManager для фонового обновления цен
3. 🗺 Убрать "chelyabinsk", подключить NominatimGeocodingProvider
4. 📦 Обновить зависимости (Compose, Kotlin, Room)

## ⚙️ Команды
```powershell
cd C:\Users\Дима\Desktop\AI-Fuel-Assistant
git pull origin main
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat jacocoTestReport   # отчёт о покрытии
