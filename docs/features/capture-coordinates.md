# Capture Coordinates

Collect map links from other apps into an on-device list, then save them as a route. Overlay is
not required — capture is a background intercept while this app is the default browser.

Key files: `:app/LinkInterceptorActivity.kt`, `:core:data/CaptureCoordinatesRepository.kt`,
`:feature:map:impl/CaptureCoordinatesViewModel.kt`, `:core:common/util/CaptureLink.kt`,
`:core:designsystem/component/LjGuidedStepCard.kt`

## Behaviour

Capture mode is **off** by default. Capture is a top-level destination (`CAPTURE_ROUTE`),
listed on Home and in the navigation drawer next to Map / Routes / Favorites. It is **not**
a map FAB. `shouldSkipIdleRedirect` includes `CAPTURE_ROUTE` so the page stays when the user
leaves for Android settings or another app.

`CaptureCoordinatesForm` (`:core:designsystem`) renders one of two mutually exclusive views, keyed on
`CaptureSetupState.isDefaultBrowser` (`isCaptureDefaultBrowser()`, refreshed on `ON_RESUME`). It is the
only setup fact the app can verify, so it is the only gate. Captured points stay stored while gated
and reappear after setup; the gate is view-only.

### Setup incomplete (not the default browser)

Only onboarding-style step cards (`LjGuidedStepCard`, shared with @docs/features/onboarding.md's
permission cards) and a **Setup guide** text button are shown. No Capture mode switch, List/Jump,
captured list, pass-through row or Save as route.

- **Default browser** — action opens Default apps (`ACTION_MANAGE_DEFAULT_APPS_SETTINGS`) so the
  user can set **Browser app** to this app. `RoleManager.createRequestRoleIntent(ROLE_BROWSER)` is
  a no-op on many OEMs (including Samsung) and is not used.
- **Supported-links hint** — plain text under the default-browser card telling the user to also turn
  on supported links for this app. The app cannot verify it, so it has no button and never gates.
- **Turn off Google Maps supported links** — action opens Google Maps' "Open by default" screen.
  Not checked separately: it completes together with the default-browser step.
- **Setup guide** — opens the wiki Capture page (`AppConstants.AppInfo.CAPTURE_GUIDE_URL`), the same
  pattern as onboarding's Troubleshooting button.

### Setup complete (this app is the default browser)

The setup cards disappear entirely and the feature UI shows a one-line intro (`capture_intro`,
which also says links pass through when neither List nor Jump is on) and:

- **Capture mode + List / Jump** — one overall DataStore switch followed by two independent
  checkboxes (`CaptureCoordinatesRepository`; not part of `ExportData`). List appends a point; Jump
  teleports immediately through `TeleportUseCase`. Either action, both together, and neither are
  supported. If captured points already exist when the overall mode is enabled, a dialog asks
  whether to **Clear** (primary/default action) or **Keep** them before enabling.
- A **Pass-through** row opens an in-app browser picker, used when a captured link's mode doesn't
  list or jump it (see the intercept table below).
- Lists captured points in an orange-outlined read-only box (skip exact duplicate of the last point)
- Point order defaults to **Optimize proximity** (`orderedCapturedPoints` → `orderByProximity`,
  same nearest-neighbor as paste coordinates). The on-screen list stays in capture order so
  **Last** (remove last) is unambiguous; proximity is applied when copying or saving the route.
- **Copy** / **Last** / **Clear** sit on one icon row immediately above the route-name field
  (gap between Copy and the remove actions) and Save as route
- Save as a straight route via `RouteRepository.insertRoute` when there are ≥2 points

There is no "Ready" banner, pass-through banner, point-order hint, or step-number badge.

### Top-bar overflow menu and restore

The top bar carries a three-dot menu (`LjOverflowMenu`) with **Setup guide** (opens the wiki page)
and **Restore default browser**, both always shown. Restore opens a "Restore default browser?" dialog
whose **Restore** button stays disabled until the user ticks a checkbox acknowledging that Capture
turns off and the default browser must be set back in Android settings. On confirm,
`CaptureCoordinatesRepository.resetSetup()` writes Capture mode, List and Jump off and sets a
per-device `capture_coordinates_setup_reset` flag (DataStore, not part of `ExportData`), then Default
apps settings opens. While the flag is set the gate treats setup as incomplete, so the setup cards
return even though this app still holds the browser role. Captured points, the pass-through browser
and the stored previous `ROLE_BROWSER` holder are kept (Android will not assign the previous browser
back programmatically, so the app needs them to offer it as a pass-through choice). The flag clears
when the user taps the default-browser card again or when this app is no longer the default browser.

The floating widget overlay is **not** part of intercept. Link handling depends on the
overall **Capture mode** switch and List/Jump actions, not overlay visibility.

## Intercept

`LinkInterceptorActivity` (`singleInstance`, `excludeFromRecents`, `noHistory`, catch-all
`http`/`https` VIEW + BROWSABLE, **and** the Maps host filters that used to live on
`MainActivity`). Catch-all is **not** on `MainActivity` — `MainActivity` keeps `geo:` /
`google.navigation:` / share for chooser → pin. Maps `https` filters were moved off
`MainActivity` so a captured URL does not open the map screen or take focus from the
calling app.

`decideCaptureLink(captureModeEnabled, captureEnabled, jumpEnabled, coords)`:

| Mode | List | Jump | Parsed coords | Action |
|------|------|------|---------------|--------|
| On | On | Off | yes | Append point, toast, finish; the calling app stays in front |
| On | Off | On | yes | Teleport with `TeleportUseCase`, toast, finish; do not append |
| On | On | On | yes | Append, then teleport with `TeleportUseCase`, toast, finish |
| Off | Either | Either | either | Forward without capture or teleport |
| On | Off | Off | either | Forward without capture or teleport |
| On | Either | Either | no | Forward to the chosen pass-through browser |

Coordinates are parsed with `parseUrlCoords` / `parseDeepLinkCoords` (`DeepLinkParser.kt`),
including `https://www.google.com/maps/search/?api=1&query=LAT,LON` through implicit VIEW
intents. Short Maps share links are resolved via
`GoogleMapsShortLinkResolver` before parse.

The app cannot forward to Android's current default browser while it is itself the default, so it
stores the browser that held the role before setup and lets the user choose another installed
browser on the Capture page. Forwarding excludes this app to prevent a loop. With Capture mode off,
Google Maps web links are first sent explicitly to the Google Maps package; this still works when
Maps' supported-links switch is off. If Maps is unavailable, the selected browser is used.

Browser discovery combines installed web-link handlers with apps that advertise a browser launcher.
This matters while the app owns Android's browser role: some phones return only the current role
holder for a generic web query even though other browsers remain installed. The list refreshes when
the Capture screen resumes after a system Settings change.

## Defaults

Capture mode, List, and Jump default **off**. Upgrading from the earlier two-toggle design keeps the
overall mode on when either old action was on. Point order defaults to **Optimize proximity**. The
`AppFeature.CAPTURE_COORDINATES` map-FAB flag may still exist in Settings on older installs
but is no longer rendered on the map or floating map.

The widget panel exposes Capture only as an anchored long-press shortcut on its paste button; the
controls and captured list remain on the main-app Capture screen. Intercept does not require the overlay.
