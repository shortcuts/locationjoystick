# Architecture

## Module Structure

Multi-module, NowInAndroid-style. Feature = `api` (contract) + `impl`. Shared: `:core:*`.

```
feature/*        — UI + ViewModels (Compose screens, no business logic)
  ↓ depends on
core/data        — Repositories (single source of truth)
  ↓
core/database    — Room DB
core/datastore   — DataStore Prefs

core/location    — Mock GPS engine (ForegroundService), independent of UI
core/model       — Pure Kotlin data classes, no Android deps
```

| Module | Purpose |
|--------|---------|
| `:app` | Entry, Hilt, `LjApp`, `LjNavHost`, drawer |
| `:core:common` | Utils, extensions, constants (`AppConstants`) |
| `:core:data` | Repositories, DataStore prefs |
| `:core:database` | Room DB (v2), DAOs, entities |
| `:core:datastore` | DataStore prefs source |
| `:core:designsystem` | Tokens, theme, typography, shared components |
| `:core:location` | Mock GPS foreground service + movement engine |
| `:core:model` | Pure Kotlin domain classes |
| `:core:overlay` | WindowManager overlay utils |
| `:core:routing` | OSRM client, route interpolation, roaming engine, replay engine |
| `:core:testing` | Shared test utils, fakes |
| `:feature:favorites:api` / `:impl` | Favorites list, MapPicker, teleport |
| `:feature:joystick:api` / `:impl` | Floating joystick overlay service |
| `:feature:map:api` / `:impl` | MapLibre screen, map interactions, roaming bottom sheet |
| `:feature:onboarding:api` / `:impl` | Multi-step onboarding flow |
| `:feature:routes:api` / `:impl` | Route list, creator, detail, replay |
| `:feature:settings:api` / `:impl` | Speed profiles, widget config, export/import, QR transfer |
| `:feature:widget:api` / `:impl` | Floating widget overlay service, map floating view |

## MVVM + Repository Pattern

VMs expose `StateFlow`/`SharedFlow`. UI collects via `collectAsStateWithLifecycle()`. Repos = single truth — VMs never touch DAOs/DataStore directly.

Data flow: ViewModel → Repository → DataSource (Room / DataStore / LocationManager).

## Navigation

`LjApp` wraps `LjNavHost` in `ModalNavigationDrawer`. `IdleScreen` = main hub post-onboarding; cards nav to Map, Routes, Favorites, Settings.

`LjNavHost` uses nested `navigation {}` graphs for back-isolation:
- `routes_graph`: Routes + RouteCreator + RouteDetail
- `favorites_graph`: Favorites + MapPicker

Drawer nav: `popUpTo(IDLE_ROUTE) { saveState = true }` + `launchSingleTop + restoreState`.

`FavoritesViewModel` shared across favorites graph via `hiltViewModel(navController.getBackStackEntry("favorites_graph"))`.

## Dependency Injection

Hilt throughout. VMs: `@HiltViewModel`. Repos: `@Singleton`.

## Reactive Streams

Kotlin Flow everywhere. No RxJava. No LiveData.

## Coroutines

- `viewModelScope` for UI-bound work
- `ServiceScope` (service lifecycle) for background work
- Never `GlobalScope`
- Always `SupervisorJob()` in service scopes