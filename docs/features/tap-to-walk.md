# Tap to Walk

Two shortcuts for triggering walk-to without a confirmation sheet, useful when you want to act quickly inside another app.

Key files: `:feature:widget:impl/MapFloatingView.kt`, `:feature:widget:impl/TapToWalkOverlay.kt`, `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:settings:impl/SettingsScreen.kt`

## Settings

Tier 1's toggle lives in Settings → Menus → Privacy, next to "Hide teleport features" — it moved
there because Floating Map Quick Walk is its own standalone feature, not part of the Tap to Walk
umbrella (see @docs/features/hide-teleport.md; it is not one of the features that toggle hides).
Tier 2 and compass tracking stay in Settings → Menus → Tap to Walk.

| DataStore key | Type | Default | Description |
|---|---|---|---|
| `FLOATING_MAP_QUICK_WALK` | Boolean | `false` | Skip confirmation sheet on floating map taps (Settings → Menus → Privacy) |
| `TAP_TO_WALK_OVERLAY_ENABLED` | Boolean | `false` | Show crosshair button in widget panel |
| `TAP_TO_WALK_SCALE_MPX` | Double | `0.23` | Meters per pixel for pixel→GPS conversion (calibrated for a fully zoomed-out AR game map) |
| `COMPASS_TRACKING_ENABLED` | Boolean | `false` | Capture compass heading before each tap |
| `COMPASS_TEST_TARGET_PACKAGE` | String | `""` | Package the "Compass orientation" Test button switches to automatically. Empty = manual switch |

Scale is clamped to `AppConstants.TapToWalkConstants.MIN_SCALE_MPX`–`MAX_SCALE_MPX` (0.01–1.0 m/px) in `applySnapshot()`.

`COMPASS_TRACKING_ENABLED` is live-persisted (written directly to DataStore, not through the
save/discard draft). There is no compass-region setting — see "Compass Orientation" below;
detection auto-locates the icon fresh on every call, so nothing needs to be stored.

### Map Scale

Settings → Menus → Tap to Walk shows a "Map scale (m/px)" slider, prefilled
with `AppConstants.TapToWalkConstants.DEFAULT_SCALE_MPX` (0.23 m/px) —
empirically calibrated against a fully-zoomed-out AR game map and accurate
for most players out of the box, with no setup step required. Players whose
game's zoom differs (or who play a different AR/GPS game) can drag the
slider to correct the scale; the label reminds them to zoom their game
fully out first, since accuracy depends on the scale setting matching the
game's actual zoom level. Range: `MIN_SCALE_MPX`–`MAX_SCALE_MPX` (0.01–1.0
m/px).

An earlier version required tapping two landmarks on the live game and
typing the real-world distance between them to compute the scale — removed
because it asked most players (who don't know the exact distance between
two arbitrary map landmarks) for information they don't have. The
already-accurate default plus a manual slider replaces it.

## Tier 1 — Floating Map Quick Walk

`MapFloatingView` exposes a `quickWalk: Boolean` parameter. When `true`, `addOnMapClickListener` calls `onWalkTo(pos)` directly instead of setting `pendingTap` to open `TapActionPanel`.

`WidgetPanelPresenter.showMapFloatingView()` reads `settingsRepository.getFloatingMapQuickWalk()` as a composable state and passes it to `MapFloatingView`.

Uses `rememberUpdatedState` for both `quickWalk` and `onWalkTo` so the `AndroidView.factory` callback (which runs once) always captures the latest values.

## Tier 2 — Screen Tap-Intercept Overlay

`TapToWalkOverlay` adds a `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_FOCUSABLE` but **without** `FLAG_NOT_TOUCH_MODAL`, so it intercepts all touches on the screen.

