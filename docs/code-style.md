# Code Style

## General

- Kotlin only. No Java.
- Package declaration every file, match module structure.
- No wildcard imports.
- Max line length: 120 chars.

## Compose

- State hoisting: ViewModels hold state, Composables receive as params.
- Use `collectAsStateWithLifecycle()`, not `collectAsState()`.
- No business logic in Composables.
- `@Preview` every non-trivial Composable.
- `remember { }` expensive computations, `rememberSaveable { }` state surviving process death.

## Coroutines

- `viewModelScope.launch { }` for ViewModel coroutines.
- `Dispatchers.IO` for DB and network.
- `Dispatchers.Default` for CPU work (path interpolation, RDP simplification).
- `Dispatchers.Main` UI updates only when not already on main.
- Never `runBlocking` on main thread.
- Always `SupervisorJob()` in service scopes.

## Repository Pattern

- Repositories: single source of truth. ViewModels never touch DAOs or DataStore directly.
- `Flow<T>` observable data, `Result<T>` one-shot operations.
- Map Room entities to domain models inside repository, not ViewModel.

## Error Handling

- Use `Result<T>` for failable operations.
- Catch at repository boundary. Domain models and ViewModels stay exception-free.
- `Log.e(TAG, "message", e)` every caught exception. Never swallow silently.

## Naming Conventions

| Symbol | Convention |
|--------|-----------|
| ViewModels | `FeatureViewModel` |
| Screens | `FeatureScreen` |
| DAOs | `EntityDao` |
| Repositories | `FeatureRepository` |
| Services | descriptive + `Service` suffix |