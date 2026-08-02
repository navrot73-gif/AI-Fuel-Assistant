# AI Fuel Assistant

AI-powered Android приложение для анализа расхода топлива, выбора оптимальных АЗС и построения маршрутов.

## Возможности

- **Учёт заправок** — добавление записей о заправках с расчётом расхода топлива
- **Управление автопарком** — несколько ТС с индивидуальной статистикой
- **AI-аналитика** — интеллектуальные рекомендации по выбору АЗС и оптимизации расхода
- **Карта АЗС** — отображение ближайших заправок с ценами и рейтингами
- **Маршруты** — построение оптимальных маршрутов с учётом заправок

## Стек технологий

| Категория | Технологии |
|-----------|------------|
| UI | Jetpack Compose, Material 3 |
| Архитектура | MVVM, Clean Architecture |
| DI | Hilt |
| База данных | Room |
| Сеть | OkHttp 4 |
| Карты | OSMDroid |
| AI-провайдеры | DeepSeek, GigaChat (Сбер), YandexGPT, HuggingFace |
| Навигация | Jetpack Navigation Compose |

## Сборка

### Требования
- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK 34

### Настройка ключей API

Скопируйте `local.properties.example` в `local.properties` и заполните нужные ключи:

```bash
cp local.properties.example local.properties
```

```properties
DEEPSEEK_API_KEY=your_deepseek_key
HUGGINGFACE_TOKEN=your_hf_token
GIGACHAT_CLIENT_ID=your_giga_client_id
GIGACHAT_CLIENT_SECRET=your_giga_secret
GIGACHAT_AUTHORIZATION_KEY=your_giga_auth_key
YANDEX_API_KEY=your_yandex_key
YANDEX_FOLDER_ID=your_yandex_folder
ORS_API_KEY=your_ors_key
```

> **Примечание:** Для работы приложения достаточно **одного** настроенного AI-провайдера. Если ключи не указаны, соответствующий провайдер просто не будет использоваться.

### Сборка APK

```bash
./gradlew assembleDebug
```

### Запуск тестов

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## Архитектура

```
app/
├── data/           # Репозитории, DAO, сущности БД
├── domain/         # Бизнес-логика (FuelDispatcher, модели)
├── ai/             # AI-провайдеры и роутер
├── geo/            # Геосервисы (ORS, Nominatim)
├── features/       # Экраны и ViewModel'и
│   └── dashboard/  # Главный экран с метриками
├── ui/             # Compose-экраны, темы, компоненты
└── di/             # Hilt-модули
```

## Лицензия

[MIT](LICENSE)
