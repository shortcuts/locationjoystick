# Floating Widget

Small floating button overlay. Tap to expand a panel with configured quick-access controls. Items configured in Settings.

Key files: `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:settings:impl/SettingsScreen.kt`

## Mechanism

- Same overlay mechanism as the joystick via `:core:overlay`.
- Separate service, toggled independently of the joystick.
- State transitions: collapsed (FAB) ↔ expanded (panel) via `ValueAnimator`.
- Long-press the launcher icon shows a `WidgetSidePopup` with **Pause** and **Stop** while spoofing is active. Pause calls `MapController.parkSpoofingKeepWidget()`: mock GPS, joystick overlay, wakelock, and any route/walk/roam stop, but `FloatingWidgetService` stays. Feature icons fade (`WidgetIgnoredTint`) and taps are no-ops until Start. The Pause icon becomes **Start** (`mapController.startSpoofing()` at the last location). Stop still calls `MapController.stopSpoofing()` (same as the app top-bar Stop) and removes the widget. Short tap still expands/collapses; drag still repositions.
- Enabled features stored in DataStore as `stringSetPreferencesKey`; the shared display order (see below) is a separate `stringPreferencesKey`.
- `MockLocationService` auto-starts the widget overlay (alongside the joystick overlay) whenever spoofing starts and `SYSTEM_ALERT_WINDOW` is granted — there is no separate manual start button for it.

## Hiding the Overlay

Settings → Menus → Privacy → "Hide floating widget" (`AppSettings.hideWidgetOverlay`, DataStore key `hide_widget_overlay`, default `false`) stops `MockLocationService` from starting `FloatingWidgetService` when spoofing starts. The joystick overlay and any accessibility-based features (e.g. compass tracking, see @docs/features/tap-to-walk.md) are unaffected — this only hides the widget button/panel itself. Round-trips through `ExportData` like `hideTeleportFeatures`.

Live while spoofing is active: `MockLocationService.observeLocationState()` runs a reactive collector (`combine` of its own state and `getHideWidgetOverlay()`, decision via the pure `computeWidgetOverlayAction()` in `LocationLoopPolicy.kt`) that starts or stops `FloatingWidgetService` the moment the toggle changes, not just at the next RUNNING transition — flipping the setting mid-session removes (or restores) the overlay immediately.

## Configurability

Both the widget panel and the map screen's FAB column render the same `AppFeature` set (`:core:model/AppFeature.kt`), each feature declaring which surface(s) — `WIDGET`, `MAP`, or both — it's eligible for. Settings → Menus → "App Features" shows one combined, drag-to-reorder list: a drag handle, a checkbox to show on the widget (if eligible), and a checkbox to show on the map (if eligible).

A single shared `featureOrder` list controls display order on both surfaces, so they stay consistent by default — the user can still diverge enablement per surface, just not relative order. `SettingsRepository.getWidgetFeatures()` / `getMapFeatures()` filter+sort that shared order by each surface's enabled set.

Speed Cycle (`AppFeature.SPEED_CYCLE`) writes the global active profile via `SettingsRepository.setActiveProfileId`. Starting a route or roam switches to that session's chosen profile first (`activateSessionSpeed`); later Speed Cycle taps and Settings speed changes win for the rest of the session and are not written back into the route pin or roaming defaults.

## Picker search

The full-screen favorites and routes pickers (`FavoritesFloatingView` / `RoutesFloatingView`)
show a search icon next to Close. Tapping it reveals a `ListSearchField`; tapping again hides
the field and clears the query so the full list returns. Query state is `remember` inside those
composables and the panel `ComposeView` is recreated on every `showPanel()`, so reopening always
starts unfiltered.

The favorites picker also has **Share current location** immediately left of Search (same
`shareCurrentLocationCoordinates` helper as the Favorites screen toolbar). It is hidden on a
favorite's detail (`hasBack`). Empty lists still show Share, even though Search is hidden.
A successful share closes the overlay (`onShareOpened`) before starting the chooser so the
system sheet is not covered by the panel; a missing location only toasts and leaves the list
open.

Both pickers use `mapPanelLayoutParams()` (focusable overlay + `SOFT_INPUT_ADJUST_RESIZE`) so the
search field can take IME input. The compact FAB column stays `FLAG_NOT_FOCUSABLE`.

