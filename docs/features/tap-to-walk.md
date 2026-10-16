# Tap to Walk

Two shortcuts for triggering walk-to without a confirmation sheet, useful when you want to act quickly inside another app.

Key files: `:feature:widget:impl/MapFloatingView.kt`, `:feature:widget:impl/TapToWalkOverlay.kt`, `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:settings:impl/SettingsScreen.kt`

Bench record and measured results: [tap-to-walk-investigation.md](tap-to-walk-investigation.md).

## Settings

Tier 1's toggle lives in Settings → General → Privacy, next to "Hide teleport features" — it moved
there because Floating Map Quick Walk is its own standalone feature, not part of the Tap to Walk
umbrella (see docs/features/hide-teleport.md; it is not one of the features that toggle hides).
Tier 2 stays in Settings → Menus → Tap to Walk. Compass tracking has no toggle — it is always
on whenever the accessibility service is granted (see "Compass Orientation" below).

| DataStore key | Type | Default | Description |
|---|---|---|---|
| `FLOATING_MAP_QUICK_WALK` | Boolean | `false` | Skip confirmation sheet on floating map taps (Settings → General → Privacy) |
| `TAP_TO_WALK_OVERLAY_ENABLED` | Boolean | `false` | Show crosshair button in widget panel |
| `TAP_TO_WALK_SCALE_MPX` | Double | `0.23` | Meters per pixel for pixel→GPS conversion (calibrated for a fully zoomed-out AR game map) |
| `COMPASS_TEST_TARGET_PACKAGE` | String | `""` | Game app picked in Settings → Menus → Tap to Walk. The calibration walk launches it. Empty = Start calibration disabled |
| `TAP_TO_WALK_GROUND_CALIBRATION` | String (JSON) | absent | Latest calibration walk result (`StoredGroundCalibration`, see "Calibration walk"). Per-device, not part of `ExportData`; cleared by Reset All Data |
| `COMPASS_DISCLOSURE_CHOICE` | String | `""` | The user's answer to the accessibility disclosure: `""` (unanswered), `accepted`, `declined` (`AppConstants.CompassTrackingConstants`). Per-device, not part of `ExportData` |

Scale is clamped to `AppConstants.TapToWalkConstants.MIN_SCALE_MPX`–`MAX_SCALE_MPX` (0.01–1.0 m/px) in `applySnapshot()`.

There is no compass-region setting — see "Compass Orientation" below; detection auto-locates the
icon fresh on every call, so nothing needs to be stored.

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

Primary path (ground map): when a stored calibration (`StoredGroundCalibration`, see "Calibration walk")
matches the full display size, the tap pixel goes through `GroundMap.pixelToGround`, and the resulting
east/north offset is applied with `CalibrationWalkMath.commandedTarget` (bearing = `atan2(east, north)`,
distance = `hypot`) to the current position. No screenshot or heading is read in the tap callback: the map
comes from the cached stored calibration (`FloatingWidgetService.groundCalibration`).
`TapToWalkOverlay.computeGroundWalkTarget(...)` is the pure companion function (null past the horizon).

Fallback (no matching calibration, other screen size, API < 30, or tap past the horizon): the north-up
scale formula, never blocking the tap. No compass is read.

```
dx_m = (tapX - screenW/2) * metersPerPixel
dy_m = -(tapY - screenH/2) * metersPerPixel
newLat = currentLat + (dy_m / 6_371_000) * (180/π)
newLon = currentLon + (dx_m / 6_371_000) * (180/π) / cos(currentLat * π/180)
```

Accuracy of the fallback depends on the scale setting matching the game's zoom and the map being north-up.
`TapToWalkOverlay.computeWalkTarget(...)` is a `companion object` pure function — no Android deps, testable directly.

Invariants a change must not break:

