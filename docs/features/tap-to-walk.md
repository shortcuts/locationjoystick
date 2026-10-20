# Tap to Walk

Two shortcuts for triggering walk-to without a confirmation sheet, useful when you want to act quickly inside another app. Tier 1 is a quick-walk option on the floating map. Tier 2 is a screen-wide tap interceptor. Tier 2 is the one that needs the accessibility service.

Key files: the widget module's map view, tap overlay and widget service, and `:core:location/ScreenCaptureSource.kt`. Settings keys and their defaults live in the settings code, not here.

Bench record and measured results: [tap-to-walk-investigation.md](tap-to-walk-investigation.md).

State-of-the-art research for the automatic fit and the superposition retry: [tap-to-walk-research.md](tap-to-walk-research.md).

## Why the settings sit where they do

Tier 1's toggle is in the Privacy group, next to Hide Teleport Features, because Floating Map Quick Walk is a standalone feature. It is not one of the features that setting hides ([hide-teleport.md](hide-teleport.md)). Tier 2 lives under the Tap to Walk menu. Screen access has no toggle of its own: it is on whenever the accessibility service is granted.

## Tier 1 — Floating Map Quick Walk

When the option is on, a tap on the floating map walks directly instead of opening the action panel. The option is read as composable state, and the tap callbacks use the latest values rather than values captured once at view creation.

## Tier 2 — Screen Tap-Intercept Overlay

The overlay is a full-screen window that takes every touch. It must not set the flag that lets touches pass through to the window below; that flag breaks interception.

Position is read once, at tap time. The tap pixel is in full-display device pixels, computed from the overlay's on-screen location plus the in-window tap. Never use the overlay window size: system bars inset it, and the calibration is fitted on full-display screenshots.

### Pixel to GPS

Primary path: a stored ground map (see "Automatic fit") that matches the current display size converts the tap pixel to a ground offset in metres east and north. The offset is applied to the current position using the flat-earth scale that the walk uses, not the haversine radius. Taps past the horizon of the fitted map yield no target.

No map and no way to learn one (below API 30, spoofing not running idle, game not in front): the tap walks nowhere and a toast says why. A stored map that matches the display but is not valid still resolves when no new map can be learned, because it is the best bearing available. An earlier version fell back to a north-up formula with a hand-set scale. It was removed: the game camera has no north, so the fallback walked a wrong bearing, and the scale asked the player for something they cannot know.

When no valid map exists and learning is possible, the tap does not move the player. The overlay holds the tap pixel and the position it was made at, the coordinator walks its learning route, and the held tap is resolved against the new map and walked. The tap is never resolved against a map the monitor flagged as suspect, when a new one can be learned. `learnMap` returns a `LearnOutcome`, and only a fresh fit walks the held tap. The fit takes minutes (state `Analysing`, cancellable); if the player teleports or moves meanwhile, the tap is dropped with a "tap again" toast, because walking it would send the player back to the tap-time spot. Cancel is silent: the user asked for it.

Invariants:

- Never read the map orientation or take a screenshot inside the tap callback. The tap reads the cached stored fit only. That fit is written by the coordinator's analysis of earlier walks, never by a user action and never on the tap path. (The old Settings walk made the same rule easy to keep because the user started it; with the automatic fit it is the reason frames are taken on the 3 s tick and analysed at leg close.)
- The ground frame is flat-earth at 111320 m per degree, the frame the fit is measured in. The haversine radius drifts about 0.1 percent from those measurements.
- An unfit tap never walks a guessed bearing. A learning walk starts only from a tap, and the overlay is dismissed first, so it never blocks the player's touches during the walk.

## Screen access

The accessibility service exists for two things: taking screenshots for the automatic fit, and reading and tapping the connect control for Reconnect accessory ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)). It captures nothing until the user accepts the disclosure. Where the map points is never read from the screen. It is measured by fitting how the map moves between frames of walks.

