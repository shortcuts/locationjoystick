# Location Links

Get externally-shared location links into the app. Two tiers—automatic and opt-in—both route through the same confirm sheet (Teleport / Walk / Do nothing).

## Overview

**Automatic tier** (always on):
- Receives: `geo:`, `google.navigation:`, own domain (`https://locationjoystick.shrtcts.fr`), Google Maps shares (`ACTION_SEND` text/plain)
- Setup: None
- Outcome: Confirm sheet to Teleport / Walk / Do nothing

**Opt-in tier** (Capture mode):
- Receives: Any map link when this app is the default browser and Capture mode is on
- Setup: Set this app as default browser + turn on supported links
- Actions: List (collect), Jump (teleport), or both; pass-through browser when off
- Outcome: List appends, Jump teleports, then confirm sheet (when Capture off or link unparseable)

## Automatic Tier: Deep Links & Location Sharing

### URL Format

```
https://locationjoystick.shrtcts.fr/?lat=LAT&lon=LON
```

| Parameter | Type | Range | Description |
|-----------|------|-------|-------------|
| `lat` | `Double` | `[-90, 90]` | Latitude |
| `lon` | `Double` | `[-180, 180]` | Longitude |

Custom scheme equivalent (app-to-app):

```
locationjoystick://open?lat=LAT&lon=LON
```

Both formats are parsed identically. HTTPS is preferred for general sharing. The custom scheme is useful for automation and app-to-app workflows.

### Generating Share Links (UI)

A **Share** icon (`LjShareLocationAction`) expands inline into **Copy coordinates** (decimal degrees to the clipboard) and **Share link** (the HTTPS URL as plain text through the system share sheet). It appears in four places:

- Map and floating-map FAB columns, directly below the recenter / search button: shares the current mock position (toasts "No current location" when there is none).
- Map long-press sheet (`PendingTapSheet`) and floating-map tap panel (`TapActionPanel`): shares the tapped point.

Helpers live in `core/common/util/ShareCoordinates.kt` (`locationShareLink`, `shareLocationLink`, `copyCoordinatesToClipboard`). From the floating map, **Share link** also closes the overlay so the chooser is not hidden behind it. The old long-press "Share this location" button is gone.

Favorites no longer generate that URL. The favorite overflow menu copies decimal-degree coordinates to the clipboard (**Copy coordinates**, same `copyCoordinatesToClipboard` helper) or sends the same text through the system share sheet (**Send as message**).

### Receiving Automatic Links

When the link is opened:

1. App opens (or comes to foreground via `singleTask` launch mode).
2. Map screen activates.
3. Map pans to the coordinate.
4. Confirmation sheet appears: Teleport / Walk here / Walk via roads / Do nothing.

The coordinate is never acted on automatically — the user must confirm. Confirming Teleport / Walk also opens the app chosen under "Launch Another App After a Link", if any.

If the link's coordinates can't be parsed or no URL/coordinates can be extracted from the shared text, a snackbar reading "Couldn't open that link" is shown instead of silently doing nothing.

### Supported Link Formats

These intent filters are always active (not part of Capture mode):

| Format | Example |
|---|---|
| `geo:` URI | `geo:35.62,139.77` |
| `geo:` URI with placeholder base + `q` param | `geo:0,0?q=35.62,139.77(Landmark)` — `q` takes priority over the `0,0` base, since many apps emit a placeholder base coordinate |
| `google.navigation:` scheme | `google.navigation:q=35.62,139.77` |
| `maps.google.com` (any path) | `https://maps.google.com/maps?q=35.62,139.77` |
| `www.google.com/maps*` | `https://www.google.com/maps/search/?api=1&query=35.62,139.77` |
| `www.google.com/maps/@LAT,LON,Zoomz` path form | `https://www.google.com/maps/@35.62,139.77,15z` |
| `www.google.com/maps/place/.../data=!3dLAT!4dLON` (place link) | `https://www.google.com/maps/place/Name/@1.0,2.0,15z/data=!3d50.305571!4d2.792041` — `data=!3d!4d` is preferred over the `@` segment, since `@` is just the last camera/viewport position and can diverge from the actual shared place |

### Google Maps "Share" Button

The Maps app's **Share** button uses Android's `ACTION_SEND` share sheet (text/plain), a separate mechanism with its own intent filter. The shared text usually contains a shortened link (`https://goo.gl/maps/...` or `https://maps.app.goo.gl/...`) with no embedded coordinates — the real lat/lon only exists in the page the shortener redirects to. This app detects these hosts and follows the HTTP redirect chain to recover the final long URL before parsing coordinates.

