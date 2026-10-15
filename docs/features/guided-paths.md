# Guided Paths (Game Setup)

A guided path is a setup choice that hides features a given game does not need. It only hides UI. It never changes a stored value, a default, or a running session.

Key files: `:core:model/GuidedPath.kt`, `:core:model/AppSettings.kt`, `:core:data/SettingsRepository.kt`, `:feature:onboarding:impl/PathPickerStep.kt`, `:feature:onboarding:impl/OnboardingViewModel.kt`, `:feature:settings:impl/SettingsGeneralSubScreen.kt`

## Paths

| Path | Hides |
|------|-------|
| Default | Nothing |
| Pokémon Go | Planting: Settings → Roaming → Planting, the "Walk around / Planting" switch in the roaming sheet, the floating map, and the widget panel |
| Pikmin Bloom | Tap to Walk and Reconnect Accessory: their Settings → Menus sections |

Flags live on `GuidedPath`: `showsPlanting`, `showsTapToWalk`, `showsReconnectAccessory`. Each flag is a pure check on the enum. Gating happens in the UI only.

## Behaviour

- Picked on the last onboarding screen (`PathPickerStep.kt`). Default is preselected. There is no skip. Only finishing that screen sets `ONBOARDING_COMPLETE`.
- Changed any time at Settings → General → Game setup (`GuidedPathSection` in `SettingsGeneralSubScreen.kt`).
- Stored in DataStore as `guidedPath` (the enum name). Exported and imported with the rest of `ExportData`. An unknown or missing value parses as `DEFAULT` (`GuidedPath.fromName`).
- Hiding is not destructive. Saved values for hidden features (for example a Planting radius or a Tap to Walk calibration) stay as they are. Switching back shows the sections again.
- A running planting session or Tap to Walk walk is not stopped by switching path.
- No default value changes for any path. Preset values such as speed caps and planting radius per path are not part of this feature yet.
- Existing installs that already finished onboarding never see the picker. They start on Default.

## Adding a path or a gated feature

1. Add the enum entry or a `showsX` flag in `GuidedPath.kt`. Keep `fromName` falling back to `DEFAULT`.
2. Gate the UI for that feature only. Do not gate the stored setting.
3. Update the Paths table above, the `GuidedPath` row in `docs/domain-models.md`, and the wiki page `docs/wiki/game-setup.html`.
4. Add strings to `values`, `values-zh-rCN` and `values-zh-rTW` for the picker and the switcher.
5. Export and import: the field is already in `ExportData`. Keep the lenient parse.

## Related

- [Onboarding](onboarding.md): the picker is step 4.
- [Roaming](roaming.md): Planting hidden on Pokémon Go.
- [Tap to Walk](tap-to-walk.md): hidden on Pikmin Bloom.
- [Reconnect Accessory](bluetooth-reconnect-tap.md): hidden on Pikmin Bloom.
- [Export / Import](export-import.md): `guidedPath` round-trips.
- [Domain models](../domain-models.md): `GuidedPath`.