Capture is gated in the bridge that consumers call, not at each call site: disclosure accepted, screen on and the game in front. Android can enable the service from system settings without the app ever opening, so a call-site check would be bypassable. The disclosure names the game in front as the only time frames are taken, so the gate is part of the consent, not only of battery care.

### Why the service is named "screen helper"

The service was renamed from an earlier name. Android keys enabled services by component name, so users who updated must turn it on again in Android's settings. The stored disclosure choice survives the rename through a key migration in the datastore module.

### Platform limits

Screenshots require API 30. Below it no map can exist, so the overlay walks nowhere. The status block is one shared Screen access block above both feature sections, shown while either switch is on, so a Reconnect-only user still sees whether the service runs; each section adds a service-off hint when its switch is on without the service. On older devices the block is hidden and the service is disabled in the manifest, so it never shows up in Android's accessibility list where it could be enabled without the in-app disclosure. The service also skips binding on connect as a second guard. Tier 1 and Tier 2 quick-walk still work below API 30.

## Prominent Disclosure

Play's accessibility policy rejected releases that showed the disclosure only in a settings sub-screen, and rejected a release that let the accessibility service run without an in-app accept. The disclosure is therefore a single full-screen surface, shown in the normal usage flow, immediately before Android's own consent screen.

It names the accessibility API, the data it reads (periodic screenshots while walking with Tap to Walk on and the game in front, and during Reconnect accessory), and the purpose. Play's policy requires the periodic screenshots to be named in this text, in the onboarding card and in the service description. The copy lives in string resources, not here; read it there.

Rules that the code must keep:

- The dialog cannot be dismissed by back press or an outside tap. The user must answer.
- The body scrolls and the buttons stay pinned, so the choices are always visible.
- Only Accept opens Android's accessibility settings. Skip consents to nothing and opens nothing. Skip exists only on the onboarding prompt.
- Decline never locks the feature out. Only the onboarding auto-show is gated on "unanswered". Every other entry point keeps asking until the choice is accepted.
- The answer is stored per device, and is not part of an export. A restored backup must not carry consent to a new phone.
- Once accepted, the other entry points skip the dialog and act directly.

Entry points, each immediately before Android's consent screen, so reviewers always reach the disclosure in normal use:

- Onboarding: the first screen of a fresh install, on every API level, while unanswered. This was added after build 2400 was rejected for a disclosure reachable only through Settings menus.
- Optional onboarding card on the path picker.
- The Tap to Walk switch in Settings, with the overlay caveat on the same screen.
- Tapping the widget crosshair, which opens the app to the disclosure.
- The screen-access row's open-settings button.
- The automatic fit adds no new consent path. It needs the service granted and the disclosure accepted, both checked in the capture bridge.

The Play Console declaration and store listing are done. Do not re-check them as part of routine work.

## Warning screen

Turning on the overlay shows a caveat that the overlay intercepts taps. The caveat and the accessibility disclosure share one screen, so the user confirms once. Decline leaves the toggle off and records nothing: the user declined the feature, not the service.

## Anti-cheat

Accessibility services running in the background can be detected by some games. The settings UI says so. The user can turn the service off in system settings; there is no separate in-app toggle, because that would only hide the same signal.

## Automatic fit

The app fits and maintains the ground map itself, so a tap maps to a metre offset, for any camera twist, zoom or screen size. There is no calibration step for the user. The earlier Settings walk (8 legs, 10 minutes, one stored record) was removed: it was a chore, it went stale the moment the player twisted or pinched the map, and nothing readable at tap time says the camera changed.