Favorite detail adds **Rename** and **Delete** with in-overlay dialogs. Route detail adds compact
Rename, Share, and Delete icons above its start options. Sharing dismisses the overlay before
launching Android's share sheet, preventing the picker from covering or trapping the sheet.

## Paste coordinates overlay

The widget paste button opens a **bottom sheet**, not a full-screen dimmer (`pasteOverlayLayoutParams()`:
`MATCH_PARENT` × `WRAP_CONTENT`, `Gravity.BOTTOM`, `FLAG_NOT_TOUCH_MODAL`). Taps above the sheet
reach the app underneath so the user can copy from Maps without closing paste. Height is capped at
half the screen; the form already scrolls.

The title row has a shrink control (`ExpandMore` / `ExpandLess`). Shrunk, the sheet is a bar plus
hint ("Switch apps to copy, then expand to paste") and the window becomes `FLAG_NOT_FOCUSABLE` so
the other app can take focus for copy. The `PasteCoordinatesForm` stays composed at zero size so
`rememberSaveable` text is not wiped. Expand restores focus for IME and the clipboard paste icon.

Clipboard paste on every paste surface **appends** a new line when the field already has text
(`mergeClipboardIntoPasteText`) so a second copy from another app adds a point instead of replacing
the first.

Long-pressing the widget panel's paste button toggles one compact, anchor-aligned Capture shortcut
in a `WidgetSidePopup`. It opens the existing Capture screen in `MainActivity` directly, without an
extra-functions menu. A short tap still opens paste.

Teleport from this sheet (and from the map paste sheet) is a GPS jump: it does not call OSRM.
Walk via roads does, and can fall back to a straight walk if routing is down. A user teleport
while spoofing is RUNNING aborts any in-flight Follow-roads route start so a late routing result
cannot overwrite the jump, and the test provider is updated on that same intent (not on the next
1 Hz tick). After widget Pause, paste and the other feature icons stay faded until Start —
teleport does not unpark mock GPS.

See @docs/features/favorites.md and @docs/features/routes.md for the shared `matchesNameSearch`
filter used by the in-app lists and map sheets.

## Appearance

Overlay panels follow Settings → Appearance (`getThemeMode()` → `LjTheme`), same as the main app.
List rows use `surfaceVariant`; route-start checkboxes use `ljCheckboxColors()`. The compact icon
column stays black circles with orange / white icons so it remains visible over other apps —
inactive icons use a light grey-white tint, and ignored joystick / roam icons fade to about 42%
white instead of nearly matching the circle. The launcher and every expanded control share a
50 dp-wide, 52 dp-high slot: each control keeps a 48 dp touch target around its 42 dp circle, while
the common slot centers the full column and reduces the visible gap by 4 dp. `WidgetSidePopup`
uses the same slot height, so pause, stop, and other secondary controls stay centered on their
parent row. See @docs/features/theme.md.

## Service Lifecycle

- Binds to `MockLocationService` in `onStartCommand`.
- Unbinds in `onDestroy`.
- Binds to `JoystickOverlayService` only while mock GPS is not IDLE. Pause unbinds first so `BIND_AUTO_CREATE` cannot restart the joystick while the widget stays on screen.

## Pause vs Stop

Pause is **not** `LocationRepository.pauseSpoofing()` (`MockLocationState.PAUSED`), which keeps pushing a frozen mock fix. Pause parks: state goes IDLE, the test provider is removed, and `MockLocationService` does **not** `stopSelf()`, matching the follower-inactive-leader keep-alive pattern. A persisted `keep_widget_on_idle` flag makes a `START_STICKY` restart stay parked instead of resuming spoofing at the last location. Full Stop (widget long-press Stop, notification Stop, or app Stop) always clears that flag, stops the widget, and `stopSelf()`s — including while already parked, because StateFlow will not re-emit IDLE. The widget also `stopSelf()`s on Stop so the overlay goes away immediately. The foreground notification stays while parked (Android requires an ongoing notification for the still-running service); Stop on that notification is a full stop.

## Completion Badge

A red dot appears at the top-right of the widget FAB when a route, walk, or roaming session ends naturally (completion, not user-initiated stop). The badge is driven by `pendingCompletionFlow: MutableStateFlow<Boolean>` in `FloatingWidgetService`, set on `mapController.completionMessages` emission.