### Domain Verification

`locationjoystick.shrtcts.fr` uses `android:autoVerify="true"`. For HTTPS links to open the app directly (no disambiguation dialog), `/.well-known/assetlinks.json` must be served at that domain with the release signing fingerprint. Without it, Android shows a chooser — links still work but require user selection.

### Third-Party Integration

Any tool can construct a link without an API key or authentication:

```
https://locationjoystick.shrtcts.fr/?lat=35.6762&lon=139.6503
```

Rules:
- Both `lat` and `lon` are required. Missing either silently drops the link.
- Coordinates outside valid range (`lat` > 90 or < −90, `lon` > 180 or < −180) are rejected.
- Standard URL percent-encoding applies for any unusual characters, though coordinates are pure ASCII.

## Opt-In Tier: Capture Mode

Collect map links from other apps into an on-device list, then save them as a route. This tier intercepts links **only when Capture mode is on** and this app is the default browser.

### Setup

Capture mode is **off** by default. Capture is a top-level destination listed on Home and in the navigation drawer next to Map / Routes / Favorites.

Setup lives in **Settings > General > Capture**. The app verifies only one setup fact: whether it is the default browser. This is the only gate. While gated, the Capture screen shows one card with an **Open Capture setup** button; it navigates to `SETTINGS_CAPTURE_ROUTE` (Settings opened on Menus, scrolled to Capture; Back returns to the Capture screen). Captured points stay stored while gated and reappear after setup.

**Setup steps:**
1. Tap **Default browser** to open Android's Default apps settings. Set this app as **Browser app**. (On Samsung, use the Default apps settings directly rather than a role-request dialog.)
2. Return to Settings — it rechecks automatically.
3. Turn on **supported links** for this app in its Android settings. The app cannot verify this, so verify it yourself.
4. Turn off **Open supported links** for Google Maps. With this off, Maps links come to this app instead of opening Maps directly.

Once setup is complete, the setup cards disappear and Settings shows the toggle and controls; the Capture screen shows the full list UI.

### Configuration

After setup is complete:

- **Capture mode** — overall switch; off by default. When enabled for the first time, a dialog asks whether to **Clear** existing points or **Keep** them.
- **List** / **Jump** — independent checkboxes (both optional). List appends each tapped location to the list; Jump teleports immediately via `TeleportUseCase`. Both can be on, off, or one each.
- **Pass-through browser** — opens an in-app browser picker, used when Capture mode is off or both List and Jump are off (see the intercept table below).

Capture mode, List, and Jump are **not** part of `ExportData` and are per-device. They reset when this app is no longer the default browser, managed through **Restore default browser** in Settings > General > Capture.

### Also in Settings

The Capture toggles, List/Jump checkboxes, and pass-through browser picker are also available on the Capture screen. Both surfaces read and write the same `CaptureCoordinatesRepository`, so changes made in either place reflect everywhere. Setup steps and **Restore default browser** exist in Settings only.

### Intercept Behavior

`LinkInterceptorActivity` (`singleInstance`, `excludeFromRecents`, `noHistory`) catches all `http`/`https` VIEW + BROWSABLE intents and Google Maps hosts. Unlike `MainActivity`, it does not bring focus back to the app, letting the calling app stay in front.

`decideCaptureLink(captureModeEnabled, listEnabled, jumpEnabled, parsedCoords)`:

| Mode | List | Jump | Parsed coords | Action |
|------|------|------|---------------|--------|
| On | On | Off | yes | Append point, toast, finish; the calling app stays in front |
| On | Off | On | yes | Teleport with `TeleportUseCase`, toast, finish; do not append |
| On | On | On | yes | Append, then teleport with `TeleportUseCase`, toast, finish |
| Off | Either | Either | either | Forward to pass-through browser without capture or teleport |
| On | Off | Off | either | Forward to pass-through browser without capture or teleport |
| On | Either | Either | no | Forward to the chosen pass-through browser |

When both List and Jump are off or Capture mode is off and coordinates parse, the confirm sheet (Teleport / Walk / Do nothing) is shown, routing through the same outcome as automatic tier links.

