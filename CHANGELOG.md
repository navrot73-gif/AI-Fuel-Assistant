# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Type-safe navigation routes via `kotlinx.serialization` in `ui/NavigationRoutes.kt` (`NavRoute` sealed interface).
- Unit tests for navigation route serialization and deserialization in `NavigationRoutesTest.kt`.

### Changed
- Refactored `AppNavigation.kt` and `DashboardScreen.kt` from legacy string-based navigation routes to type-safe Compose Navigation 2.8+ API (`composable<NavRoute.X>` and `navController.navigate(NavRoute.X)`).
- Updated `ARCHITECTURE.md` to reflect completed type-safe navigation implementation.
