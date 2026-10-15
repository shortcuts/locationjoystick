# Tap to Walk

Two shortcuts for triggering walk-to without a confirmation sheet, useful when you want to act quickly inside another app.

Key files: `:feature:widget:impl/MapFloatingView.kt`, `:feature:widget:impl/TapToWalkOverlay.kt`, `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:settings:impl/SettingsScreen.kt`

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
| `COMPASS_TEST_TARGET_PACKAGE` | String | `""` | Package the "Compass orientation" Test button switches to automatically. Empty = manual switch |
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
taps (3 s budget; falls back to north-up if not ready). **No manual calibration**: an earlier
version asked the user to drag a circle onto the compass and store its position/radius — that
required precise calibration to avoid picking up unrelated red UI elements (a gym marker, a raid
egg) nearby, and a bad calibration made detection "confused" (issue reported after that version
shipped). Detection now re-locates the icon fresh on every call instead, immune to nearby clutter
by construction — see below.

Key files: `:core:location/CompassHeadingSource.kt`, `:feature:widget:impl/CompassAccessibilityService.kt`

### CompassHeadingSource

`@Singleton` bridge owned by `:core:location`. `CompassAccessibilityService` calls `bind(this)` on connect and `unbind()` on disconnect. `FloatingWidgetService` calls `captureHeading()` which delegates to the live service; `SettingsViewModel.testCompassDetection()` calls the same method for the Settings screen's Test button (see "Verifying On-Device" below). `captureHeading()` retries up to 3 times, 500 ms apart, on any null result.

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

### Prominent Disclosure

