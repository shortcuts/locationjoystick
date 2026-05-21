# Speed Profiles

Three presets: Walk, Run, Bike. All user-editable. Applies to joystick, route replay, and roaming.

Key files: `:feature:settings:impl/SettingsScreen.kt`, `:core:data/SettingsRepository.kt`

## Presets

| Profile | Constant |
|---------|----------|
| Walk | `AppConstants.ProfileConstants.WALK_SPEED_MPS` |
| Run | `AppConstants.ProfileConstants.RUN_SPEED_MPS` |
| Bike | `AppConstants.ProfileConstants.BIKE_SPEED_MPS` |

## Behaviour

- Stored in DataStore.
- UI: three chips or segmented button in widget + Settings screen.
- Changes take effect immediately on the next tick.

## Constraints

- Speed clamped to `AppConstants.ProfileConstants.MIN_SPEED_MS`–`AppConstants.ProfileConstants.MAX_SPEED_MS`.
- Inline warning shown below speed input when speed exceeds `AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS`. Warning uses generic language — no specific game names.
