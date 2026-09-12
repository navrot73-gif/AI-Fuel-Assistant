# Промт для Jules: Type-safe Navigation (ТЗ №3 из ARCHITECTURE.md)

> Скопируй этот блок и вставь в Jules как есть. Не редактируй правила.

---

## Задача

Заменить строковые маршруты в `ui/AppNavigation.kt` на type-safe navigation через `kotlinx.serialization` (требует `androidx.navigation-compose 2.8+`). В `ARCHITECTURE.md` помечено как «ТЗ №3 — план».

## Контекст

- Проект: AI Fuel Assistant (`com.navrot.aifuelassistant`)
- Текущая навигация: строковые маршруты в `ui/AppNavigation.kt` (~10-15 экранов: map, garage, dashboard, fuel, vehicles, reports, settings, ...)
- Стек: Jetpack Navigation Compose 2.8.0 (уже в libs.versions.toml)
- Текущая версия kotlin-serialization: 1.6.3 (уже подключен)
- Kotlin 2.0.21 (поддерживает `@Serializable` на data class)

## НЕ ТРОГАТЬ

- `GasStationRepository`, `MapViewModel`, `DashboardViewModel` — менять только публичный API навигации
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (только если чего-то реально не хватает — тогда согласование через navrot73-gif)
- `MapDiagnosticsTracker`, схемы Room, AI-провайдеры
- Существующие тесты не должны падать. Если меняется публичный API — обновить вызывающие места и тесты
- НЕ удалять старые строковые маршруты, пока не переключены все вызовы на новые

## Что сделать

### 1. Создать файл `ui/NavigationRoutes.kt`

```kotlin
package com.navrot.aifuelassistant.ui

import kotlinx.serialization.Serializable

sealed interface NavRoute {
    @Serializable data object Map : NavRoute
    @Serializable data object Dashboard : NavRoute
    @Serializable data object Garage : NavRoute
    @Serializable data object Vehicles : NavRoute
    @Serializable data object Reports : NavRoute
    @Serializable data object Settings : NavRoute
    @Serializable data class FuelForm(
        val vehicleId: Long? = null,
        val stationId: Int? = null
    ) : NavRoute
    @Serializable data class VehicleDetail(val vehicleId: Long) : NavRoute
    @Serializable data class StationDetail(
        val stationId: Int,
        val fuelType: String? = null
    ) : NavRoute
}
```

> Допили список под реальные экраны проекта. Сначала прочитай `AppNavigation.kt` и собери ВСЕ существующие строковые маршруты оттуда. Для каждого маршрута с параметрами сделай `@Serializable data class`, без параметров — `@Serializable data object`.

### 2. Обновить `ui/AppNavigation.kt`

- Использовать `composable<NavRoute.Map> { MapScreen() }` вместо `composable("map") { MapScreen() }`
- Для экранов с параметрами: `composable<NavRoute.FuelForm> { entry -> val args = entry.toRoute<NavRoute.FuelForm>(); FuelFormScreen(args.vehicleId, args.stationId) }`
- Импорт: `import androidx.navigation.toRoute`

### 3. Обновить все вызовы `navController.navigate("map")` на `navController.navigate(NavRoute.Map)`

- Найти все места через grep: `rg "navigate\(\"" --type kotlin`
- Заменить на `navigate(NavRoute.X)`

### 4. Удалить старые строковые маршруты

После того как все вызовы переведены — убрать старые `composable("string_route") { ... }` блоки.

### 5. Тесты

- Добавить unit-тест в `app/src/test/java/com/navrot/aifuelassistant/ui/NavigationRoutesTest.kt`:
  - Проверить, что все `NavRoute` подклассы `Serializable` (через рефлексию или просто компиляцию)
  - Smoke-test: создать `NavRoute.FuelForm(vehicleId = 1, stationId = 5)`, сериализовать в JSON, десериализовать, проверить равенство

### 6. Документация

- Обновить `ARCHITECTURE.md` раздел «Навигация» — убрать «План», написать «Реализовано через kotlinx.serialization»
- Добавить запись в `CHANGELOG.md` в `[Unreleased]` секцию

## Ожидаемые проверки

```bash
./gradlew assembleDebug           # BUILD SUCCESSFUL
./gradlew lintDebug               # 0 errors
./gradlew testDebugUnitTest       # все зелёные, новый NavigationRoutesTest проходит
```

## Диагностика для PR

В описании PR приложить:
1. `git log --oneline -5` (показать коммиты)
2. `git push origin <branch>` (показать в отчёте)
3. Размер патча: `git diff --stat HEAD~1`
4. Подтверждение: «Все экраны переведены на type-safe navigation, старые строковые маршруты удалены, новый NavigationRoutesTest зелёный»

## Definition of Done

- [ ] `ui/NavigationRoutes.kt` создан со всеми экранами проекта как `@Serializable`
- [ ] `ui/AppNavigation.kt` переписан на `composable<NavRoute.X>` (НЕ `composable("string")`)
- [ ] Все вызовы `navigate("...")` заменены на `navigate(NavRoute.X)`
- [ ] Старые строковые маршруты удалены
- [ ] `NavigationRoutesTest` добавлен и проходит
- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` — всё зелёное
- [ ] `ARCHITECTURE.md` обновлён (раздел «Навигация»)
- [ ] `CHANGELOG.md` обновлён
- [ ] PR открыт через Jules, в описании есть git log и результаты проверок

---

**Когда сборка зелёная → создай PR от ветки `feature/type-safe-navigation` в `main`.**
