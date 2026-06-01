# Floating Widget

Small floating button overlay. Tap to expand a panel with configured quick-access controls. Items configured in Settings.

Key files: `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:settings:impl/SettingsScreen.kt`

## Mechanism

- Same overlay mechanism as the joystick via `:core:overlay`.
- Separate service, toggled independently of the joystick.
- State transitions: collapsed (FAB) ↔ expanded (panel) via `ValueAnimator`.
- Items stored in DataStore as `stringSetPreferencesKey`.

## Service Lifecycle

- Binds to `MockLocationService` in `onStartCommand`.
- Unbinds in `onDestroy`.

## Edge Cases

- No items configured → show placeholder.
- Clamp panel to screen bounds.
- Re-clamp on `onConfigurationChanged`.

## ELEVATION_CONTROLS Feature

When `ELEVATION_CONTROLS` is enabled in Experimental Settings (requires root), it appears in the widget panel as a 3-button column:

| Button | Icon | ElevationMode |
|--------|------|---------------|
| ↑ | KeyboardArrowUp | `TiltUp` |
| ○ | RadioButtonUnchecked | `Neutral` |
| ↓ | KeyboardArrowDown | `TiltDown` |

The active mode is highlighted with `MaterialTheme.colorScheme.primary`; inactive buttons use `LjInactive`. Tapping a button calls `onElevationModeSelected`, which updates `_elevationMode` in `FloatingWidgetService` and relays the new mode to `MockLocationService.setElevationMode()`.

`ELEVATION_CONTROLS` is filtered out of `getWidgetFeatures()` automatically when elevation controls is disabled in settings — it never appears in the widget panel for non-root users.
