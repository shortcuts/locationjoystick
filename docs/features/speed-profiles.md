# Speed Profiles

Five presets: Slow Walk, Walk, Run, Bike, Drive. All user-editable. Applies to joystick, route replay, and roaming.

Key files: `:feature:settings:impl/SettingsScreen.kt`, `:core:data/SettingsRepository.kt`

## Presets

| Profile | Constant |
|---------|----------|
| Slow Walk | `AppConstants.ProfileConstants.SLOW_WALK_SPEED_MPS` |
| Walk | `AppConstants.ProfileConstants.WALK_SPEED_MPS` |
| Run | `AppConstants.ProfileConstants.RUN_SPEED_MPS` |
| Bike | `AppConstants.ProfileConstants.BIKE_SPEED_MPS` |
| Drive | `AppConstants.ProfileConstants.DRIVE_SPEED_MPS` |

## Behaviour

- Stored in DataStore.
- UI: scrollable segmented control (roaming default) and individually labeled speed inputs (Settings screen).
- Changes take effect immediately on the next tick.
- Widget's Speed Cycle feature cycles through the user's **enabled** profiles in preset order via `SettingsRepository.getEnabledSpeedProfiles()`.

## Enabled Speed Profiles (Speed Cycle)

Settings → Menus → "Speed Cycle" lets the user choose which of the five presets the widget's Speed Cycle button cycles through — useful since most users only need a subset.

- Default enabled: Walk, Run, Bike. Slow Walk and Drive are opt-in.
- Stored as `AppSettings.enabledSpeedProfileIds` (`Set<String>` of profile IDs), DataStore key `enabled_speed_profile_ids`.
- Editing speed *values* (Settings → GPS) always shows and edits all 5 profiles regardless of enablement — this toggle only affects which profiles are cycled through, not which can be edited.
- If the enabled set is ever empty, `SettingsRepository.getEnabledSpeedProfiles()` falls back to all 5 profiles so cycling never breaks. The Settings UI itself also blocks unchecking the last enabled profile.
- Round-trips through export/import via `AppSettings.enabledSpeedProfileIds`; old exports without the field default to Walk/Run/Bike.

## Constraints

- Speed clamped to `AppConstants.ProfileConstants.MIN_SPEED_MS`–`AppConstants.ProfileConstants.MAX_SPEED_MS`.
- Inline warning shown below speed input when speed exceeds `AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS`. Warning uses generic language — no specific game names. Drive's default speed exceeds this threshold by design.
