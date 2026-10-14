# Theme

Light and dark color themes, for readability across lighting conditions (e.g. direct sunlight).

Key files: `:core:designsystem/LjColors.kt`, `:core:designsystem/LjTheme.kt`, `:core:designsystem/LjShapes.kt`, `:core:designsystem/LjTypography.kt`, `:core:designsystem/component/LjCard.kt`, `:core:designsystem/component/LjBottomSheet.kt`, `:core:designsystem/component/LjMapIconButton.kt`, `:core:designsystem/component/LjCheckboxRow.kt`, `:core:datastore/AppPreferencesDataSource.kt`, `:core:data/SettingsRepository.kt`, `:app/ThemeViewModel.kt`, `:app/MainActivity.kt`, `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:widget:impl/WidgetPanelPresenter.kt`

## Modes

`ThemeMode` (`:core:model`): `DARK` (default, orange-on-black) or `LIGHT` (high-contrast, dark text on a light background). Both share the same `LjTypography` and `LjShapes` — only colors differ; text/font scaling is a separate, unimplemented ask. The light theme text pairs (accent and error on their containers/surfaces), the top-bar Start/Stop toggle tints, and unchecked checkbox / outlined field outlines meet WCAG accessibility minima (4.5:1 for text, 3:1 for controls) and are guarded by `LjColorsContrastTest`.

## Storage

Persisted as a live (non-draft) DataStore string preference — takes effect immediately, no Save step. Default: `DARK`.

## Toggle

Settings → Menus → "Appearance" → **Light mode** switch.

## Application

`MainActivity` collects `ThemeViewModel.themeMode` (`@HiltViewModel`, wraps `SettingsRepository.getThemeMode()`) and passes `darkTheme = themeMode == ThemeMode.DARK` to the root `LjTheme` composable, so the whole Compose tree recomposes with the new `ColorScheme` as soon as the preference changes.

Overlay panels (`FloatingWidgetService`, `WidgetPanelPresenter`) collect the same `getThemeMode()` flow and pass it into `LjTheme`, so routes / favorites / paste / roaming sheets match the in-app Appearance switch. The round widget icon column stays black with bright icons so it remains visible over other apps in both modes.

Unchecked checkboxes and outlined fields route through `outlineVariant`: dark theme uses `LjDarkOutlineVariant` (`#8A8490`), light theme uses `LjLightOutlineVariant` (`#7A6F68`), both meeting at least 3:1 contrast (WCAG 1.4.11) on their respective surfaces and guarded by `LjColorsContrastTest`. Filled Start / Teleport buttons inherit `onPrimary` / outline colours — hosts must not paint those labels with `LjText`.

## Surfaces, shapes and type

- Both color schemes set the `surfaceContainer*` tokens explicitly. Unset, Material falls back to its baseline (purple-tinted) palette for sheets and cards. Sheets use `surfaceContainerLow`, one step above the background; cards use `surfaceVariant`, one step above the sheet.
- `LjCard`/`LjSurface`/`LjOutlinedCard` are flat (no elevation) on `surfaceVariant` with a 1 dp `outlineVariant` hairline and 12 dp corners. Buttons and segmented controls use 8 dp corners; outlined buttons use the hairline, not the orange `outline`. `LjTopBar` sits on `background` with a hairline divider underneath.
- `LjModalBottomSheet` (`LjBottomSheet.kt`) is the shared sheet wrapper: `surface` container, 16 dp top corners, theme scrim. The map, Routes and Favorites sheets use it; sheet titles are `titleMedium`, full-screen form titles `titleLarge`. `LjFloatingActionButton` is a flat 12 dp rounded square (Routes and Favorites add buttons), and picker rows carry the hairline border.
- Map FABs (`LjMapIconButton`) and the route-progress chip are 12 dp rounded squares. `CooldownAdvisoryBadge` is plain `bodySmall` text, not a filled chip.
- Corner radii scale: 4 / 8 / 12 / 16 / 20 dp (`LjShapes`).
- `LjTypography` uses SemiBold titles and headlines with no negative letter spacing; sizes are unchanged.
- No shadows: `LjMapIconButton`, the search bar, the map hint pill and floating overlay panels use a 1 dp `outlineVariant` hairline border instead.

## Edge Cases

- Not part of `AppSettings`/`ExportData` — it's a per-device display preference, not exported/imported data (matches `REMEMBER_LAST_LOCATION`/compass-tracking pattern).