Coordinates are parsed with `parseUrlCoords` / `parseDeepLinkCoords` (in `DeepLinkParser.kt`), including implicit VIEW intents on Google Maps search URLs. Short Maps share links are resolved via `GoogleMapsShortLinkResolver` before parse.

### Browser Forwarding

The app cannot forward to Android's current default browser while it is itself the default, so it stores the browser that held the role before setup and lets the user choose another installed browser on the Capture page. Forwarding excludes this app to prevent a loop.

With Capture mode off, Google Maps web links are first sent explicitly to the Google Maps package; this still works when Maps' supported-links switch is off. If Maps is unavailable, the selected browser is used.

Browser discovery combines installed web-link handlers with apps that advertise a browser launcher. This matters while the app owns Android's browser role: some phones return only the current role holder for a generic web query even though other browsers remain installed. The list refreshes when the Capture screen resumes after a system Settings change.

### Collecting and Saving

Captured points are listed as numbered rows in a scrolling list card (Copy, Last and Clear sit below it as three equal buttons), skipping exact duplicates of the last point. Point order defaults to **Optimize proximity** (nearest-neighbor ordering, the same option used when pasting coordinates into a route). Select **Keep original** instead to save in capture order.

**Copy** / **Last** (remove most recent) / **Clear** sit on one icon row above the route-name field. **Save as route** appears below the name field and requires ≥2 points. The new route is added to Routes with the specified name.

**Restore default browser** (Settings > General > Capture) clears Capture mode, List, and Jump, and opens Android's Default apps settings so the user can restore their usual browser. It sets a per-device flag so the setup cards return even though the app still holds the browser role. Captured points, the pass-through browser, and the stored previous browser holder are kept (Android will not reassign the previous browser back programmatically). The flag clears when the user taps the default-browser card again or when this app is no longer the default browser.

### Launch Another App After a Link

**Settings > General > Capture > After a map link, open** picks an installed launchable app (default: None). After any handled link — a Capture Jump teleport in `LinkInterceptorActivity`, or Teleport / Walk / Walk via roads confirmed on the confirm sheet for a link-pinned point — `LaunchAfterLinkUseCase` starts that app so focus returns to the game instead of staying here. Capture-only (List) and "Do nothing" never launch. A missing, disabled or self-package target is a silent no-op (logged). Tap-to-teleport and pasted coordinates never launch: `MapViewModel` only arms the launch for a pin that came from `observeDeepLinkCoords`.

The package is stored per-device in DataStore (`CaptureCoordinatesRepository.launchAfterLinkPackage`), is not in `ExportData`, and survives **Restore default browser**. The picker lists apps through the existing `MAIN`/`LAUNCHER` `<queries>` entry in the manifest; no `QUERY_ALL_PACKAGES`.

## Implementation

| Layer | Detail |
|-------|--------|
| **Automatic Tier Entry** | `MainActivity.handleIntent` → `parseDeepLinkCoords` (in `DeepLinkParser.kt`) |
| **Automatic Channel** | `DeepLinkRepository` — `SharedFlow(replay=1)`; `consume()` calls `resetReplayCache()` to clear after delivery |
| **Automatic Consumer** | `MapViewModel.observeDeepLinkCoords` — pins via `pinCoordinateTarget` (`pendingTapPosition` + `pendingCameraTarget` + confirm sheet), the same path as map paste-coordinates |
| **Automatic URL builder** | `AppConstants.AppInfo.buildDeepLink(lat, lon)` |
| **Automatic Manifest** | Intent filters on `MainActivity`: HTTPS own domain (`autoVerify`) + custom scheme + `geo:` + `google.navigation:` + `ACTION_SEND` text/plain |
| **Capture Manifest** | Intent filters on `LinkInterceptorActivity`: catch-all `http`/`https` VIEW + BROWSABLE, and Google Maps hosts |
| **Capture Intercept** | `LinkInterceptorActivity.kt`, `CaptureCoordinatesRepository.kt` (`:core:data`) |
| **Capture UI** | `CaptureCoordinatesForm` + `CaptureSetupSteps` + `CaptureRestoreDialog` (`:core:designsystem`) + `SettingsMenusSubScreen` (hosts setup) (`:feature:settings:impl`) |
| **Launch after link** | `LaunchAfterLinkUseCase` (`:core:data`), called by `LinkInterceptorActivity` and `MapViewModel` |
| **Capture List** | `CaptureCoordinatesViewModel.kt` (`:feature:map:impl`), `CaptureLink.kt` (`:core:common/util`) |
