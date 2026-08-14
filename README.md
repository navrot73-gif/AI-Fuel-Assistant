# AI Fuel Assistant

Android app for tracking fuel consumption, picking the best gas stations, and building fuel-aware routes with AI-powered analytics.

## Features

- **Fuel logs** — record refuels and get automatic consumption calculations
- **Fleet management** — multiple vehicles with individual statistics
- **AI analytics** — smart recommendations on station choice and fuel economy
- **Gas station map** — nearby stations with prices and ratings on an offline map
- **Routing** — optimal routes that account for fuel stops

## Tech stack

| Category | Technology |
|----------|------------|
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, Clean Architecture |
| DI | Hilt |
| Database | Room |
| Networking | OkHttp, kotlinx-serialization |
| Maps | osmdroid |
| Navigation | Jetpack Navigation Compose |
| AI | DeepSeek, Qwen, GigaChat, YandexGPT, Hugging Face |
| Build | Gradle (Kotlin DSL) |

## AI providers

| Provider | Notes |
|----------|-------|
| DeepSeek | Primary AI provider |
| Qwen | Alibaba Cloud DashScope |
| GigaChat | Sber, via authorization key or client id/secret |
| YandexGPT | Requires API key + folder id |
| Hugging Face | Requires an access token |

Only **one** configured AI provider is required. Providers without a valid key are skipped automatically.

## Build

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Build APK

```bash
./gradlew assembleDebug
```

### Run tests

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## API keys

Copy `local.properties.example` to `local.properties` and fill in the keys you need:

```bash
cp local.properties.example local.properties
```

```properties
DEEPSEEK_API_KEY=your_deepseek_api_key_here
QWEN_API_KEY=your_qwen_api_key_here
HUGGINGFACE_TOKEN=your_huggingface_token_here
GIGACHAT_AUTHORIZATION_KEY=your_base64_authorization_key_here
GIGACHAT_CLIENT_ID=your_gigachat_client_id_here
GIGACHAT_CLIENT_SECRET=your_gigachat_client_secret_here
YANDEX_API_KEY=your_yandex_api_key_here
YANDEX_FOLDER_ID=your_yandex_folder_id_here
ORS_API_KEY=your_openrouteservice_api_key_here
```

Never commit `local.properties` to Git.

## License

[MIT](LICENSE)

---

# AI Fuel Assistant

Android-приложение для учёта расхода топлива, выбора оптимальных АЗС и построения маршрутов с учётом заправок, с AI-аналитикой.

## Возможности

- **Учёт заправок** — добавление записей и автоматический расчёт расхода топлива
- **Управление автопарком** — несколько транспортных средств с отдельной статистикой
- **AI-аналитика** — рекомендации по выбору АЗС и оптимизации расхода
- **Карта АЗС** — ближайшие заправки с ценами и рейтингами на офлайн-карте
- **Маршруты** — построение оптимальных маршрутов с учётом заправок

## Стек технологий

| Категория | Технологии |
|-----------|------------|
| UI | Jetpack Compose, Material 3 |
| Архитектура | MVVM, Clean Architecture |
| DI | Hilt |
| База данных | Room |
| Сеть | OkHttp, kotlinx-serialization |
| Карты | osmdroid |
| Навигация | Jetpack Navigation Compose |
| AI | DeepSeek, Qwen, GigaChat, YandexGPT, Hugging Face |
| Сборка | Gradle (Kotlin DSL) |

## AI-провайдеры

| Провайдер | Примечания |
|-----------|------------|
| DeepSeek | Основной AI-провайдер |
| Qwen | Alibaba Cloud DashScope |
| GigaChat | Сбер, через ключ авторизации или client id/secret |
| YandexGPT | Требует API-ключ и folder id |
| Hugging Face | Требует access token |

Для работы приложения достаточно **одного** настроенного AI-провайдера. Провайдеры без ключей пропускаются автоматически.

## Сборка

### Требования

- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK 34

### Сборка APK

```bash
./gradlew assembleDebug
```

### Запуск тестов

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## API-ключи

Скопируйте `local.properties.example` в `local.properties` и заполните нужные ключи:

```bash
cp local.properties.example local.properties
```

```properties
DEEPSEEK_API_KEY=your_deepseek_api_key_here
QWEN_API_KEY=your_qwen_api_key_here
HUGGINGFACE_TOKEN=your_huggingface_token_here
GIGACHAT_AUTHORIZATION_KEY=your_base64_authorization_key_here
GIGACHAT_CLIENT_ID=your_gigachat_client_id_here
GIGACHAT_CLIENT_SECRET=your_gigachat_client_secret_here
YANDEX_API_KEY=your_yandex_api_key_here
YANDEX_FOLDER_ID=your_yandex_folder_id_here
ORS_API_KEY=your_openrouteservice_api_key_here
```

Никогда не коммитьте `local.properties` в Git.

## Лицензия

[MIT](LICENSE)
