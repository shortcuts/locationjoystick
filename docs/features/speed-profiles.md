# Speed Profiles

A set of editable speed presets. The built-in presets cover walking through driving. Profiles apply to the joystick, route replay, walk-to and roaming.

Key files: the settings screen and the settings repository.

## Behaviour

- Profiles are stored in settings. Each has an editable speed. The built-in profiles cannot be deleted.
- The settings screen shows the speed controls as a segmented control for roaming defaults, and as labelled inputs for the speed values.
- Starting a saved route or a roam switches the active profile to that session's chosen speed. The sources are the route's pinned profile, the walk-around profile, or the planting profile.
- After start, the widget's speed cycle and the settings speed changes apply on the next tick. They do not rewrite the route's pin or the roaming defaults. The session follows the user, not the saved setting.

## Speed cycle

The widget's speed cycle button cycles through the profiles the user has enabled, in preset order.

- Most users need only a few. Walk, Run and Bike are on by default. Slow Walk and Drive are opt-in.
- Editing a profile's speed always shows all profiles, whether or not they are enabled. The enabled set only controls cycling.
- If the enabled set is ever empty, cycling falls back to all profiles, so the button never does nothing. The settings screen also refuses to uncheck the last enabled profile.

## Constraints

- Speeds are clamped to a range. Out-of-range input is not saved.
- Above a threshold, the settings screen shows a warning. The threshold is an anti-cheat signal, not a physical limit. The warning must use neutral wording and name no game, because naming one would tie the app to it. The default drive speed is above the threshold by design.

## Control API

Speeds can be read and updated over the leader Control API. See [group-sync.md](group-sync.md), "Content API".