Play's Accessibility API policy rejected releases that showed the disclosure only in a settings
sub-screen, or that let the accessibility service run without an in-app accept. `CompassDisclosureDialog`
(`:core:designsystem`) is the single disclosure surface: a full-screen dialog that names the
AccessibilityService API, the data it reads (one screenshot), the purpose (compass heading), and
that the screenshot stays on-device and nothing is collected or shared. Its title and body say
"Accessibility" explicitly. It cannot be dismissed by back press or outside tap — only "Accept" or
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
| Onboarding (on entry) | `OnboardingScreen` opens the dialog by itself when the stored choice is unanswered and API >= 30. Buttons: Accept (records `accepted`, opens Android's Accessibility settings), Decline (`declined`), Skip (`skipped`). Every button stores an answer, so it never auto-shows again. Installs that finished onboarding before this shipped never reach it |
| Onboarding card | Optional "Compass orientation" card below the three required steps (every API level, skip line first, not counted in "Step X of 3", never blocks "Start"). "Review and turn on" reopens the same disclosure (`OnboardingScreen.kt`) |
| Turning Tap to Walk on | The Tap to Walk switch opens the same screen, with the two overlay caveats above the disclosure text and "Accept" as the accept label (`SettingsMenusSubScreen.kt`). Below API 30 only the caveats are shown, with "Enable anyway" |
| Tapping the widget crosshair | `FloatingWidgetService.onTapToWalkClicked()` launches `MainActivity` with `EXTRA_SHOW_COMPASS_DISCLOSURE`; the overlay opens on the next tap |
| Settings compass row | "Open Settings" in `CompassOrientationSection` |

Play Console side is done and needs no re-checking: declaration form, per-submission video,
store-listing mention, and no stale artifacts on other tracks. Rejections after build 2400 came from
reviewers not reaching the in-app disclosure, hence the onboarding auto-show. It is on the first-run
screen every new install walks through, and the service stays gated on an in-app accept regardless.
The Map never auto-shows it, so existing installs see no change.

The answer is persisted in `COMPASS_DISCLOSURE_CHOICE` (`accepted`, `declined`, `skipped`, or empty
when unanswered). Only the onboarding auto-show is gated on "unanswered"; the Tap to Walk switch, the
crosshair and the Settings row keep asking until the choice is `accepted`, so a Decline or Skip
never locks the feature out.
The Settings compass row's "Open Settings" button always prompts, because it is the one path that
opens Android's accessibility consent screen, and Play requires the disclosure before every trip
there.

`CompassHeadingSource.captureHeading()` returns `null` until the choice is `accepted`. The consent
check lives there, not at the call sites, because Android's accessibility settings can enable the
service without the app ever being opened (see `CompassHeadingSourceTest`).

### Anti-cheat caveat

Accessibility services running in the background are detectable by some games. The Settings UI discloses this. Turn off the accessibility service in system Accessibility Settings if the game penalises it — there is no separate in-app toggle.

### Requires

`android.permission.BIND_ACCESSIBILITY_SERVICE` — granted by Android when the user enables the service in system Accessibility Settings. No runtime prompt needed.

API 30 (`takeScreenshot`) — no fallback exists below it. On API 28–29, the "Compass orientation" Settings section is hidden, and the service itself is disabled in the manifest (`android:enabled="@bool/compass_accessibility_service_enabled"`, `false` in `values/bools.xml`, `true` in `values-v30/`), so it never appears in Android's Accessibility settings where it could be turned on without the in-app disclosure. `CompassAccessibilityService.onServiceConnected()` also skips binding as a second guard. The rest of Tap to Walk (Tier 1 quick-walk, Tier 2 overlay) works unchanged.

The disclosure, the onboarding step and the service's system-settings description are localized in every supported locale with identical keys (docs/features/localization.md). The Chinese strings keep the English word "Accessibility" in parentheses next to 无障碍 so a reviewer testing in any locale sees the term Play asks for.

## Bearing registration investigation

Goal: recover the map's rotation and scale from a screenshot of the game map, without a compass icon and without
reading the other app's memory. Method: register the game screenshot against this app's own map rendered around
the same position. Harness: `scripts/registration_bench.py` (run with `scripts/registration-bench/.venv`).

Dataset and ground truth. One device only (Pixel 5, 1080x2340, 440 dpi; a second display config was not built,
because changing density restarts the game on a live account). One location (Louvre courtyard, Paris). The game
view is a fixed pitched 3D camera at a closer zoom than our top-down map, so hand-annotated landmark pairs were not
reliable. Ground truth instead comes from motion: the mock walked about 25 m north and then east (game frames every
3 s, positions read from the system), and a pitched-camera model (heading, lateral and forward pixels per metre,
perspective term) was fitted so that the ground flow between frames matches the walked distance. This truth does not
use the registration images, but it is noisy: fitting the north walk and the east walk alone gave headings of -53 and
-38 degrees (joint fit -46.7, 8 px/m across at the player, 3.2 px/m forward), so treat truth as good to about 8
degrees and 15% scale (superseded by "Re-measurement against the commanded heading" below). The Pyramid pools (blue triangles left of the player in the game) agree with -47 and not with
the heading the best-scoring matches picked.

Landing error is the largest gap in metres between where a tap would land under the estimate and under the truth,
over a 3x3 grid of game pixels on the ground band. Rows were scored on 10 walk frames, with our map also rotated
0/90/180/270 degrees about the marker (rotation is the unknown the method must recover). Host numbers:

| kind | method | n | reject % | landing err p50 / p90 (m) | median score | p50 ms |
|---|---|---|---|---|---|---|
| real | logpolar (raw) | 10 | 0 | 760 / 773 | -0.07 | 415 |
| real | orb_similarity (raw) | 10 | 0 | 398 / inf | 0.23 | 72 |
| real | orb_homography (raw) | 10 | 0 | 650 / inf | 0.23 | 83 |
| real | unpitch + heading search, luminance | 10 | 0 | 221 / 610 | 0.06 | 3095 |
| real | unpitch + heading search, park-green | 10 | 0 | 611 / 612 | -0.12 | 3072 |

("raw" = the method run on the pitched screenshot, as in the earlier synthetic table below. "unpitch" removes the
camera pitch with the per-device calibrated camera, then searches the heading about the player marker only; the
function `register_unpitched` recovers a synthetic heading within 1 m at 100 m reach.) Rotated rows are identical
to the 0 degree rows within the disc effect; the full table is `registration_bench.py real` then `report`.

Method choice: none viable. Rule: lowest p90 landing error with reject rate at most 20% and p90 at most 25 m.
Nothing is within an order of magnitude; the best p50 is 221 m. Scores do not separate good from bad: the
best-scoring heading across five frames of both walks sat near +120 degrees, about 170 degrees from the truth, with
scores of 0.3. Colour-class features (blue, green, mixed) and template matching with free translation did not give a
consistent heading either. So the landing-error threshold, verification-score threshold and latency budget are n/a:
no method produces a score that tells a correct result from a wrong one. Host latency of the heading search is about
3 s unoptimised; on-device latency is not measured (no OpenCV in the Android build).

Learned model: not built; about 20 correlated frames from one location are not a training set.

Lead: a motion-calibrated heading, prototyped in "Motion-calibrated heading prototype" below.

Not measured, dropped by user direction: on-screen north indicator, device rotation sensors, gesture-derived heading
and the manual overlay.

Earlier synthetic-only table (same-image warps, not game-to-map):

| kind | method | n | rejected | median score | median angle err (deg) | median scale err (%) | p50 ms | max ms |
|---|---|---|---|---|---|---|---|---|
| synthetic | logpolar | 6 | 0 | 0.081 | 25.00 | 16.70 | 510 | 623 |
| synthetic | orb_similarity | 6 | 0 | 0.844 | 0.00 | 0.02 | 81 | 151 |
| synthetic | orb_homography | 6 | 0 | 0.875 | n/a | n/a | 70 | 132 |

Safety note for repeat runs: teleport jumps of 25 to 120 m in a few seconds made the game show a speed warning.
Use walking-speed moves (the `walk` Control API route) when recording frames against a live game account.

## Motion-calibrated heading prototype

Chosen bearing source (user decision): the app moves the position a known distance and direction at walking
speed, and two game screenshots around each move give on-screen north and scale with no map matching. Prototype
in `scripts/registration_bench.py` (`content_flow`, `heading_from_moves`, `motion_g_est`, subcommand `motion`):
phase correlation of the ground band gives how far the scene shifted for each move; two roughly perpendicular
moves give the whole ground-to-screen map, so heading and both pixel-per-metre scales fall out with no camera
calibration. It returns nothing (caller keeps north-up) when a move is under 3 m, a flow under 5 px, the moves are
within 30 degrees of parallel, or the two row headings disagree by more than 10 degrees.

Scored on the stored north-walk and east-walk frames, every north move paired with every east move, against the
gate (reject at most 20% and p90 landing error at most 25 m):

| kind | method | n | reject % | landing err p50 / p90 (m) |
|---|---|---|---|---|
| motion_k4 (about 8 m moves) | two_move | 100 | 50 | 70.7 / 86.4 |
| motion_k2 | two_move | 144 | 57 | 75.1 / 89.6 |

Verdict: not viable at the gate. Accepted estimates are tight (k=4 heading median -39.7 degrees, sd 1.7, against
the joint-fit truth of -46.7) but sit about 7 degrees off, and even with the true scales the heading-only landing
error is p50/p90 37.5 / 50.0 m on the far probes, because a degree of heading is about 6 m at the top of the
frame. Half the pairs are rejected, mostly stationary lag frames at the start of a walk. Caveat: truth comes from
these same walks (a joint fit good to about 8 degrees), so this table alone is not independent proof; see
"Re-measurement against the commanded heading" for the follow-up. The compass-icon detector stays; nothing in the
app changed.

### Re-measurement against the commanded heading

The truth above came from polled positions, which carry position jitter. Here each move's direction is the exact
bearing sent to the Control API `/walk` (known by construction), and only its length comes from the polled
positions (projected onto that bearing, `along_track_move`). Recorded with `registration_bench.py walkcap`
(three 40 m straight legs at walking speed, no teleport: north, east and a held-out north-east, one frame every
3 s, about 25 frames per leg) and scored with `motion --commanded`. Key and host come from the environment and are
never written to disk.

The game camera was at a different orientation than the first dataset (estimated heading -0.9 degrees against
-46.7), so the 7 degree offset cannot be compared frame for frame. `/state` reported `speedMs` 0 and `bearing`
null during the walk-to, so the commanded-bearing check and the moving filter use positions instead; every frame
counted as moving, so the filter changes nothing here.

| kind (about 6.5 m / 3.3 m moves) | n pairs | reject % | heading mean / sd (deg) | landing err p50 / p90 (m) |
|---|---|---|---|---|
| north x east, k=4 | 441 | 68 | -0.9 / 3.0 | 99.6 / 185.0 |
| north x east, k=4, flow response at least 0.1 | 51 | 25 | -0.9 / 2.7 | 56.4 / 130.8 |
| north x east, k=2 | 529 | 97 | -0.8 / 2.7 | 35.4 / 110.3 |
| north x north-east, k=4 | 882 | 95 | 2.4 / 11.7 | 148.0 / 561.9 |
| held-out north-east offset, k=4, response at least 0.1 (estimate minus prediction) | 4 | n/a | -6.4 / 3.8 | n/a |
| held-out north-east offset, k=2, response at least 0.1 | 9 | n/a | -10.8 / 2.5 | n/a |

Landing error is against the circular mean of the accepted north x east headings (and their median scales), so it
measures scatter, not absolute error. The held-out offset fits the ground-to-screen map on the north and east moves
only, then compares the predicted and observed flow direction of the north-east leg; without the response filter
it is noise (sd 35 to 47 degrees).

Verdict: still fails the gate (reject at most 20%, p90 at most 25 m). The accepted north x east headings are tight
(sd about 3 degrees), so the 8 degree truth noise was largely position jitter, but the held-out leg still shows a
6 to 11 degree offset (small sample), so the 7 degree offset was not only truth noise. The dominant problem is
the flow itself: north-going moves often show about 0 px of shift for 6 m of commanded movement (the game redraws
position in steps), while east moves are consistent (about 13 px/m). Only one camera orientation was recorded; the
any-orientation property is unproven (needs the user to rotate the game camera). Annotated end frames with the
estimated screen-north arrow are written to `annotated/` for checking by eye. Nothing in the app changed.

### Multi-leg calibration and on-device pass rate

Prototype only, bench script `scripts/registration_bench.py` (`multileg`, `taptrials`); nothing in the app changed.
The two-move heading was replaced by one least-squares ground-to-screen map fitted over every valid move of eight
40 m walk legs (bearings 0, 180, 90, 270, 45, 225, 135, 315, walking speed, no teleport, Pixel 5). Moves are frame
pairs 4 and 6 frames apart, both moving, flow response at least 0.1, direction taken from the commanded bearing;
outliers beyond max(3 MAD, 1.5 px) are dropped and the map refitted once. No north is assumed: the heading comes
out of the map in whatever orientation the camera has. The acceptance bar replaces the bench p90 gate: at least
48 of 50 real taps land within 15 m. A tap pixel is turned into a ground offset with the inverse map, the mock
position walks there, and the next screenshot is searched for the patch that was under the tapped pixel (masked
normalised cross-correlation, player excluded), so the error is what the game drew, not what the calibration says.
Annotated before and after frames are written to `annotated_trials/` for checking by eye.

| measure | value |
|---|---|
| legs / moves with response at least 0.1 | 8 / 182 |
| moves used after outlier drop | 106 |
| fitted heading, scales, row skew | -1.1 deg, 6.7 and 4.6 px/m, 2.5 deg |
| residual rms | 2.7 px |
| held-out, leave one bearing out (n=165) | offset mean -0.1 deg, sd 7.7 deg, p90 of absolute 3.7 deg |
| taps run | 4 of 50 (stopped once the bar became unreachable) |
| taps within 15 m | 0 of 4 |
| landing error | 17.4, 23.3, 28.6, 32.6 m (all matches NCC 0.43 to 0.82) |

Verdict: not viable at the bar (48 of 50 within 15 m); after 4 fails at most 46 can pass. Averaging fixed the
heading, not the distance: the held-out direction error is small, but every tap's content ended below the player
(the player overshot), so the single linear scale is wrong away from the band it was measured in, because of the
camera's perspective. A 2x2 map cannot fix that; a perspective term (near and far scale differ) or per-band scales
would be the next experiment. Cardinal legs gave mostly zero flow (the game redraws position in steps, as above),
so the fit leaned on the diagonal legs. Camera orientation was recorded once; the any-orientation property still
needs the user to rotate the game camera and rerun, with calibration and taps in one uninterrupted session.
Walking speed is about 0.56 m/s here, so a full 50-tap run takes hours.

### Game-camera orientation signals

Probe (`registration_bench.py probe`, stationary, screenshots and touch only, nothing read from the game): is
there an on-screen cue (compass needle, reset affordance) that reveals or fixes the camera orientation?

| Observation (Pixel 5, latest game build) | Result |
|---|---|
| Top-right search window with the pointer-location developer overlay off, camera as found | no needle; only a weather icon and red non-needle items |
| Same window after a two-finger twist of about 40 degrees (map visibly rotated) | still no needle, no compass or reset button appeared |
| Pointer-location overlay on | its red pressure bar is read by the shipped detector as a needle at 180.0 degrees (false positive) |
| Red icons elsewhere in the window (raid "R" icon, top-right corner item) | read as needles at 78 to 94 degrees (false positives) |

Verdict: no usable cue. The game shows no heading element in the window the shipped detector scans, rotated or
not, so there is nothing to reset against and the detector can only return false positives on this build.
Two-finger camera rotation is drivable from adb by a virtual touchscreen (`uinput`); raw `sendevent` is denied
to the adb shell on this device. The needle-gated criteria (found in 8 of 10 frames, twist delta, reset to 0)
could not be met because there is no needle to read.

Incident: the first probe run tapped red blobs as "reset" taps, including a red icon below the companion ball
and, from a stale position, after a frame with no blob; that opened other screens and maps. Frames 02 onward of
that run are discarded (flagged in the run's `FLAGGED_WRONG_MAP.txt`); the verdict above uses only the baseline
frames and the single manual twist captured before any tap. The script now clears the tap target every frame,
but never taps unless a blob is found in that same frame; no needle exists, so no tapping should be rerun.
Not covered: one device, one twist; a compass that appears only after a longer rotation was not seen.

### Automated varied-camera capture

Bench only (`registration_bench.py varycap`), nothing in the app changed. One command records walk legs under
several camera zoom and orientation conditions with no hand steps: per condition it drives a two-finger pinch and
a two-finger twist through a `uinput` virtual touchscreen (external to the game, no taps, nothing read from or
injected into it), then walks one straight leg per bearing through the Control API `/walk` at walking speed. No
teleport is ever called. Output is `<out>/<zoom>_<twist>/walk_C_<bearing>/`, the layout `multileg` and
`motion --commanded` already read, plus `before.png` / `after.png` around each condition's gestures and
`condition.json`. `--truth` copies the camera anchor file into each condition directory.

```
registration_bench.py varycap OUT --conditions 1:0 1.6:0 1:40 0.5:-60 --legs 0 90 45 225 --dist 30 --truth truth.json
```

| Observation (Pixel 5, latest game build) | Result |
|---|---|
| Two fingers spreading horizontally (zoom ratio above 1) | zooms in and lowers the camera pitch to a close-up of the avatar |
| Two fingers contracting (ratio below 1) | zooms out to a wide, high-pitch view |
| Two-finger twist of 40 to 60 degrees | rotates the map and the camera heading |
| Camera reset control | none; gestures accumulate, so conditions are relative to the previous one |
| 8 short walk legs (10 to 15 m) across five conditions | frames and positions recorded, walk target cleared each leg, no speed abort |

The zoom change also changes the pitch, so a close-up is not a scaled copy of the wide view. The earlier unpitched
ground map is fitted per condition (`multileg` on one condition directory); whether one calibration spans zooms is
a question the larger sample set answers, not something shown here. A log-polar estimate of the applied rotation
and scale between the before and after frames returned about 0 degrees and scale 1.0 for a visible zoom, so it
is not used: check `before.png` / `after.png` by eye. Walking is about 0.56 m/s, so 4 conditions of 4 legs of 30 m
take roughly 15 minutes of walking plus screenshots. Not covered: one device; the larger run itself was not
recorded or scored in this entry.
### Perspective ground map and offline landing check

Bench only (`registration_bench.py persp`, `landing`; `taptrials --persp`), nothing in the app changed. Two fixes
to the earlier multi-leg map. First, the flow is measured per band (seven row bands, three column thirds) after
the leg's static content (HUD, ring, companion) and the player disc are inpainted away; band rows and columns are fractions of the frame, so no screen size is special-cased, because phase
correlation over the whole band locked onto that static content and shrank every shift. Second, the 2x2 map is
replaced by a pitched-camera homography (heading, lateral and forward scale, one perspective term) fitted to
those band flows, so px per metre falls with distance (about 7.5 down to 2.6 forward at the Pixel 5 wide view).
A tapped pixel is turned into a ground offset with the inverse homography relative to the player's feet pixel
(detected from the walked legs, see below). Heading still comes out of the fit with no north assumed.

Feet pixel detection (`detect_feet`): the world scrolls under a player that stays put, so in each leg's temporal
median only the player and HUD stay sharp, and the minimum over legs of different bearings also drops roads
parallel to one walk. Along the centre column the figure is the strongest vertical run of sharp rows (gaps of up
to 5% of the height bridged for dark legs); the feet are its last row, lifted by 7% of the figure height (the one
value tuned, on the two hand-measured points). No orientation, game read or per-device number is used. On the
recorded legs it gives 537 1558 on the Pixel 7 (by eye 538 1556) and 542 1725 on the Pixel 5 (by eye 532 1720),
and `landing` without `--anchor` scores p90 9.9 m on the Pixel 7 legs (9.7 m with the hand anchor and the old bands); the wide-zoom twist datasets above are not on disk, so their rows were not re-run.
`--anchor` stays as an override. After the fit, bands at or above the fitted horizon row (`sy / a`) are dropped
and the map refitted (`fit_perspective_horizon`); with the horizon off screen this is a no-op.

Data: 4 legs of 30 m per camera state (bearings 0, 90, 45, 225), walking speed, Pixel 5, three wide-view camera
orientations reached by two-finger twists (fitted headings about -20, +5 and -21 degrees, same lateral, forward
and perspective terms within 10 percent). Check: leave one leg out, fit on the other three, then for every held-out
frame pair with a known move of 5 to 31 m and a grid of 21 pixels, compare where the model says that pixel's
content went with where a masked template match (NCC at least 0.7) finds it. Error is the ground distance in
metres. This is what a tap's landing error is, but it is not an on-device tap run.

| camera state | samples | p50 / p90 (m) | within 10 m | within 15 m |
|---|---|---|---|---|
| wide, heading about -20 | 3554 | 2.3 / 7.4 | 94% | 96% |
| wide, twisted, heading about +5 | 3263 | 2.7 / 7.1 | 95% | 98% |
| wide, twisted again, heading about -21 | 3177 | 2.3 / 6.7 | 95% | 99% |

The old 2x2 map scores p90 7.6 to 8.1 m and 11 to 12 m for moves of 20 m and more on the same data (flat fit,
exploratory run), so the perspective term matters mostly at distance; the larger gain over the first trials is the
static-content cleanup. Samples overlap (many pairs per leg), so they are not independent.

Not shown, honestly: (1) no on-device taps were run for this entry, because the app disappeared from the test
phone mid-session (not installed), so the 10 to 15 m result is the offline check above and still needs a real
tap run; the earlier on-device run (0 of 4) used the old map. (2) Close-up zoom (pinch out about 1.6x, low
pitch, horizon visible) is still not calibrated or re-captured: the band grid is now relative and horizon-aware
and the feet pixel is detected, but the close-up `landing` check (p90 under 10 m on two screens) has not been run.
The first close-up capture (side bands, hand anchor) gave a median response of 0.04. (3) The offline check only covers moves up to about 31 m and pixels
of 700 to 1420 rows; taps beyond that are extrapolated. (4) Taps whose patch ends under the player's shadow or
companion can be mismatched; check the annotated frames by eye. (5) The feet pixel detection, the flow estimator and the fit are ported to the app (`FeetDetector`, `FrameFlow`, `GroundMapFit`), but nothing in the app calls them yet.

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
last run row minus 0.07 of the run height (sole edge to ground contact; the one tuned value).

Failure modes: a shadow below the sole lifts the last row; a companion sprite beside the player joins the run and
shifts x; close-up zoom puts the anchor too high (see open work 2, not gated in code); a stationary HUD element in the
centre column reads as figure; all legs parallel to one road keep road edges. Fallback: `null` means calibration
failed, store nothing. There is no default anchor (feet differ per device and zoom: Pixel 5 542,1725 of 1080x2340;
Pixel 7 538,1556 of 1080x2400).

Invariants a change must not break:

1. Constants and order equal the bench (`scripts/registration_bench.py` `detect_feet`); tests pin a bench-exported
   scene within 1.0 px (`scripts/export_feet_golden.py`; rerun when `detect_feet` changes).
2. Input and output are device pixels; only `toReference` scales. No screen size, density or north is hardcoded.
3. Parity, not improvement: fixing the close-up row is a separate bench-first change.

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
of the bench fit on synthetic flows with outliers. The landing numbers themselves need the cross-device fixture suite.

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

### Calibration walk

`CalibrationWalkOrchestrator` (`:core:location`, `@Singleton`) walks the mock position, screenshots the game,
runs the solver and stores one map. Nothing user-visible starts it yet and the tap handler does not read the
result yet; both consume `CalibrationWalkOrchestrator.state` and `SettingsRepository.getGroundCalibration()`.

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
signal to read its zoom or rotation at tap time (see "Game-camera orientation signals"), so a keyed record
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

Accepted state of the no-compass investigation (branch `tap-to-walk-no-compass`, bench only, no app code changed):

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
3. Ground map model ported (`GroundMap`, see "Ground map model"), feet detection ported (`FeetDetector`, see "Feet detection"), frame flow and fit ported (`FrameFlow`, `GroundMapFit`, see "Frame flow and ground fit"); the walked-leg capture and orchestration ported (`CalibrationWalkOrchestrator`, see "Calibration walk"); the calibration UX and the wiring into the tap overlay remain. The on-device analysis time is unmeasured (the pure port was only benchmarked on the host).
4. Prove the pass rate across devices, screen sizes, zooms and rotations before the epic closes.

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
feature, not the accessibility service. State is local (`rememberSaveable`) so the
screen re-shows if the user disables and re-enables.

## Anti-Patterns to Avoid

- Do not add `FLAG_NOT_TOUCH_MODAL` to the overlay — this breaks interception.
- Do not re-read position inside the tap callback — capture it once via `getPosition()` at tap time.
- Do not persist `isTapToWalkActive` in DataStore — it is transient session state in `MutableStateFlow`.
- Do not block the tap callback waiting for heading — pre-capture in `show()` with a timeout instead.
- Do not call `getPixel` on a hardware bitmap — copy to `ARGB_8888` first via `bitmap.copy(Bitmap.Config.ARGB_8888, false)`.

## Shared disclosure

The accessibility disclosure also covers the Reconnect accessory feature (docs/features/bluetooth-reconnect-tap.md): its text names the periodic screenshot and the tap, and the service gained gesture capability (`canPerformGestures`). Accepting from the Tap to Walk switch does not enable the reconnect tap; that needs its own acceptance.
