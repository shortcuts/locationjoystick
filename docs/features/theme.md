# Theme

Light and dark color themes, for readability across lighting conditions (e.g. direct sunlight).

Key files: `:core:designsystem/LjColors.kt`, `:core:designsystem/LjTheme.kt`, `:core:datastore/AppPreferencesDataSource.kt`, `:core:data/SettingsRepository.kt`, `:app/ThemeViewModel.kt`, `:app/MainActivity.kt`

## Modes

`ThemeMode` (`:core:model`): `DARK` (default, orange-on-black) or `LIGHT` (high-contrast, dark text on a light background). Both share the same `LjTypography` and `LjShapes` — only colors differ; text/font scaling is a separate, unimplemented ask.

## Storage

Persisted as a live (non-draft) DataStore string preference — takes effect immediately, no Save step. Default: `DARK`.

## Toggle

Settings → Menus → "Appearance" → **Light mode** switch.

## Application

`MainActivity` collects `ThemeViewModel.themeMode` (`@HiltViewModel`, wraps `SettingsRepository.getThemeMode()`) and passes `darkTheme = themeMode == ThemeMode.DARK` to the root `LjTheme` composable, so the whole Compose tree recomposes with the new `ColorScheme` as soon as the preference changes.

## Edge Cases

- Not part of `AppSettings`/`ExportData` — it's a per-device display preference, not exported/imported data (matches `REMEMBER_LAST_LOCATION`/compass-tracking pattern).
