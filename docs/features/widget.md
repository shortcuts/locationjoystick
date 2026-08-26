# Floating Widget

Small floating button overlay. Tap to expand a panel with configured quick-access controls. Items configured in Settings.

Key files: `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:settings:impl/SettingsScreen.kt`

## Mechanism

- Same overlay mechanism as the joystick via `:core:overlay`.
- Separate service, toggled independently of the joystick.
- State transitions: collapsed (FAB) ↔ expanded (panel) via `ValueAnimator`.
- Enabled features stored in DataStore as `stringSetPreferencesKey`; the shared display order (see below) is a separate `stringPreferencesKey`.
- `MockLocationService` auto-starts the widget overlay (alongside the joystick overlay) whenever spoofing starts and `SYSTEM_ALERT_WINDOW` is granted — there is no separate manual start button for it.

## Hiding the Overlay

Settings → Menus → Privacy → "Hide floating widget" (`AppSettings.hideWidgetOverlay`, DataStore key `hide_widget_overlay`, default `false`) stops `MockLocationService` from starting `FloatingWidgetService` when spoofing starts. The joystick overlay and any accessibility-based features (e.g. compass tracking, see @docs/features/tap-to-walk.md) are unaffected — this only hides the widget button/panel itself. Round-trips through `ExportData` like `hideTeleportFeatures`.

Live while spoofing is active: `MockLocationService.observeLocationState()` runs a reactive collector (`combine` of its own state and `getHideWidgetOverlay()`, decision via the pure `computeWidgetOverlayAction()` in `LocationLoopPolicy.kt`) that starts or stops `FloatingWidgetService` the moment the toggle changes, not just at the next RUNNING transition — flipping the setting mid-session removes (or restores) the overlay immediately.

## Configurability

Both the widget panel and the map screen's FAB column render the same `AppFeature` set (`:core:model/AppFeature.kt`), each feature declaring which surface(s) — `WIDGET`, `MAP`, or both — it's eligible for. Settings → Menus → "App Features" shows one combined, drag-to-reorder list: a drag handle, a checkbox to show on the widget (if eligible), and a checkbox to show on the map (if eligible).

A single shared `featureOrder` list controls display order on both surfaces, so they stay consistent by default — the user can still diverge enablement per surface, just not relative order. `SettingsRepository.getWidgetFeatures()` / `getMapFeatures()` filter+sort that shared order by each surface's enabled set.

## Service Lifecycle

- Binds to `MockLocationService` in `onStartCommand`.
- Unbinds in `onDestroy`.

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

## Floating Map — Route Controls

When `AppFeature.MAP_FLOATING` is enabled, the floating map's FAB column includes a route button:

- **Shown when**: `AppFeature.ROUTES` is enabled for the map surface in Settings **or** a route replay is currently active.
- **Active state**: route icon turns green (`LjSuccess`) during `ROUTE_REPLAY` mode.
- **Expand controls**: tapping the route button expands two inline buttons to the left:
  - **Stop** — ends the replay immediately.
  - **Pause / Resume** — toggles replay pause state.
- **No replay active**: tapping the route button opens the floating routes picker (`showRoutesFloatingView()`), matching the main map screen's behaviour and the button's own "Open routes" label.
- **Expansion state ownership**: the expanded/collapsed flag lives in `WidgetPanelPresenter.mapRouteControlsExpanded`, **not** in a `remember` inside `MapFloatingView`. `showPanel()` builds a fresh `ComposeView` on every open, so composable-local state would reset to collapsed each time the map panel was reopened mid-replay — leaving the pause/stop controls unreachable. The presenter collapses the flag automatically once `mockMode` leaves `ROUTE_REPLAY`, so a new route never starts pre-expanded.
- **Settings gate**: `enabledMapFeatures` flows through `MapSharedState` so the floating map respects the same visibility toggle as the main map screen.

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

When enabled (Settings → GPS → "Show altitude override button", `AppSettings.altitudeOverrideButtonEnabled`,
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

## Anti-Patterns to Avoid

- Do not store reopen-surviving panel state in a composable `remember`. `WidgetPanelPresenter.showPanel()` builds a fresh `ComposeView` on every open, so `remember` state resets to its initial value each time the panel reopens. Hoist the state to the presenter or service instead (a `StateFlow`), per the fix in PR #40 — see `mapRouteControlsExpanded` in "Floating Map — Route Controls" above for the concrete example.

## Edge Cases

- No items configured → show placeholder.
- Clamp panel to screen bounds.
- Re-clamp on `onConfigurationChanged`.
