# Hide Teleport Features

A single toggle that removes every teleport entry point in the app. Only walking and route replay remain as ways to move.

Key files: the settings model and repository, the settings menu, and every screen and view model that offers a teleport.

## Behaviour

- Off by default. It is saved with the settings and round-trips through export.
- Lives under Settings → General → Privacy.
- When on, each teleport control is removed from its composable tree, not just disabled. A visible but inert button is a trap for the user. Affected entry points cover the map's long-press sheets, the favorites and route sheets and pickers, the paste builders, the widget panels, the Group Sync leader teleport and follower snap, and route jump buttons.
- Teleport-between-waypoints is removed with it. A route start still walks to its first stop instead of teleporting.
- Starting a route ignores a stale teleport intent. The intent is checked against the setting, so an old value cannot cause a hop.

The Control API bypasses this setting for teleport-between-waypoints only. That is deliberate, so integrators can still drive hop routes. See [group-sync.md](group-sync.md), "Control API".

## Idle-state exception

When nothing is spoofing yet, a tap on the map or a favorite still teleports. Those are the only ways to set a starting position at all. Hiding them would leave a user with no way to begin, so they are not gated. Every other teleport path is reachable only while already spoofing, or is unconditional, and is gated.

## What is not hidden

- The map hint text changes to describe only walking, because the tap gesture it described is disabled. This is a copy change, not a removed feature.
- Copy that describes favorites still says "teleport or walk". Walking still works, so the copy stays accurate.