How it works (class docs and tests are the detail): while Tap to Walk is on and the game is in front, every straight stretch of a walk the app already makes (tap to walk, quick walk, joystick) becomes a leg of screenshots. Positions are the app's own mock fixes, so bearing and length are exact. When a leg closes, its screen motion is compared with what the stored fit predicts. A cumulative-sum test on the residual (CUSUM) decides whether it still matches. Any alarm makes the fit suspect. A leg with no usable flow (close-up zoom, an open menu) counts as a mismatch too, because passing it in silence keeps a stale fit. The comparison scores only the bands the fit used, so bands above the fitted horizon do not inflate the residual. All four terms are then re-fitted from the legs that follow. Re-fitting the heading alone was benched and dropped: see "Automatic fit bench results" in [tap-to-walk-investigation.md](tap-to-walk-investigation.md). Route replay and roaming are not legs: their path is not straight and the jitter offset applies. A full re-fit needs legs whose bearings spread by at least `AUTOFIT_MIN_BEARING_SPREAD_DEG`, because opposite legs lie on one line and pin one axis. A failed full re-fit drops its legs, so the same legs never cost minutes of CPU twice.

Why legs, not a calibration route: every walk is a free measurement, and a camera change is noticed within the walk that follows it. The accepted cost is that a tap made between a twist and the next closed leg uses a stale fit.

Why scale is searched and never asked: a fixed or hand-set scale cannot follow a pinch. The fit solves scale together with heading.

Why a tap with no valid map walks first: with no fit there is nothing to resolve the tap against, and a wrong guess is a wrong teleport target. The learning route walks at walking speed, never a teleport, so it does not touch the teleport cooldown and does not look like a jump to the game. Progress and a Cancel action are on the foreground notification, not in the tap overlay, because the overlay takes every touch and would lock the player out of the game for minutes. The widget crosshair is the second surface, because the notification may never show (notifications denied on API 33+): it takes the active tint while learning and a click cancels instead of opening the overlay. Opening the overlay mid-learning would walk a tap and replace the learning leg, which fails the learn. The learning route is two perpendicular legs. Two legs replaced the bench's four: held-out landing error p90 6.2 m, under the 10 m bar ([investigation](tap-to-walk-investigation.md), decision 12).

Why one stored record and not one per zoom or rotation: the game exposes no signal the app can read at tap time. A record keyed by zoom or rotation could never be looked up, so there is a single latest record per device, overwritten by every re-fit.

Why per device and not exported: the map depends on the phone's screen and the game's camera. A backup restored on another device would give wrong taps. The record is rejected when its screen size differs from the current display.

Frames spool to the app cache, one leg at a time, because a leg does not fit in the heap. A leg's spool is deleted as soon as the fit is valid; while the fit is not valid, the last few legs are kept for the full re-fit, so a twist or zoom from before is never mixed with legs after it. The cache is user storage, so the spool is bounded and short-lived:

- The capture gate lives in `ScreenCaptureSource`, not in callers. It includes the Tap to Walk switch, so no frame exists while the feature is off.
- The spool is wiped when that gate closes (switch off, disclosure withdrawn, service unbound). A leg already in analysis finishes, and its frames are deleted as soon as it ends.
- The spool is also wiped when observing starts. A process kill skips the cleanup on exit, so the previous session may have left frames.
- A byte cap and a free-space floor apply before each frame. Over either, the oldest buffered leg goes first, because a refit that loses an old leg only waits for a newer one.
- Frames are deleted on every exit path, including the service ending.

At most one analysis runs at a time, never on the tap path.

Invariants a change must not break:

1. Learning walks at the walking speed constant, not the user's walk profile.
2. Geometry uses the flat-earth metres-per-degree constant. Do not swap in the haversine radius.
3. The feet pixel goes into the flow code in device pixels. Only the fit converts to the 1080x2340 reference frame. That reference is fixed; stored fits are in reference pixels.
4. Opposite legs in the synthetic test must differ in scroll size. Identical frame sets give identical temporal medians, which defeat the rejection of repeated world texture. Real walks never repeat terrain exactly.
5. A stored map is valid only for the screenshot size it was fitted at.
6. No frame is taken unless the disclosure is accepted, the screen is on and the game is in front. The check lives in the capture bridge.
7. An alarm makes the fit suspect until a full re-fit succeeds. Never silently keep a map that does not explain the screen.

