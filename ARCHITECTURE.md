# Архитектура AI Fuel Assistant

## Структура
data/       - Room, API, репозитории, модели (GasStation, FuelPrice)
domain/     - use cases (GetBestStationsUseCase - скоринг АЗС)
ui/         - экраны: map, dashboard, fuel, vehicle + ViewModel
ai/         - AiRouter и провайдеры: Qwen, YandexGPT, GigaChat, DeepSeek
di/         - Hilt-модули

## Поток данных
Screen -> ViewModel -> UseCase -> Repository -> (Room или API)

## AI
AiRouter: fallback Qwen -> YandexGPT -> DeepSeek -> GigaChat

## Навигация
Сейчас: строковые маршруты в AppNavigation.kt
План: type-safe через kotlinx.serialization (ТЗ №3)

## Скоринг АЗС
score = цена + очередь*0.5 + (100-надёжность)*0.2 (меньше = лучше)