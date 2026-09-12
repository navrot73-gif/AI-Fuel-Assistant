# Патчи для AI Fuel Assistant

Подготовлены Mavis'ом (MiniMax Agent) на основе аудита проекта от 2026-09-12.

## 📦 Содержимое

| Файл | Что делает | Размер |
|------|------------|--------|
| `001-cleanup-duplicates.patch` | Удаляет `classes.jar`, дубль `app/README.md`, обновляет `.gitignore` | 4.5 КБ |
| `002-docs-and-scoring.patch` | Синхронизирует `ARCHITECTURE.md` со скорингом, добавляет `CHANGELOG.md` и `docs/SCORING.md` | 19 КБ |
| `003-performance.patch` | Убирает двойной Haversine в `StationFilterAndSorterImpl`, добавляет LRU кеш в `PriceReliabilityCalculator` | 12 КБ |
| `PROMPT-jules-type-safe-navigation.md` | Готовый промт для Jules (ТЗ №3 из ARCHITECTURE.md — type-safe Navigation) | 6 КБ |

## 🚀 Как применить (на компе)

### Подготовка

```bash
# Скачиваешь aifuel-patches.zip с телефона, кидаешь в папку с проектом
cd ~/projects/AI-Fuel-Assistant
git checkout main
git pull origin main
git status  # чисто, нет локальных правок
```

### Применить все 3 патча в 3 отдельных ветки

```bash
# === Патч 1: мелочи (cleanup) ===
git checkout -b chore/cleanup-duplicates
git apply --check /path/to/patches/001-cleanup-duplicates.patch
git apply /path/to/patches/001-cleanup-duplicates.patch
git add -A
git commit -m "chore: remove build artifacts and duplicate README"

# === Патч 2: документация ===
git checkout main
git checkout -b docs/scoring-and-changelog
git apply /path/to/patches/002-docs-and-scoring.patch
git add -A
git commit -m "docs: sync ARCHITECTURE.md, add CHANGELOG and SCORING.md"

# === Патч 3: производительность ===
git checkout main
git checkout -b perf/scoring-and-cache
git apply /path/to/patches/003-performance.patch
git add -A
git commit -m "perf: eliminate double Haversine calc and add reliability cache"
```

### Прогнать тесты

```bash
# На каждой ветке:
./gradlew assembleDebug
./gradlew lintDebug
./gradlew testDebugUnitTest
```

### Запушить и открыть PR

```bash
git push -u origin chore/cleanup-duplicates
# Открываешь PR через GitHub UI или через gh CLI:
gh pr create --title "chore: remove build artifacts and duplicate README" --body "..."
```

## ⚠️ Если `git apply` ругается

Это значит, что в репо уже есть локальные изменения. Решения:

1. **Откатить локальные изменения на main:**
   ```bash
   git checkout -- .
   git clean -fd
   git pull
   ```

2. **Или применить с `--3way` (3-way merge):**
   ```bash
   git apply --3way /path/to/patches/001-cleanup-duplicates.patch
   ```

3. **Или использовать `git am` вместо `git apply`:**
   ```bash
   git am /path/to/patches/001-cleanup-duplicates.patch
   # (применит сразу с автором и сообщением коммита)
   ```
   `git am` сам подскажет если будут конфликты:
   ```bash
   # После ручного разрешения:
   git add .
   git am --continue
   ```

## 💬 Промт для Jules

Файл `PROMPT-jules-type-safe-navigation.md` — это готовый промт для крупной задачи (type-safe Navigation через kotlinx.serialization). Скопируй его содержимое и вставь в Jules — он сделает PR по вашему стандартному процессу.

## 📊 Что НЕ включено в патчи (но обсуждалось в аудите)

- ❌ `GasStationRepository` — удаление вторичных конструкторов (нужны тесты Hilt с `@TestInstallIn`)
- ❌ `AiRouter` — circuit breaker для fallback (требует дизайн-решения)
- ❌ `MapDiagnosticsTracker` — сделать через Hilt singleton (требует рефакторинг DI)
- ❌ `GeoUtils.hardcodedDetectCity` — убрать magic default (требует ручной пикер городов в UI)

Эти задачи требуют более глубокого погружения. Если хочешь — могу подготовить отдельные промты или сделать после ревью первого PR.

## ✅ Что гарантировано работает

Все 3 патча прошли «git apply --check» без ошибок (сгенерированы из чистого коммита в локальном клоне). Существующие тесты `StationFilterAndSorterTest` и `PriceReliabilityCalculatorTest` **должны** пройти без изменений — патчи сохраняют публичные сигнатуры.

Если что-то падает — пришли вывод `./gradlew testDebugUnitTest`, разберём.