- **Trigger**: any natural completion event (route replay end, walk-to arrival, roaming loop end).
- **Cleared**: when the user taps the FAB to expand the panel (`isPanelExpandedFlow` becomes `true`).
- **Does not appear**: when the user manually stops a session.

## Route Controls Across Surfaces

Pause/resume/stop for an active route replay is implemented separately on
three surfaces. Each owns its own expand/collapse state — a change to one
does not affect the others.

| Surface | Entry-point file(s) | State ownership |
|---|---|---|
| Main map screen FAB column | `:feature:map:impl/MapFabColumn.kt`, `MapViewModel.kt`, `MapUiState.kt` | `MapUiState.isRouteControlsExpanded`, toggled by `MapAction.ToggleRouteControls` in `MapViewModel` |
| Widget panel row | `:feature:widget:impl/WidgetPanelContent.kt`, `FloatingWidgetService.kt` | `FloatingWidgetService.routeExpandedFlow` (`MutableStateFlow<Boolean>`) |
| Floating map (in-widget) | `:feature:widget:impl/MapFloatingView.kt`, `WidgetPanelPresenter.kt` | `WidgetPanelPresenter.mapRouteControlsExpanded` (`MutableStateFlow<Boolean>`) — see below |

While a route replay is active, a `current/total` progress chip is pinned at the **bottom** of the widget panel icon list (after configurable features and extra sections, before debug stats) and at the bottom of the floating-map FAB column. Same `LocationRepository.routeProgress` source as the main map FAB column (@docs/features/routes.md, "Route progress"). Previous / Next on these surfaces use `RouteReplayEngine.jumpToNextWaypoint` / `jumpToPreviousWaypoint`; when Teleport between waypoints is on, the engine lingers at the jumped stop before the next automatic hop (@docs/features/routes.md, "Next / Previous Waypoint").

## Expanded-control hit testing

The compact widget overlay is `WRAP_CONTENT`. Putting extra buttons in a `Row` beside the
route / group / altitude icon widens the whole `Column`, so the transparent rectangle
above and below that row still captures touches (`FLAG_NOT_TOUCH_MODAL` only passes
events *outside* the window). `WidgetSidePopup` draws those extra controls in a
`TYPE_APPLICATION_SUB_PANEL` child window attached to the overlay's `windowToken`,
sized to the buttons, with `clippingEnabled = false` so it can draw outside the FAB
column. Parent-relative placement (`anchorRight`/`anchorTop`, flip left if the bar
would go off the right of the screen) keeps the bar flush with the icon and moving
with the widget when it is dragged — a sibling `TYPE_APPLICATION_OVERLAY` popup
stayed put. Taps on the game or map around the bar reach the app underneath.
Altitude's popup is `focusable` so the IME still works; the parent overlay still
clears `FLAG_NOT_FOCUSABLE` while altitude is expanded.

## Floating Map — Route Controls

When `AppFeature.MAP_FLOATING` is enabled, the floating map's FAB column includes a route button:

- **Shown when**: `AppFeature.ROUTES` is enabled for the map surface in Settings **or** a route replay is currently active.
- **Active state**: route icon turns green (`LjSuccess`) during `ROUTE_REPLAY` mode.
- **Expand controls**: tapping the route button expands two inline buttons to the left:
  - **Stop** — ends the replay immediately.
  - **Pause / Resume** — toggles replay pause state.