- Tap pixel is in full-display device pixels: `getLocationOnScreen` of the overlay plus the in-window tap, never the overlay window size (system bars may inset it). The calibration is fitted on full-display screenshots and `matches(w, h)` compares against `maximumWindowMetrics`.
- Ground frame is north-up metres east/north of the feet, flat earth at 111320 m/deg (the walk's frame), not the 6,371,000 m haversine radius used by the fallback.
- Fallback is north-up by design: nothing assumes or reads a compass; heading comes from the walked-leg fit only.
- The fallback must stay while nothing user-visible starts a calibration walk; when it does, that entry must dismiss the overlay first.

## Compass Orientation

`TapToWalkOverlay` no longer reads the compass heading: taps use the ground map (see "Pixel → GPS
Formula"). The service, `CompassHeadingSource.captureHeading()` and the shared icon detection remain, and
Reconnect accessory (docs/features/bluetooth-reconnect-tap.md) shares the detection. Settings no longer calls
`captureHeading()` (the Test button is gone), so it has no Settings caller. The text below describes that detection. **No manual calibration**: an earlier
version asked the user to drag a circle onto the compass and store its position/radius — that
required precise calibration to avoid picking up unrelated red UI elements (a gym marker, a raid
egg) nearby, and a bad calibration made detection "confused" (issue reported after that version
shipped). Detection now re-locates the icon fresh on every call instead, immune to nearby clutter
by construction — see below.

Key files: `:core:location/CompassHeadingSource.kt`, `:feature:widget:impl/CompassAccessibilityService.kt`

### CompassHeadingSource

`@Singleton` bridge owned by `:core:location`. `CompassAccessibilityService` calls `bind(this)` on connect and `unbind()` on disconnect. `captureHeading()` delegates to the live service and retries up to 3 times, 500 ms apart, on any null result.

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

The Settings Test button was removed. Settings no longer runs a compass check. Verify on device through the
calibration walk (see "Calibration walk").

A "Game app" picker (`SettingsViewModel.launchableApps`, queried once via
`PackageManager.queryIntentActivities` on `ACTION_MAIN`/`CATEGORY_LAUNCHER`, excluding this app)
lets the user select their game once — persisted live as `COMPASS_TEST_TARGET_PACKAGE`. The calibration walk
launches that app.

### Prominent Disclosure

Play's Accessibility API policy rejected releases that showed the disclosure only in a settings
sub-screen, or that let the accessibility service run without an in-app accept. `CompassDisclosureDialog`
(`:core:designsystem`) is the single disclosure surface: a full-screen dialog that names the
AccessibilityService API, the data it reads (screenshots, with their cadence: about one every 3 seconds for
about 10 minutes during a calibration walk the user starts, about one a minute while Reconnect
accessory is on), the purpose (measuring how the game's map moves, the accessory
state), and that calibration screenshots stay on the phone only until the calibration ends, other
screenshots are deleted at once, and nothing is collected or shared. Play's policy requires the periodic
screenshot to be named in this text, in the onboarding card and in the service description. Its title and body say
"Accessibility" explicitly, state that everything is off by default, that no screenshot or tap happens while
the features (Tap to Walk, Reconnect accessory) are off, and that the app works fully after "Decline". It cannot be dismissed by back press or outside tap — only "Accept" or
"Decline" (the wording from Play's prominent-disclosure guidance), plus a small "Skip" at the
bottom right on the onboarding prompt only (`onSkip`). The body text scrolls while the buttons
stay fixed at the bottom, so they are always visible. Only "Accept" opens
`ACTION_ACCESSIBILITY_SETTINGS`; Skip consents to nothing and opens nothing. A "Read the Tap to
Walk guide" link opens `AppConstants.AppInfo.TAP_TO_WALK_GUIDE_URL` (the wiki page), so the
in-app disclosure stands on its own and the long-form explanation stays one tap away.

Play also rejected a build (2400) whose disclosure was reachable only through Settings menus:
the disclosure must sit in the normal usage flow. It therefore appears at every point where a user
reaches the feature, always immediately before Android's own accessibility consent screen, and
the first of them is onboarding, which every new install walks through:

| Entry point | Where |
|---|---|
| Onboarding (on entry) | `OnboardingRoute` opens the dialog over the permission screen (first screen of a fresh install, every API level, no path gate) while the stored choice is unanswered. `PathPickerStep` keeps a fallback auto-show (unanswered, API >= 30, path not Pikmin Bloom). Buttons: Accept (records `accepted`, opens Android's Accessibility settings), Decline (`declined`), Skip (`skipped`). Every button stores an answer, so it never auto-shows again. Installs that finished onboarding before this shipped never reach it |
| Onboarding card | Optional "Tap to Walk setup" card on the path picker step (every API level, skip line first, hidden on Pikmin Bloom, never blocks "Start"). "Review and turn on" reopens the same disclosure (`PathPickerStep.kt`) |
| Turning Tap to Walk on | The Tap to Walk switch opens the same screen, with the two overlay caveats above the disclosure text and "Accept" as the accept label (`SettingsMenusSubScreen.kt`). Below API 30 only the caveats are shown, with "Enable anyway" |
| Tapping the widget crosshair | `FloatingWidgetService.onTapToWalkClicked()` launches `MainActivity` with `EXTRA_SHOW_COMPASS_DISCLOSURE`; the overlay opens on the next tap |
| Settings compass row | "Open Settings" in `CompassOrientationSection` |
| Calibration | Calibration needs the service already granted, so it adds no new consent path |

Play Console side is done and needs no re-checking: declaration form, per-submission video,
store-listing mention, and no stale artifacts on other tracks. Rejections after build 2400 came from
reviewers not reaching the in-app disclosure, hence the onboarding auto-show. It is on the first-run
screen every new install walks through, and the service stays gated on an in-app accept regardless.
The Map never auto-shows it, so existing installs see no change.

The answer is persisted in `COMPASS_DISCLOSURE_CHOICE` (`accepted`, `declined`, `skipped`, or empty
when unanswered). Only the onboarding auto-show is gated on "unanswered"; the Tap to Walk switch, the
crosshair and the Settings row keep asking until the choice is `accepted`, so a Decline or Skip
never locks the feature out. Once the choice is `accepted`, the Tap to Walk switch and the Settings
compass row's "Open Settings" button skip the disclosure and act directly (`showsTapToWalkEnableDisclosure`,
`showsCompassOpenSettingsDisclosure` in `SettingsMenusSubScreen.kt`).

`CompassHeadingSource.captureHeading()` returns `null` until the choice is `accepted`. The consent
check lives there, not at the call sites, because Android's accessibility settings can enable the
service without the app ever being opened (see `CompassHeadingSourceTest`).

### Anti-cheat caveat

Accessibility services running in the background are detectable by some games. The Settings UI discloses this. Turn off the accessibility service in system Accessibility Settings if the game penalises it — there is no separate in-app toggle.

### Requires

`android.permission.BIND_ACCESSIBILITY_SERVICE` — granted by Android when the user enables the service in system Accessibility Settings. No runtime prompt needed.

API 30 (`takeScreenshot`) — no fallback exists below it. On API 28–29, the "Compass orientation" Settings section is hidden, and the service itself is disabled in the manifest (`android:enabled="@bool/compass_accessibility_service_enabled"`, `false` in `values/bools.xml`, `true` in `values-v30/`), so it never appears in Android's Accessibility settings where it could be turned on without the in-app disclosure. `CompassAccessibilityService.onServiceConnected()` also skips binding as a second guard. The rest of Tap to Walk (Tier 1 quick-walk, Tier 2 overlay) works unchanged.

The disclosure, the onboarding step and the service's system-settings description are localized in every supported locale with identical keys (docs/features/localization.md). The Chinese strings keep the English word "Accessibility" in parentheses next to 无障碍 so a reviewer testing in any locale sees the term Play asks for.

### Ground map model

`GroundMap` in `:core:model` (`GroundMap.kt`, pure Kotlin, no Android) is the app-side port of the bench's
`ground_to_game` / `tap_to_ground_persp`. It only maps; the walked-leg fit (see "Frame flow and ground fit") and feet detection are separate.

- Frames: screen x right, y down, in the device's own pixels; ground east +x, north +y (north up), metres relative to the
  player's feet pixel. No compass is read; `headingDeg` (fitted, degrees) is how far north is rotated in the frame.
- Calibration (`GroundCalibration`): `headingDeg`, `lateralPxPerM`, `forwardPxPerM`, `perspective` (1/m), `feetX`,
  `feetY`. All pixel values are reference pixels (1080x2340), not device pixels.
- Screen scaling: a device pixel is scaled to the reference frame by `x * 1080 / width`, `y * 2340 / height` (and
  back for `groundToPixel`), so one calibration serves any screen size or density.
- API: `pixelToGround(x, y)` returns east/north metres or `null` past the horizon line (divisor <= 0);
  `groundToPixel(east, north)` is the inverse. A non-positive screen size throws.
- No row-skew term: the validated perspective model has none (skew belonged to the superseded 2x2 map).
- Valid range: the fit was only checked for rows 700 to 1420 of 2340 and moves up to about 31 m; anything else is
  extrapolated, not rejected.

Invariants a change must not break:

1. The formula equals the bench: `H = camH * rot(heading)`, `camH = [[s,0,px],[0,-sy,py],[0,-a,1]]`, tap offset
   `Hinv(p) - Hinv(feet)` with homogeneous divide. Tests pin it to bench goldens within 1e-6 m
   (`scripts/export_ground_map_golden.py`; rerun when `ground_to_game` changes).
2. Reference size stays 1080x2340; stored calibrations are in reference pixels.
3. Units are px per metre, degrees, metres; screen y is down, ground north is up.
4. `groundToPixel(pixelToGround(p)) == p` (round trip).

### Feet detection

`FeetDetector` in `:core:model` (`FeetDetector.kt`, pure Kotlin) ports the bench `detect_feet`. Input: per walked leg,
the gray frames recorded while moving (`GrayFrame`, row-major 0..255, device pixels, at least 3 per leg). Output:
`FeetPixel` in device pixels, or `null`; `toReference(width, height)` converts to the 1080x2340 frame of
`GroundCalibration.feetX/feetY`. Nothing calls it yet (the walked-leg calibration entry will).

Method: per leg take the per-pixel temporal median (the world scrolls, the player stays put, so only the player and
HUD stay sharp), blur 3x3, Sobel gradient magnitude; take the minimum over legs (drops roads parallel to one walk);
edge = above 30. In the centre columns 0.42w to 0.58w, sum edges per row with a box of `max(3, 0.005h)`. In rows
0.4h to 0.9h group rows into runs split by gaps over 0.05h (dark legs break the figure), keep the run with the most
support (reject under `0.0005 * h * k`), drop rows under 0.1 of the run peak. x = mean edge column in the run; y =
last run row minus 0.07 of the run height (sole edge to ground contact; the one tuned value). Then, if that row is
more than 0.03h from the prior 0.6415h, the row is the screen-fixed range ring merged into the figure's run (NYC smoke,
1695 against about 1556), so the prior is used instead and the fallback is printed by the bench. x is kept.

Failure modes: a shadow below the sole lifts the last row; a companion sprite beside the player joins the run and
shifts x; close-up zoom puts the anchor too high (see open work 2, not gated in code); a stationary HUD element in the
centre column reads as figure; all legs parallel to one road keep road edges. Fallback: `null` means calibration
failed, store nothing, except that legs with moving frames never end in `null`: the sprite faces each walk
direction, so the minimum over the app's eight legs can erase it (Pixel 7, NYC: calibration was rejected), and
`FeetDetector.fallback` then returns the centred prior (0.5w, 0.6415h). The row prior (0.6415h, tolerance 0.03h) is the
only default anchor: it applies when the detected row is outside the tolerance or nothing is detected (feet differ per device and zoom: Pixel 5 542,1725 of 1080x2340; Pixel 7
538,1556 of 1080x2400). Each use of the prior is a fallback to count; a frequent one counts as a failed registration.

Invariants a change must not break:

1. Constants and order equal the bench (`scripts/registration_bench.py` `detect_feet`); tests pin a bench-exported
   scene within 1.0 px (`scripts/export_feet_golden.py`; rerun when `detect_feet` changes).
2. Input and output are device pixels; only `toReference` scales. No screen size, density or north is hardcoded.
3. Bench and Kotlin change together: the row prior and tolerance are the same constants in both (`PRIOR_FEET_FRAC`, `PRIOR_TOLERANCE`).

### Frame flow and ground fit

`PhaseCorrelation`, `FrameFlow` and `GroundMapFit` in `:core:model` (pure Kotlin, no OpenCV, no Android) port the
bench's `content_flow`, `clean_leg`, `band_moves` and `fit_perspective_horizon`. The calibration walk
(`CalibrationWalkOrchestrator`, see "Calibration walk") calls them through `GroundCalibrationSolver`. They turn walked-leg frames plus the mock position of each frame into a
`GroundCalibration`; `FeetDetector` supplies the feet pixel.

Data flow, in order:

1. Caller supplies per leg a `WalkLeg`: gray frames (`GrayImage`, 8-bit, luma `0.299R + 0.587G + 0.114B`, device
   pixels), a moving flag per frame, and each frame's mock position as `GroundOffset` metres east/north of any
   origin (only differences are used; the orchestrator derives them from the commanded bearing and the polled
   position, as the bench's `along_track_move` does).
2. `FrameFlow.cleanLeg(frames, feetX, feetY)` removes leg-static content. A pixel is static when its deviation
   from the temporal median is under 3 gray levels and it sits on an edge of that median (Sobel magnitude over 60);
   the static mask is dilated 9x9 and joined with the player disc (radius 110 px, centre 60 px above the feet).
   Masked pixels are filled in every frame. Only rows 700/2340 to 1950/2340 of the height are cleaned.
3. `FrameFlow.bandMoves(legs, feetX, feetY)` cuts the cleaned frames into 7 row bands (0.21h to 0.70h) x 3 column
   thirds, builds the local-contrast feature of each band and phase-correlates every pair of frames `k` apart
   (`k` in 2, 3, 4, 6) that were both moving and moved at least 2 m. Output per pair: the known move and per band
   `fx, fy` (device px, content shift of the later frame), `response`, band centre.
4. `GroundMapFit.fit(moves, feetX, feetY, width, height)` converts flows and band centres to reference pixels and
   fits `(heading, lateral, forward, perspective)` of the pitched-camera homography; `GroundFit.calibration` feeds
   `GroundMap`. Returns `null` under 40 usable flows.

Thresholds and why (`FrameFlow`, `PhaseCorrelation`, `GroundMapFit` constants; pixel sizes are scaled by
`width / 1080`, rows and bands are fractions of the height):

| value | where | why |
|---|---|---|
| feature: blur 6 px, subtract; energy blur 10 px, divide by `sqrt + 4`; blur 1.5 px | `FrameFlow.feature` | local-contrast luminance: removes ground colour and brightness, the 4 gray-level floor keeps flat areas from being amplified |
| static: deviation < 3, edge > 60, dilate 9x9 | `cleanLeg` | HUD, ring and companion do not move with the ground; a bare phase correlation locks onto them and shrinks every shift |
| disc radius 110 px, 60 px above feet | `cleanLeg` | player sprite and its shadow, measured on the Pixel 5 at wide zoom |
| rows 700..1950 of 2340 | `cleanLeg` | below the HUD, above the bottom bar |
| 7 x 3 bands over 0.21h..0.70h | `bandMoves` | the full 14-column grid measured worse on the wide Pixel 7 legs (p90 13.4 m against 9.7 m) |
| frame gaps 2, 3, 4, 6; move >= 2 m | `bandMoves` | shorter moves give flows too small to fit |
| response floor 0.2 | `GroundMapFit` | the value of the validated `persp` path (below that the peak is noise) |
| outlier cut `max(3 * 1.4826 * MAD, 2.0)` px as weights `c / abs(r)`, recomputed at Gauss-Newton iterations 8, 20, 30 | `GroundMapFit` | robust to a few bands that still see static content |
| at least 40 flows | `GroundMapFit` | four parameters from fewer flows are not pinned |
| 7 starts `theta0` in -20, 5, 30, -60, 60, 120, 180 | `GroundMapFit` | the heading is unknown (no north); best start by `sqrt(mean(min(r^2, 100)))` |
| horizon refit: drop bands whose centre row is at or above `sy / a` plus 0.03h, once | `GroundMapFit` | close-up zoom shows the horizon; bands near it have no coherent flow |
| Hann window `sqrt(wx * wy)`, padding to 2^a 3^b 5^c, cross power divided by `abs + 2.2e-16`, 5x5 centroid, response = 5x5 sum | `PhaseCorrelation` | exactly `cv2.phaseCorrelate` with `createHanningWindow` (measured: its 2-D window is the square root of the outer product) |

The entry text quoted 0.1 for the response floor and `max(3 MAD, 1.5 px)` for outliers. Those are the values of the
superseded 2x2 map (`fit_ground_map`, `multileg`). The perspective path that produced the landing numbers below
uses 0.2 and `max(3 MAD, 2.0)`, so that is what is ported; do not "fix" it back.

Deviations from the bench, each checked by a test:

- Fill instead of `cv2.inpaint` (Telea): masked pixels are filled ring by ring from the mask boundary with the mean of
  valid 4-neighbours, then 40 Gauss-Seidel smoothing sweeps. Without the sweeps the fill copies boundary streaks
  inward; they are static in position and pulled a band's flow to zero (measured on the synthetic scene). Bands
  holding a cleaned HUD therefore differ in response from the bench.
- Band-disc weighting (fixes a bench defect): bench far-row bands crossing the player disc kept full weight. A band
  overlapping the disc has its response multiplied by the unmasked fraction of the band and set to 0 below 0.5, so
  it cannot bias the forward scale.
- Reported camera is the positive-scale one: `(s, sy, a, theta)` and `(-s, -sy, -a, theta + 180)` are the same
  homography and the bench returns either; the port flips to `s > 0`.
- `GroundFit.flowCount` is the number of flows used; the bench's `n` is half of that.
- Not ported: the patch-match landing check and its edge/ambiguity guard (`patch_offset`, open work 1). It is
  bench trial tooling for scoring taps, not part of the calibration.

Parity target (offline landing check above, bench `landing`): p50 about 2.5 m, 96 to 99 percent of samples within 15 m
at wide zoom across three camera orientations. The Kotlin unit tests pin the stages, not those end numbers: flow per
band within 1.0 px of the bench and response within 0.15 (except bands with a cleaned HUD) on a procedural
1080x2340 scene, fitted heading within 1.5 degrees, scales within 3 percent and the perspective term within 5 percent
of the bench fit on synthetic flows with outliers. The cross-device parity suite (see "Parity fixtures" below) now pins the fit and the tap offsets of each recorded device to the bench. The landing percentages themselves still need on-device or longer offline evidence.

Failure modes: fewer than 40 flows or a singular fit return `null` (calibration failed, store nothing); a feet pixel
that is wrong by tens of pixels puts the disc mask on the wrong place and leaves the sprite in the flow; frames that
are static for the whole leg (not moving) give no flows; very few frames (under about 8) let chance "static" speckle
into the mask (the 9-frame test scene is the minimum that was clean).

Invariants a change must not break:

1. Units: `fx, fy` and band centres in device pixels out of `bandMoves`, converted to 1080x2340 reference pixels
   only inside `GroundMapFit.fit`; `GroundCalibration` is in reference pixels; moves in metres east/north (north up).
2. No pixel constant is device-specific: sigmas, disc and dilation scale by `width / 1080`, rows and bands are
   fractions of the height; the feet pixel is an argument, never a default.
3. Response is not normalised across implementations: the 0.2 floor was tuned on cv2's value, so keep the window,
   padding and epsilon of `PhaseCorrelation` (a plain Hann product lowers it by about 20 percent; a 1e-7 epsilon by a third).
4. Goldens: `scripts/export_flow_golden.py` writes `core/model/src/test/resources/flow_golden/`; rerun it when
   `clean_leg`, `band_moves`, `_feat` or `fit_perspective*` change. The scene is procedural (no image files).

### Parity fixtures

`DeviceParityTest` (`core/model`) replays bench numbers recorded per device: the band flows (device pixels), the feet
pixel, the bench perspective fit (reference pixels) and the tap-to-ground offsets of a probe grid. Fixtures live in
`core/model/src/test/resources/device_golden/<device>/` and are written by `scripts/export_device_golden.py`; the
regeneration command and the tolerances are in docs/testing.md, "Bench parity fixtures".

Provenance (values from the exporter's output, read from `meta.txt`, `fit.txt`; `captured_on` is the exporter's required `--captured-on` argument, the "legs captured" column):

| fixture | model | resolution | density | camera state | legs captured | bench legs | feet pixel (device px) | bench fit: heading, lateral px/m, forward px/m, perspective, median residual, n |
|---|---|---|---|---|---|---|---|---|
| `pixel5_1080x2340` | Pixel 5 | 1080x2340 | 440 dpi | wide zoom, no twist (bench persp run; not recorded at capture) | 2026-10-13 | `data/walk_C_000`, `045`, `090` | 537.6, 1714.6 | -0.94 deg, 11.17, 1.48, -0.00144, 5.24 px, n 1422 |
| `pixel7_1080x2400` | Pixel 7 | 1080x2400 | 420 dpi | wide zoom, no twist (bench persp run; not recorded at capture) | 2026-10-15 | `persp_pixel7_20261015/walk_C_000`, `045`, `090`, `225` (bench worktree `tap-to-walk-no-compass`) | 536.5, 1557.7 | 1.94 deg, 7.68, 2.90, -0.00165, 3.36 px, n 1995 |

The Pixel 5 feet pixel measures 537.6, 1714.6 on the current detector, not the 542, 1725 quoted in the detection notes
above; the fixture follows the current detector. The Pixel 5 fixture uses the three-leg set in `data/`; the four-leg
Pixel 5 set in the same worktree is not used. Both sets are gitignored bench data, so only the numbers are committed.

What the fixtures do not cover, on purpose:

- `FrameFlow` and `FeetDetector` on real game frames. Frames are never committed: the game HUD shows the player's
  name, and the bench folder is git-ignored. Those stages stay pinned by the procedural goldens in `flow_golden/` and
  `feet_golden/`.
- Close-up zoom and any screen other than the two above. A new device needs a new fixture directory and a test entry.

Invariants a change must not break:

1. Units: the fixture flows and taps are device pixels; the first line of `fit.txt` is reference pixels (1080x2340).
   The exporter converts with `x * 1080 / width` and `y * 2340 / height` before `fit_perspective_horizon`, the same
   conversion `GroundMapFit.fit` makes, so the expected fit and the Kotlin fit share one frame.
2. The test passes the device feet pixel (`fit.txt` line 2) and the screen size from `meta.txt`. It never runs
   `FeetDetector`, so a detector change shows up only in the exporter output and the `fit.txt` values it writes.
3. Tap offsets are checked through a `GroundMap` built from the Kotlin fit, not from the bench fit, so a drift in
   either the fit or the map fails the test.
4. Tolerances are fixed: heading 1.5 degrees, lateral and forward scale 3 percent, perspective 5 percent, median
   residual 0.3 px, flow count equal to `2 * n`; tap offsets within the larger of 1.0 m and 3 percent of the bench
   offset length. A failing test is a port defect: fix the port, do not widen the tolerance.
5. Fixtures hold numbers only. Adding a frame, a screenshot or a pixel dump is out of scope.

### Calibration walk

`CalibrationWalkOrchestrator` (`:core:location`, `@Singleton`) walks the mock position, screenshots the game,
runs the solver and stores one map. The tap handler reads the stored result via
`SettingsRepository.getGroundCalibration()`; the user starts it from Settings, see "Calibration UX" below.

Data flow:

1. `start()` checks readiness (`NOT_READY` otherwise): spoofing `RUNNING`, mode `JOYSTICK` or `TELEPORT`, no
   walk target, a position. It then takes one probe frame through `CompassHeadingSource.captureGrayFrame()`
   (null: `SERVICE_UNAVAILABLE`, which also covers an unbound service and a disclosure not accepted) and
   checks free cache space for `8 legs x 36 frames x width x height` bytes (`STORAGE`).
2. Eight legs, bearings 0, 180, 90, 270, 45, 225, 135, 315, each 40 m from where the previous leg ended
   (`MapController.walkTo(target, WALK_SPEED_MPS)`; flat-earth target, 111320 m per degree, as the bench's
   `commanded_target`). About 320 m at 0.5556 m/s is about 10 minutes. Opposite bearings in sequence bring the
   player back to the start, so there is no return walk. It never calls `TeleportUseCase`: no teleport, no
   cooldown timestamp written, and the game's speed warning (which teleports of 25 to 120 m triggered) is not
   provoked.
3. One screenshot per 3 s tick (`captureGrayFrame`: 8-bit luma in device pixels). The frame's position is the
   mean of `currentPosition` just before and just after the screenshot; its time is seconds since the leg's
   first tick. A null screenshot is skipped. Frames go to `cacheDir/tap_calibration/<leg>/<i>.gray` and only
   metadata stays in memory (24 to 36 frames x 8 legs x 2.5 MB would not fit the heap; one leg is about 60 MB).
   The directory is emptied at start and on every exit path.
4. A leg ends when `walkTarget` clears after having been set. It counts only if the player is within 5 m of
   the leg target; otherwise `INTERRUPTED`.
5. `GroundCalibrationSolver.solve` loads one leg at a time: moving frames (neighbour speed at least 0.3 m/s),
   `FeetDetector.legGradient` per leg, `FeetDetector.detectFromGradients`, per leg `FrameFlow.bandMoves` with
   along-track offsets (`CalibrationWalkMath.alongTrackOffset`), then `GroundMapFit.fit`. The result is stored
   as one `StoredGroundCalibration` before `Done` is emitted.

Stored record: fitted `GroundCalibration` (heading is the camera orientation; lateral, forward and perspective
are zoom and pitch) plus the screenshot `screenWidth x screenHeight`, `calibratedAtMs`, `flowCount`,
`medianAbsPx`. It is one record (latest success), not a family keyed by zoom or orientation: the game exposes no
signal to read its zoom or rotation at tap time (see [tap-to-walk-investigation.md](tap-to-walk-investigation.md), "Game-camera orientation signals"), so a keyed record
could never be looked up. "Per device" holds because DataStore is on-device and a stored map is rejected by
`matches(width, height)` on another screen size. It is a standalone key outside `SettingsSnapshot`, so it is never
exported or imported (docs/features/export-import.md) and a restored backup cannot carry a wrong map to a phone.

Failure reasons (`CalibrationFailure`; any one stores nothing, deletes the frames and stops the walk):

| reason | when |
|---|---|
| `NOT_READY` | start preconditions above |
| `SERVICE_UNAVAILABLE` | accessibility service unbound, disclosure not accepted, probe screenshot rejected |
| `STORAGE` | free cache space under the estimate |
| `TIMEOUT` | a leg longer than 2 x (40 m / 0.5556 m/s), about 144 s (a joystick takeover pauses the walk and lands here) |
| `SPEED_ABORT` | more than 3 m/s between two consecutive frames (also catches a teleport mid-run) |
| `INTERRUPTED` | walk target cleared more than 5 m from the leg target, or the position was lost |
| `FRAME_SIZE_CHANGED` | a screenshot differs in size from the probe (rotation) |
| `FEET_NOT_FOUND` / `TOO_FEW_FLOWS` | solver: no figure in the temporal median, or fewer than 40 usable flows |
| `IMPLAUSIBLE_FIT` | a fit converged but a scale is under 1 reference px per metre: the screen did not follow the walk (game in the background shows this app or a frozen frame; a static screen fits about 0.003 with a perfect residual, which the bench would have accepted) |

Cooldown and anti-cheat: the walk never teleports, so it neither reads nor writes the teleport cooldown and does
not look like a jump to the game. The periodic screenshots are as detectable as the compass capture (see
"Anti-cheat caveat"); they go through the same disclosure gate as `captureHeading()`.

#### Calibration UX

Lives in Settings -> Menus -> Tap to Walk, as `GroundCalibrationSection` directly under the compass block
(`SettingsMenusSubScreen.kt`), shown on API 30+ once Tap to Walk is on and the accessibility service is granted.
State comes from `SettingsViewModel.calibrationState` (the orchestrator's `state`) and `groundCalibration`; both
sit outside the draft/save `SettingsUiState` because a calibration is not a setting.

- Start button is disabled until the game app (the Game app picker) is chosen. On tap the view model checks
  `CalibrationWalkOrchestrator.isReady` first: not ready shows `NOT_READY` without leaving Settings. Otherwise it
  launches the game, waits `CALIBRATION_GAME_FOREGROUND_DELAY_MS` (3 s) and calls `start()`. The wait matters:
  `run()` takes its probe frame at once, and a probe of Settings fits a static screen (`IMPLAUSIBLE_FIT`).
- Progress: "Walking leg L of 8" with a bar at `(L-1)/8`, then "Analyzing step S of N". Cancel calls `cancel()`.
- End: `Done` or `Failed` shows a toast over the game and brings the app's task to front, because the user spends
  the ten minutes inside the game. The same message stays in the section.
- Failure copy: one string per `CalibrationFailure`, selected by the exhaustive `calibrationFailureMessageRes`
  (`CalibrationCopy.kt`), so a new enum entry fails to compile until it has text.
- Status line: no record is "Not calibrated. Taps assume the map is north-up."; a record shows date and
  measurement count; a record whose size differs from the display shows "Saved map is for a different screen
  size. Recalibrate." (the same `matches` test as the tap overlay).
- Recalibration is manual. Zoom and rotation cannot be read at tap time (see [tap-to-walk-investigation.md](tap-to-walk-investigation.md), "Game-camera orientation
  signals"), so the button reads "Recalibrate" and the help text says to run it after changing the game's zoom,
  rotation or the phone's display size.
- The tap overlay hint reads "Not calibrated: taps assume north-up. Calibrate in Settings." when no usable
  record exists, the one place a player sees the fallback happen.

Invariants of the UX: the game must be in front before `start()`; readiness is checked before leaving the app;
the start never teleports and the copy tells the user not to touch the joystick, zoom or rotate; the disclosure
names the periodic screenshot (below).

Invariants a change must not break:

1. Speed is `WALK_SPEED_MPS` (0.5556), not the user's walk profile (`speedMsOverride` through `WalkToEngine`):
   it is the speed the bench legs were validated at, and a faster user profile risks the game's speed warning.
2. Geometry uses 111320 m per degree and the bench's moving rule (0.3 m/s over the neighbouring frames, clamped at
   the ends); `CalibrationWalkMathTest` pins it to `scripts/export_calibration_walk_golden.py` output within
   1e-6 m. Do not swap in the haversine radius (111195 m per degree): it drifts 0.1 percent from the goldens.
3. The feet pixel goes to `bandMoves` and `fit` in device pixels; only `GroundMapFit.fit` converts to reference.
4. The full walk is never held in memory: frames are spooled and the solver asks for one leg at a time (twice:
   once for the feet gradient, once for the flows).
5. The stored map is valid only for the screenshot size it was fitted at.
6. Spool ceiling: raw frames are about 0.7 GB on disk at 1080x2340 during a run; compress or crop only if devices
   report `STORAGE`.
7. Opposite legs in the synthetic test differ in scroll size on purpose: identical frame sets give identical
   temporal medians, which defeat the detector's min-over-legs rejection of world texture. Real walks never
   repeat terrain exactly.

### Status and open work

Accepted state of the no-compass investigation (branch `tap-to-walk-no-compass`; the bench record is in [tap-to-walk-investigation.md](tap-to-walk-investigation.md)):

- Heading and scale come out of a walked-leg fit with no north assumed (`persp`).
- Offline landing error is p50 about 2.5 m, with 96 to 99 percent of samples within 15 m, at wide zoom across three camera orientations.
- Not proven: close-up zoom (measured 2026-10-15, not met, see below), other devices and screen sizes beyond the one below, and the end-to-end landing numbers of the Kotlin port (its stages are pinned to the bench by unit tests).

On-device result, perspective map, fixed wide zoom, acceptance 11 of 12 taps within 15 m (2026-10-15):

| device | resolution, density | feet pixel | calibration | taps | within 15 m | p50 / p90 (m) | verdict |
|---|---|---|---|---|---|---|---|
| Pixel 7 (model Pixel 7) | 1080x2400, 420 dpi | 538 1556 (by eye) | n 1247, median residual 3.3 px | 9 run | 7 of 9 | 4.1 / 21.6 | FAIL (first run, before the match-ambiguity guard) |
| Pixel 7 (model Pixel 7) | 1080x2400, 420 dpi | 538 1556 (by eye) | n 1247, median residual 3.3 px | 36 walked, 12 scored, 24 skipped | 11 of 12 | 5.2 / 13.9 | PASS (11/12) |

The first run stopped after 9 taps, when 11 of 12 could no longer be reached. The two misses landed 21.6 m and 18.6 m
off; the first had a match score of 0.59 and the second 0.90, so the second is a confident mismatch to check
in its annotated frames. Taps closer than 5 m are not drawn (they barely test the walk). The bench constants
were fitted on a Pixel 5 (1080x2340); the bench now scales them to the screen's own width and height, and the
tap rows are taken from the device's own ground band, so this is the first run on a second screen.

The second run added a guard: a match is skipped, never counted, when its peak sits on the search edge or the
second-best peak more than 25 px away reaches 1.1 times the best. Of 36 walked taps, 24 were skipped this way and
12 were scored; 11 of those 12 landed within 15 m, and the one miss landed 23.2 m off (annotated frames `t17`).
That skip rate is high. The pass rate covers only the taps the bench could match, not every tap
the player tried, so it is not yet proof of reliability. It is one device, one camera state, and the calibration
came from the same session. Not run: the Pixel 5 re-check, close-up zoom, and repeat runs for variance.

Open work, in order:

1. Lower the skip rate (24 of 36 taps on the second Pixel 7 run) and explain the 23 m miss (`t17`), then repeat the run on this device and on more devices and camera states.
2. Close-up (z1.6) landing is not met: p90 96.9 m on the Pixel 7, target under 10 m. Measured with `varycap --conditions 1.6:0` then `landing` without `--anchor`: feet pixel detected 509 1548, 928 samples, p50 22.6 m, p90 96.9 m, within 10 m 20%, within 15 m 34%, 66% of matches skipped as ambiguous. The wide capture on the same device gives p50 3.0 m, p90 9.9 m with the detected anchor. The detected feet row looks too high at close-up (sharp rows span 1149 to 1911, the detector stops at the torso/leg gap); widening the run gap to 0.12 moved the anchor to 1796 and made landing worse (p90 286.8 m), so the anchor is not the main error: the fit and the match step at close-up are. Needs a second device and a cause for the ambiguity at close-up before retrying.
3. Ground map model ported (`GroundMap`, see "Ground map model"), feet detection ported (`FeetDetector`, see "Feet detection"), frame flow and fit ported (`FrameFlow`, `GroundMapFit`, see "Frame flow and ground fit"); the walked-leg capture and orchestration ported (`CalibrationWalkOrchestrator`, see "Calibration walk"); the tap overlay now reads the stored ground map (fallback north-up without one). The on-device analysis time is unmeasured (the pure port was only benchmarked on the host).
4. Prove the pass rate across devices, screen sizes, zooms and rotations before the epic closes. The proof command is `make tap-smoke` (docs/testing.md, "Tap-to-walk global smoke gate"): 20 fixed locations, 12 taps each, bar 19 of 20 locations with 11 of 12 within 15 m. A failing gate blocks the port entry and the "Prove tap-to-walk 100% reliable" entry. No full run has been recorded yet. The parity suite shows the port matches the bench on each recorded device; it does not show that the pass rate holds.

Measured 2026-10-16 (Pixel 7, `make tap-smoke ONLY=1`, New York Times Square, wide zoom): the range-ring merge put the
feet row at 1695 (about 1556 true), so calibration errors were 19.7 and 20.5 m on the failing taps. With the row prior
the row is 1540 (prior fallback fired once), and the same location gives 2 passes (7.5, 10.3 m), 1 fail (16.8 m),
1 unverified (14.0 m), 2 ambiguous, stopped at below_bar. Location 1 still fails its 11 of 12 bar. Not built: the
retry / last-good / north-up fallback chain per tap (taps resolve from the stored calibration, which has no per-tap
registration to retry) and the 5 s latency budget. Full 20-location gate not run.

## Warning Screen

Enabling the overlay opens `CompassDisclosureDialog` (see "Prominent Disclosure" above) with two
caveats before activating:
1. Some apps detect mock location activity and may penalise the account.
2. Accuracy depends on the scale setting matching the game's zoom level.

The caveats and the accessibility disclosure share one screen, so the user confirms once instead of
passing a dialog and then a disclosure screen. The confirm button turns the overlay on. It reads
"Accept" when the accessibility disclosure is part of the screen, and then also records the accept
and opens `ACTION_ACCESSIBILITY_SETTINGS`. Below API 30, where only the caveats show, it reads
"Enable anyway". "Decline" leaves the toggle off and records nothing — the user declined the
feature, not the accessibility service. Once the accessibility choice is `accepted`, turning the switch
on skips this screen and enables the overlay directly (`showsTapToWalkEnableDisclosure`).

## Anti-Patterns to Avoid

- Do not add `FLAG_NOT_TOUCH_MODAL` to the overlay — this breaks interception.
- Do not re-read position inside the tap callback — capture it once via `getPosition()` at tap time.
- Do not persist `isTapToWalkActive` in DataStore — it is transient session state in `MutableStateFlow`.
- Do not capture a screenshot or heading in the tap callback — the map is read from the cached stored calibration.
- Do not use the overlay window size as the screen size — use full-display pixels (location on screen plus display metrics).
- Do not use the haversine radius for the ground-map offset — the ground frame is 111320 m/deg.
- Do not call `getPixel` on a hardware bitmap — copy to `ARGB_8888` first via `bitmap.copy(Bitmap.Config.ARGB_8888, false)`.

## Shared disclosure

The accessibility disclosure also covers the Reconnect accessory feature (docs/features/bluetooth-reconnect-tap.md): its text names the periodic screenshot and the tap, and the service gained gesture capability (`canPerformGestures`). Accepting from the Tap to Walk switch does not set the reconnect flag. Turning on Reconnect accessory asks for its own acceptance only while neither flag is accepted; once the accessibility choice is accepted, it records the reconnect acceptance without a dialog (`showsReconnectDisclosure`).

## Guided path

On the Pikmin Bloom guided path the Tap to Walk section in Settings > Menus is removed from the tree. The stored setting is untouched; switching path in Settings shows it again. Source: `GuidedPath.showsTapToWalk`.
