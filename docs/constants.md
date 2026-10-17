# Constants

All constants live in `:core:common/constants/AppConstants.kt`. Look there for values and for the groups they belong to.

## Rules

- No new top-level or companion constant outside `AppConstants`. Add it there.
- Modules that need constants declare `implementation(project(":core:common"))` in their `build.gradle.kts`.
- Exception: `:core:model` is pure JVM and cannot depend on `core:common`. Constants only used in `:core:model` stay there.