- **No replay active**: tapping the route button opens the floating routes picker (`showRoutesFloatingView()`), matching the main map screen's behaviour and the button's own "Open routes" label.
- **Expansion state ownership**: the expanded/collapsed flag lives in `WidgetPanelPresenter.mapRouteControlsExpanded`, **not** in a `remember` inside `MapFloatingView`. `showPanel()` builds a fresh `ComposeView` on every open, so composable-local state would reset to collapsed each time the map panel was reopened mid-replay — leaving the pause/stop controls unreachable. The presenter collapses the flag automatically once `mockMode` leaves `ROUTE_REPLAY`, so a new route never starts pre-expanded.
- **Settings gate**: `enabledMapFeatures` flows through `MapSharedState` so the floating map respects the same visibility toggle as the main map screen. Paste coordinates (`AppFeature.PASTE_COORDINATES`) is on both WIDGET and MAP, on by default (`DEFAULT_WIDGET_ENABLED` / `DEFAULT_MAP_ENABLED`), and uses the map-surface gate on the floating-map FAB column. The widget panel button opens `PasteCoordinatesFloatingView` (shared `PasteCoordinatesForm`): one valid point offers Teleport / Walk / Walk via roads; multiple points switch to route-labelled options and Start route. Save first as favorite remains separate from Save route. Roaming (`AppFeature.ROAMING`) is on both WIDGET and MAP, on by default on the widget (`DEFAULT_WIDGET_ENABLED`). Existing installs gain it via `mergeNewDefaultWidgetFeatures`: `LEGACY_WIDGET_SEEN_DEFAULTS` is the pre-paste set, and `WIDGET_PRE_ROAMING_SEEN_DEFAULTS` is the paste-era set, so roaming is unseen until 0.20.5. If `widget_seen_defaults` already contains `roaming`, the user ran a build where the widget button was a default and then turned it off — leave it off. Tap while idle opens `RoamingFloatingView` (Walk around the block + Planting). Tap while `MockMode.ROAMING` expands pause/stop via `WidgetSidePopup` (`roamingExpandedFlow`). A playing route blocks roam start; joystick toggle/lock and roam icons fade but stay readable on the black circle when that control is ignored. The widget eye shows or hides the overlay on its own (lock is not required). Joystick show/hide and lock stay tappable while faded (they still show and lock the overlay; movement no-ops until the route or roam pauses). Roam start stays a no-op while a route is playing. Capture coordinates (`AppFeature.CAPTURE_COORDINATES`) is MAP-only, on by default on the map FAB set, and opens the same helper page on the floating map; intercept does not require the overlay (@docs/features/capture-coordinates.md). Existing installs that persisted `enabledWidgetFeatures` without `PASTE_COORDINATES` still get it on upgrade (`mergeNewDefaultWidgetFeatures`); map FABs gain `CAPTURE_COORDINATES` the same way via `mergeNewDefaultMapFeatures`. Turning either off in Settings then sticks.

The floating map is a MapLibre `MapView` inside a `TYPE_APPLICATION_OVERLAY` window.
`rememberMapView(overlay = true)` (`:core:map`) sets `textureMode` so the map composites
over the app underneath. A default GLSurfaceView punches a transparent hole (location
dot and FABs visible, no streets). OSM tiles use the same `MapTileHttp.install` path as
the main map (@docs/features/map-tiles.md).

It opens as a compact window (82% of screen width, 58% of screen height, capped at 420 × 560 dp)
with a drag handle and expand button. The window itself uses those compact bounds with
`FLAG_NOT_TOUCH_MODAL`; it is not a full-screen transparent touch target, so taps outside it reach
the game underneath. Expand switches the existing window to the previous full-screen layout, and
shrink restores its compact position without recreating the `MapView`.

The floating map also has an always-visible **N + navigation arrow** north-lock button. North lock
starts on, immediately keeps bearing at 0, and disables rotation gestures while leaving pan gestures
available. Turning it off restores MapLibre's normal rotation gestures; it does not force a saved
bearing after unlock. The lock is local UI state for that floating-map instance. The floating map's
own center (my-location) button centers on the current spoofed position while spoofing is active;
when idle with no current position, it falls back to the last position the floating map's camera
was moved to (a tap-to-pin, route jump, etc.) instead of doing nothing.

## Group Sync Button

When the device is a Group Sync follower with follower mode enabled
(`GroupRepository.groupState`), the widget panel shows a group-sync icon
button:

- **Shown when**: `groupState.role == GroupRole.FOLLOWER && groupState.followerModeEnabled`.
- **Hidden**: for `GroupRole.LEADER` and `GroupRole.NONE`.
- **Expand control**: tapping the icon expands one inline button to the right —
  **Teleport to leader now** — which sends `ACTION_FOLLOWER_TELEPORT` to
  `MockLocationService`, the same action the Group Sync screen's own
  "Teleport to leader now" button sends (see @docs/features/group-sync.md).
  If a cooldown advisory applies (same distance-tiered `CooldownEngine` used
  elsewhere), `FloatingWidgetService` shows it as a one-shot Toast after
  tapping — the teleport still goes through, the Toast is advisory only. The
  icon-only row has no room for the persistent badge the Group Sync screen
  shows.
