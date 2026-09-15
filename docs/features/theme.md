# Theme

Light and dark color themes, for readability across lighting conditions (e.g. direct sunlight).

Key files: `:core:designsystem/LjColors.kt`, `:core:designsystem/LjTheme.kt`, `:core:designsystem/component/LjCheckboxRow.kt`, `:core:datastore/AppPreferencesDataSource.kt`, `:core:data/SettingsRepository.kt`, `:app/ThemeViewModel.kt`, `:app/MainActivity.kt`, `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:widget:impl/WidgetPanelPresenter.kt`

## Modes

`ThemeMode` (`:core:model`): `DARK` (default, orange-on-black) or `LIGHT` (high-contrast, dark text on a light background). Both share the same `LjTypography` and `LjShapes` — only colors differ; text/font scaling is a separate, unimplemented ask.

## Storage

Persisted as a live (non-draft) DataStore string preference — takes effect immediately, no Save step. Default: `DARK`.

## Toggle

Settings → Menus → "Appearance" → **Light mode** switch.

## Application

`MainActivity` collects `ThemeViewModel.themeMode` (`@HiltViewModel`, wraps `SettingsRepository.getThemeMode()`) and passes `darkTheme = themeMode == ThemeMode.DARK` to the root `LjTheme` composable, so the whole Compose tree recomposes with the new `ColorScheme` as soon as the preference changes.

Overlay panels (`FloatingWidgetService`, `WidgetPanelPresenter`) collect the same `getThemeMode()` flow and pass it into `LjTheme`, so routes / favorites / paste / roaming sheets match the in-app Appearance switch. The round widget icon column stays black with bright icons so it remains visible over other apps in both modes.

Unchecked checkboxes and outlined fields in dark mode use `LjDarkOutlineVariant` (`#8A8490`) plus `ljCheckboxColors()` (`uncheckedColor = onSurface`) so Loop / Planting / Reverse / Return / Follow roads / Teleport between waypoints stay visible on `LjSurface`. Filled Start / Teleport buttons inherit `onPrimary` / outline colours — hosts must not paint those labels with `LjText`.

## Edge Cases

- Not part of `AppSettings`/`ExportData` — it's a per-device display preference, not exported/imported data (matches `REMEMBER_LAST_LOCATION`/compass-tracking pattern).
