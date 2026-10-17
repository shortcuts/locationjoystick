# Guided Paths (Game Setup)

A guided path is a setup choice that hides features a given game does not need. It never changes a stored value, a default, or a running session. Beyond hiding UI, it gates the runtime of Tap to Walk and Reconnect Accessory.

Key files: `:core:model/GuidedPath.kt` (the path flags), `:core:data/SettingsRepository.kt`, the onboarding picker, and the Game setup section of General settings.

## Paths

- **Default:** nothing hidden.
- **Pokémon Go:** hides Planting everywhere it is offered. Planting is a walking-around-a-game-map feature the game does not need.
- **Pikmin Bloom:** hides Tap to Walk and Reconnect Accessory. Neither is useful in that game.

On Pikmin Bloom the reconnect loop stops and the widget crosshair is hidden, since the hidden settings would otherwise leave no way to turn them off. Stored values stay, so switching back restores them.

The exact screens each path hides are in the flags on `GuidedPath` and the code that reads them.

## Why it is UI-only

Hiding is not destructive. A path that hides a feature must leave that feature's saved values alone, so switching back restores them. Gating a stored setting would make a switch lose data.

A running planting session or Tap to Walk walk is not stopped by switching path, for the same reason.

No default value changes with a path. Per-path presets such as speed caps are not part of this feature, and should not be added without a new decision: a preset would look like a setting the user chose.

## Behaviour

- Picked on the last onboarding screen. Default is preselected. There is no skip; finishing that screen is what marks onboarding complete.
- Changed any time from General settings.
- Stored with the rest of the settings and exported with them. An unknown or missing value falls back to the default path. Older backups and manually edited files must not break.
- Existing installs that already finished onboarding never see the picker and start on Default.

## Adding a path or a gated feature

1. Add the enum entry or a flag in `GuidedPath.kt`. Keep the lenient parse falling back to the default.
2. Gate the UI for that feature only. Do not gate the stored setting.
3. Update the wiki game-setup page. It is the user-facing list of what each path hides.
4. Add translations for the picker and the switcher.

## Related

- [Onboarding](onboarding.md): the picker is one of its steps.
- [Roaming](roaming.md): Planting is hidden on Pokémon Go.
- [Tap to Walk](tap-to-walk.md): hidden on Pikmin Bloom.
- [Reconnect Accessory](bluetooth-reconnect-tap.md): hidden on Pikmin Bloom.
- [Export / Import](export-import.md): the path round-trips with the other settings.