### Ground map

The ground map is a pure-Kotlin perspective model in the model module. It maps device pixels to ground offsets and back, and it has no Android dependency so the math can be tested on the host. A device pixel is scaled into the reference frame, so one fit serves any screen size. Its valid range was checked only on part of the screen and up to about 31 m; outside that it is extrapolated, not rejected.

### Feet detection and frame flow

Feet detection finds the player's feet pixel by removing the moving world (a per-pixel temporal median over a leg), then looking for the sharp edges of the figure in a central column. Taking the minimum over legs drops roads that run parallel to one walk. The row prior exists because the detector can be pulled up by a range ring or a shadow. The prior is a fallback, and each use of it counts as a failed registration.

Frame flow removes static content (HUD, range rings, companion sprite) from the frames, then measures how each horizontal band of the screen shifted between frames a known distance apart. The perspective fit uses those shifts with robust weighting and rejects bands near the horizon.

Constants here are tuned values with a reason attached. Do not "fix" one back to a value from an older bench version. Known deviations from the bench, each covered by a test:

- Hole filling is a smoothed fill, not the library inpainting. Without the smoothing, boundary streaks copy inward and pull flows to zero.
- Bands that overlap the player disc are down-weighted, because the bench gave them full weight and they biased the forward scale.
- The reported camera is always the positive-scale solution. Both signs describe the same homography.
- The phase correlation shifts by a true cyclic shift with an integer centre. OpenCV's `fftShift` differs for odd padded sizes (1080x1920 bands pad to 135 rows), by up to 0.5 px. The Kotlin side is the correct one.

Parity: each recorded device has a fixture of bench numbers, and a test checks the port's fit and tap offsets against them. Fixtures hold numbers only, never frames. Frames are not committed because the game HUD shows the player's name.

### Status and open work

Accepted state of the no-compass investigation (branch `tap-to-walk-no-compass`; the bench record is in [tap-to-walk-investigation.md](tap-to-walk-investigation.md)):

- Heading and scale come from the walked-leg fit. No north is assumed.
- Wide zoom on one device passes the bar. Close-up zoom does not yet.
- Twist, still-frame twist and one-parameter zoom were benched for the automatic fit: results and thresholds are in the investigation doc, "Automatic fit bench results".
- Not proven: other devices and screen sizes, close-up zoom, and the end-to-end pass rate of the port. The smoke gate for the pass rate has not been run in full.

Open work, in order:

1. Lower the skip rate of the matcher. It skips most taps as ambiguous, and the pass rate only covers the taps it could match.
2. Close-up zoom. The fit and the match step fail there, not the feet anchor alone. Needs a second device and a cause for the ambiguity before another attempt.
3. Prove the pass rate across devices, screen sizes, zooms and rotations before the epic closes. The gate is the tap smoke command in [testing.md](../testing.md).

## Anti-patterns to avoid

- Do not add the flag that lets touches pass through to the overlay window. It breaks interception.
- Do not read position again inside the tap callback. Capture it once at tap time.
- Do not persist the active state of Tap to Walk. It is session state.
- Do not capture a screenshot or read a heading inside the tap callback.
- Do not mix legs from before and after a camera change in one re-fit.
- Do not use the overlay window size as the screen size.
- Do not use the haversine radius for the ground-map offset.
- Do not read pixels from a hardware bitmap. Copy it to a software config first.

## Shared disclosure

The accessibility disclosure also covers Reconnect accessory ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)). Accepting from the Tap to Walk switch does not set the reconnect acceptance. Reconnect asks for its own acceptance only while neither is accepted; once the accessibility choice is accepted, it records the reconnect acceptance without a dialog.

Both features share one screenshot budget ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)): every screenshot is serialized and spaced, because the platform rejects shots taken too close together.

## Guided path

On the Pikmin Bloom path the Tap to Walk section is removed from the settings tree. The stored setting is untouched. See [guided-paths.md](guided-paths.md).