- **Auto-collapse**: the button (and its inline teleport action) disappears
  entirely once the device stops being an enabled follower — nothing is left
  on screen for the expand state to affect.

## Altitude Override Button

When enabled (Settings → Menus → Privacy → "Show altitude override button", `AppSettings.altitudeOverrideButtonEnabled`,
default `false`), the widget panel shows a terrain-icon button:

- **Shown when**: the setting is enabled.
- **Tap**: expands an inline decimal text field prefilled with the currently reported altitude
  (`LocationRepository.reportedAltitudeMeters`, falling back to
  `AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS` if not currently spoofing), plus a
  confirm (check) button.
- **Confirm**: calls `SettingsRepository.setBaseAltitudeOverride(value)` — `MockLocationService`
  picks it up via its existing reactive collector on `getBaseAltitudeOverride()`, with no new
  `Intent`/service command needed, then the row collapses.
- **Effect**: the override becomes the new base altitude the Gaussian walk clamps around,
  applied instantly (unlike a real-elevation fetch, which converges gradually — see
  @docs/features/mock-location.md, "Real Elevation Lookup"), and takes priority over both the
  35 m default and any real-elevation fetch. Setting it also suspends the periodic elevation
  fetch until cleared via Settings → GPS → "Reset elevation override".
- **Expansion state ownership**: `FloatingWidgetService.altitudeExpandedFlow`
  (`MutableStateFlow<Boolean>`), per the "Anti-Patterns to Avoid" rule below — never `remember`
  in `WidgetPanelContent` directly.
- **Keyboard focus**: the widget's overlay window is `FLAG_NOT_FOCUSABLE` by design (it must
  never steal keyboard focus from the foreground app — see @docs/features/joystick.md,
  "Requirements"), which otherwise silently prevents the text field from ever receiving IME
  focus, so no keyboard appears and the field cannot be typed into. `FloatingWidgetService`
  clears `FLAG_NOT_FOCUSABLE` on the overlay window (`windowManager.updateViewLayout`) only
  while `altitudeExpandedFlow` is `true`, restoring it once the row collapses — mirroring the
  fix already applied to the map panel's search field (`mapPanelLayoutParams`).

## Debug Stats

Settings → Menus → Debug → "Debug stats" (`AppSettings.debugStatsEnabled`, DataStore key
`debug_stats_enabled`, default `false`) shows a live text block in the widget panel while it's
expanded: coordinates, speed (m/s), altitude, accuracy, bearing, and tick rate (Hz).

- **Source**: `MockLocationService.pushLocationUpdate()` — the single 1 Hz tick every mode routes
  through (see @docs/features/mock-location.md, "Internal Architecture") — publishes a
  `LocationRepository.DebugStats` snapshot every tick, unconditionally (cheap: one data class
  alloc at 1 Hz). Tick rate is derived from the wall-clock delta between ticks, not assumed to be
  a fixed 1 Hz, so a throttled or delayed loop is visible instead of hidden.
- **Display gate**: `FloatingWidgetService` only passes the live `DebugStats` through to
  `WidgetPanel` when the setting is enabled — the collector itself always runs, matching every
  other reactive widget-panel toggle.
- **Unset bearing**: `DebugStats.hasBearing` mirrors `LocationFix.hasBearing` (see
  @docs/features/mock-location.md, "No bearing before first move") — before the first tick with
  motion in the session, the bearing segment reads "—" instead of a misleading "0°" (issue #58).

## Anti-Patterns to Avoid

- Do not store reopen-surviving panel state in a composable `remember`. `WidgetPanelPresenter.showPanel()` builds a fresh `ComposeView` on every open, so `remember` state resets to its initial value each time the panel reopens. Hoist the state to the presenter or service instead (a `StateFlow`), per the fix in PR #40 — see `mapRouteControlsExpanded` in "Floating Map — Route Controls" above for the concrete example.

## Edge Cases

- No items configured → show placeholder.
- Clamp panel to screen bounds.
- Re-clamp on `onConfigurationChanged`.