`FloatingWidgetService` shows a crosshair button in `WidgetPanel` when `getTapToWalkOverlayEnabled()` is true (added to the panel's `sections` list). Tapping the button calls `onTapToWalkClicked()`, which creates and shows a `TapToWalkOverlay`.

The overlay shows:
- Semi-transparent background (5% black) that captures all taps
- Hint text at top center
- Cancel button (close icon, bottom-right) that dismisses the overlay

### Pixel → GPS Formula

```
dx_m = (tapX - screenW/2) * metersPerPixel
dy_m = -(tapY - screenH/2) * metersPerPixel
newLat = currentLat + (dy_m / 6_371_000) * (180/π)
newLon = currentLon + (dx_m / 6_371_000) * (180/π) / cos(currentLat * π/180)
```

**North-up assumption**: the formula assumes the game map is north-up. Accuracy depends on the scale setting matching the game's actual zoom level.

`TapToWalkOverlay.computeWalkTarget(...)` is a `companion object` pure function — no Android deps, testable directly.

The function accepts an optional `northAngleRad` (default `0.0` = north-up). When compass tracking is active the pre-captured heading is passed here:

```
geo_east  = rawDx·cos(θ) − rawDy·sin(θ)
geo_north = rawDx·sin(θ) + rawDy·cos(θ)
```

where θ = `northAngleRad` (clockwise from screen-up to geographic north).

## Compass Orientation

When enabled, `TapToWalkOverlay` takes a screenshot immediately after `show()` and auto-locates the
game's compass needle icon to detect its heading. The heading is available by the time the user
taps (1.5 s budget; falls back to north-up if not ready). **No manual calibration**: an earlier
version asked the user to drag a circle onto the compass and store its position/radius — that
required precise calibration to avoid picking up unrelated red UI elements (a gym marker, a raid
egg) nearby, and a bad calibration made detection "confused" (issue reported after that version
shipped). Detection now re-locates the icon fresh on every call instead, immune to nearby clutter
by construction — see below.

Key files: `:core:location/CompassHeadingSource.kt`, `:feature:widget:impl/CompassAccessibilityService.kt`

### CompassHeadingSource

`@Singleton` bridge owned by `:core:location`. `CompassAccessibilityService` calls `bind(this)` on connect and `unbind()` on disconnect. `FloatingWidgetService` calls `captureHeading()` which delegates to the live service; `SettingsViewModel.testCompassDetection()` calls the same method for the Settings screen's Test button (see "Verifying On-Device" below).

### CompassAccessibilityService

`@AndroidEntryPoint AccessibilityService` in `:feature:widget:impl`. Injects `CompassHeadingSource`. Implements `CompassAccessibilityServiceBridge`.

`captureHeading()` calls `takeScreenshot(Display.DEFAULT_DISPLAY, ...)` via `suspendCancellableCoroutine`, then calls `detectNorthAngle()` on the result.

`detectNorthAngle(bitmap)` is a `companion object` pure function, no calibration input:

1. Bulk-reads (`Bitmap.getPixels`, not per-pixel `getPixel` — much faster) a fixed search window —
   the right `SEARCH_X_MIN_PCT` (55%) / top `SEARCH_Y_MAX_PCT` (35%) of the screen
   (`AppConstants.CompassTrackingConstants`) — where every tested AR/GPS-spoofing game places its
   compass (confirmed on-device against a popular AR/GPS-spoofing game).
2. Filters pixels by the same red-hue test as before (HSV hue < 15° or > 345°, sat > 0.5, val > 0.3).
3. Runs 4-connected-component labelling (`findBestIconBlob`, BFS-based) over the red mask, keeping
   only blobs whose bounding box falls within `MIN_ICON_FRACTION`–`MAX_ICON_FRACTION` (0.8%–6%) of
   the screen's short side and whose pixel count clears `MIN_RED_PIXELS` (20) — this is what makes
   detection immune to a stray red pixel (too small) or an unrelated large red UI element like a
   gym marker or raid egg (too big) landing in the search window, unlike scanning a big fixed
   circle for the reddest pixel. The largest surviving blob wins.
4. The needle's pivot is approximated as the bottom-center of the winning blob's bounding box —
   empirically where the icon's rotation center sits, right at the base of its colored half (the
   red top sits directly above the icon's grey center dot in every game tested). The angle is
   `atan2(centroid.x - pivot.x, -(centroid.y - pivot.y))`, i.e. clockwise from screen-up to the
   centroid-from-pivot direction — self-calibrating every call, nothing stored between calls.

Hardware bitmaps are copied to `ARGB_8888` before pixel access and recycled after use.

### Verifying On-Device

Settings → Menus → Tap to Walk → "Compass orientation" → **Test** button lets the user confirm
detection works on their device/game without leaving Settings mid-session.

A "Game app" picker (`SettingsViewModel.launchableApps`, queried once via
`PackageManager.queryIntentActivities` on `ACTION_MAIN`/`CATEGORY_LAUNCHER`, excluding this app)
lets the user select their game once — persisted live as `COMPASS_TEST_TARGET_PACKAGE`. With an
app selected, tapping Test launches it directly (`getLaunchIntentForPackage` +
`FLAG_ACTIVITY_NEW_TASK`) instead of relying on the user having switched to it themselves.

Since `captureHeading()` screenshots whatever is *currently on screen* — which would otherwise be
the Settings UI itself, not the game behind it — the Test button waits 700 ms after switching for
the game to render, captures, then relaunches the app's own task
(`FLAG_ACTIVITY_REORDER_TO_FRONT`) to return. Reports "Detected — north is N° from up" or "Not
detected — make sure your game's compass is visible top-right".

**No app selected (picker left on "Select app…")**: falls back to the original flow — the user
switches to the game themselves, switches back to Settings, then taps Test, which briefly calls
`Activity.moveTaskToBack(true)` (revealing the game, which sits directly behind Settings in the
task stack after that switch sequence) instead of launching an intent.

### Anti-cheat caveat

Accessibility services running in the background are detectable by some games. The Settings UI discloses this. Disable compass tracking if the game penalises it.

### Requires

`android.permission.BIND_ACCESSIBILITY_SERVICE` — granted by Android when the user enables the service in system Accessibility Settings. No runtime prompt needed.

API 30 (`takeScreenshot`) — no fallback exists below it. On API 28–29, the "Compass orientation" Settings section is hidden and `CompassAccessibilityService.onServiceConnected()` skips binding, so compass tracking is unavailable; the rest of Tap to Walk (Tier 1 quick-walk, Tier 2 overlay) works unchanged.

## Warning Dialog

Enabling the overlay shows an `AlertDialog` with three caveats before activating:
1. Some apps detect mock location activity and may penalise the account.
2. Accuracy depends on the scale setting matching the game's zoom level.
3. Zoom out for better precision; zoomed-in maps amplify offset errors.

User must confirm "Enable anyway" or cancel. Cancel leaves the toggle off. State is local (`rememberSaveable`) so the dialog re-shows if the user disables and re-enables.

## Anti-Patterns to Avoid

- Do not add `FLAG_NOT_TOUCH_MODAL` to the overlay — this breaks interception.
- Do not re-read position inside the tap callback — capture it once via `getPosition()` at tap time.
- Do not persist `isTapToWalkActive` in DataStore — it is transient session state in `MutableStateFlow`.
- Do not block the tap callback waiting for heading — pre-capture in `show()` with a timeout instead.
- Do not call `getPixel` on a hardware bitmap — copy to `ARGB_8888` first via `bitmap.copy(Bitmap.Config.ARGB_8888, false)`.
- Do not add `COMPASS_TRACKING_ENABLED` to `SettingsSnapshot` / `applySnapshot` — it is live-persisted, not save/discard.
