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
| `:core:database` | Room DB, DAOs, entities |
| `:core:datastore` | DataStore prefs source |
| `:core:designsystem` | Tokens, theme, typography, shared components |
| `:core:location` | Mock GPS foreground service + movement engine |
| `:core:model` | Pure Kotlin domain classes |
| `:core:map` | Map view factories, MapLibre bridge, OSM tile HTTP (docs/features/map-tiles.md) |
| `:core:overlay` | WindowManager overlay utils |
| `:core:routing` | OSRM client, route interpolation, roaming engine, replay engine |
| `:core:testing` | Shared test utils, fakes |
| `:lint:checks` | Custom Android Lint rules (see docs/features/localization.md) |
| `:feature:favorites:api` / `:impl` | Favorites list, MapPicker, teleport |
| `:feature:group:api` / `:impl` | Group Sync screen — leader/follower Wi-Fi location sync |
| `:feature:joystick:impl` | Floating joystick overlay |
| `:feature:map:api` / `:impl` | MapLibre screen, map interactions, roaming bottom sheet |
| `:feature:onboarding:api` / `:impl` | Multi-step onboarding flow |
| `:feature:routes:api` / `:impl` | Route list, creator, detail, replay |
| `:feature:settings:api` / `:impl` | Speed profiles, widget config, export/import, QR transfer |
| `:feature:widget:impl` | Floating widget overlay + panel |

## MVVM + Repository Pattern

VMs expose `StateFlow`/`SharedFlow`. UI collects via `collectAsStateWithLifecycle()`. Repos = single truth — VMs never touch DAOs/DataStore directly.

Data flow: ViewModel → Repository → DataSource (Room / DataStore / LocationManager).

## Navigation

`LjApp` wraps `LjNavHost` in `ModalNavigationDrawer`. `IdleScreen` = main hub post-onboarding; cards nav to Map, Routes, Favorites, Capture, Settings.

Navigation graphs are nested so that back stays inside a feature (the routes and favorites flows each have their own graph). The start destination is chosen once from the spoofing state, and onboarding always wins. The graph code is the reference for which destinations exist and how they connect.

On app stop, the app returns to Idle so map-heavy screens unload. The screens that must survive a stop are listed in the shouldSkipIdleRedirect function. They are the screens where the user leaves the app on purpose to copy coordinates or to pick a file. Keep that list in step with those flows.

Feature view models that share a graph share its back-stack entry, so state survives moves inside that graph.

## Dependency Injection

Hilt throughout. VMs: `@HiltViewModel`. Repos: `@Singleton`.

## Reactive Streams

Kotlin Flow everywhere. No RxJava. No LiveData.

## Coroutines

- `viewModelScope` for UI-bound work
- `ServiceScope` (service lifecycle) for background work
- Never `GlobalScope`
- Always `SupervisorJob()` in service scopes
