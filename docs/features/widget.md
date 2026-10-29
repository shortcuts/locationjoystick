# Floating Widget

A small floating button overlay. Tap it to expand a panel of quick-access controls. The user chooses which controls appear.

Key files: the widget module's service and panel presenter, and the settings screen.

## Mechanism

- Same overlay mechanism as the joystick ([joystick.md](joystick.md)). It is a separate service, toggled independently of the joystick.
- It starts automatically when spoofing starts, provided the overlay permission is granted. There is no separate start button.
- Enabled features are a set. Display order is a separate list. See "Configurability".

### Pause and stop

Long-pressing the launcher icon offers Pause and Stop while spoofing is active.

- **Pause** parks mock location: spoofing stops, and the joystick and any route, walk or roam stop with it. The widget stays on screen and its controls fade until Start. Start resumes at the last location.
- **Stop** ends everything and removes the widget.

Pause is not the same as the paused state of a route. A paused route keeps pushing a frozen fix. A parked widget removes the mock provider altogether, so the app does not look like it is still spoofing.

Why the service stays alive while parked: the foreground notification requires the service to keep running, and a restart must not resume spoofing unasked. A stored flag keeps a restart parked. Full stop always clears that flag, even if the service is already parked, because the state would not emit again.

## Hiding the overlay

A setting hides the widget entirely. The joystick and the accessibility-based features are unaffected. The setting takes effect immediately during a session. Turning it on removes the overlay, and turning it off restores it, without waiting for the next start.

## Configurability

The widget panel and the map's button column show the same set of features. Each feature declares which surfaces it can appear on, widget or map or both.

- One shared display order keeps the two surfaces consistent. The user can enable features differently per surface, but not reorder them differently.
- New features are added to existing installs once, through a merge against the defaults each version saw. Turning a default off in settings sticks; the merge never turns it back on.

Speed cycle writes the global active speed profile. Starting a route or a roam first switches to that session's profile. Later changes win for the rest of the session and are not written back into the route's pin or the roaming defaults.

## Share and picker

- The map's share action copies coordinates or shares a link. The link path closes the overlay first, so the system share sheet is not covered.
- The favorites and routes pickers have a search field. It is hidden until the user opens it, and it clears when the picker closes, so each reopen starts unfiltered.
- Overlay panels that take text input must be focusable, or the keyboard never appears. The compact icon column must not be focusable, or it steals focus from the app underneath.
- Sharing from a picker closes the overlay first, for the same reason as the map share action.

## Paste overlay

The paste sheet is a bottom sheet, not a full-screen dimmer, so taps above it reach the app underneath. The user can copy from Maps while the sheet is open.

- It can shrink to a bar, so the user can switch apps and copy. Shrinking makes the window non-focusable so the other app can take focus. The form stays alive while shrunk, so typed text is kept.
- Clipboard paste appends a line, as on the other paste surfaces.
- A long-press on the paste button offers a shortcut to the Capture screen. A short tap still opens paste.

Teleporting from the paste sheet is a plain position jump and does not call the routing service. Walking via roads does, and can fall back to a straight walk when routing is unavailable. A teleport during spoofing aborts any road-following start that is still pending, so a late result cannot undo the jump.

## Appearance

Overlay panels follow the app's theme setting. Their icons are tinted for legibility over other apps, because the icon column is drawn on top of whatever app is running. Colour states follow one rule across the app: grey when idle, green when moving, orange when paused. Green also marks an "on" state. Disabled controls fade, so they stay visible without looking active.

## Route controls across surfaces

Pause, resume and stop for a route are implemented on three surfaces: the main map, the widget panel and the floating map. Each keeps its own expanded or collapsed state. A change on one surface does not affect the others.

- The widget's route icon is active only for route replay and walk-to. The roaming icon is active only for roaming. Roaming never lights the route icon.
- The route icon is grey when idle, green while moving, and orange while paused.
- A `current / total` progress chip sits at the bottom of the icon column while a route plays. It uses the same progress source as the main map ([routes.md](routes.md)).

## Expanded controls and touch

The compact overlay is sized to its content. A transparent area around the icon column still takes touches, which would block the app underneath. Extra controls that open beside an icon are drawn in a separate child window attached to the overlay, sized to the buttons. This keeps the taps around them passing through.

The side popups stay attached to the parent when the widget is dragged. They flip to the left when the widget is near the right edge of the screen.

## Floating map

The floating map is a map view inside an overlay window ([map.md](map.md), [map-tiles.md](map-tiles.md)). Rules that cannot be changed without breaking it:

- It renders in texture mode. The default surface view punches a transparent hole, so the map shows no streets over the game.
- It opens compact, so taps outside its window reach the game. Expand switches the same window to a larger layout without recreating the map.
- North lock starts on: bearing is held at zero and rotation gestures are disabled, while panning still works. Unlocking restores the normal gestures and does not force a saved bearing.
- Its centre button follows the spoofed position while spoofing. When idle, it falls back to the last point the map camera was moved to.

### Route controls on the floating map

The floating map's button column shows a route button when the routes feature is enabled on the map, or when a route is playing.

- Tapping it while a route plays expands Pause/Resume and Stop beside it.
- Tapping it while idle opens the routes picker.
- Its expanded state lives in the presenter, not in the map's composable. The map view is recreated on every open, so composable state would reset, leaving the controls unreachable mid-route.

### Feature gates

Paste coordinates is off by default and must be enabled in settings. Roaming is on by default on the widget. Capture is on by default on the map. Each has its own merge rule for existing installs, so a feature is added once and never silently re-enabled.

Roaming on the widget: a tap while idle opens the roaming panel. A tap while roaming expands pause and stop. A playing route blocks a roam start, and the roam icon fades when that is the case. Generate in the panel shows the path on a read-only map, and Start walks that path.

The joystick toggle shows or hides the stick on its own. It does not need the stick to be locked. The lock button toggles locking. Moving the stick pauses the walk, route or roam, and releasing it leaves that paused until the user resumes.

## Altitude override button

An optional button in the widget panel opens an inline numeric field with the current altitude. Confirming it sets a base altitude that overrides the default and any real-elevation lookup. The override applies at once. Setting it suspends the periodic elevation lookup until the user resets it in settings ([mock-location.md](mock-location.md)).

The panel's overlay window is non-focusable by design, so it never takes focus from the app underneath. The altitude field is the one exception: while it is open, the window becomes focusable, so the keyboard can appear. It goes back to non-focusable when the field closes.

## Debug stats

An optional block in the expanded panel shows live position, speed, altitude, accuracy, bearing and tick rate. It is for diagnosis, so it reads the same tick as the mock location loop.

- Tick rate is measured from the time between ticks, not assumed, so a stalled loop shows up.
- Bearing shows a dash until the first movement. A made-up zero would look like a real heading.
- It also carries the last reconnect check result, in memory only. See [bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md).
- The readout is drawn in its own child window. Drawn inline, it widened the overlay's touch area and blocked taps on the map beside the icons.

## Completion badge

A red dot on the widget button marks a route, walk or roam that ended on its own. It is cleared when the user expands the panel. It does not appear when the user stopped the session.

## Anti-patterns to avoid

- Do not keep panel state that must survive a reopen in composable `remember`. Each open builds a fresh composable tree, so the state resets. Hold it in the presenter or service as a flow.
- Do not make the compact overlay focusable. It would steal keyboard focus from the game.
- Do not draw an extra control in the same window as the icon column. The window's touch area grows to cover the whole row, and taps on the game beside it are lost.

## Edge cases

- No features enabled: show a placeholder, not an empty panel.
- The panel is clamped to the screen. It is clamped again on rotation.
