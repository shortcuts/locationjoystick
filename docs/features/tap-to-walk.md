# Tap to Walk

Two shortcuts for triggering walk-to without a confirmation sheet, useful when you want to act quickly inside another app. Tier 1 is a quick-walk option on the floating map. Tier 2 is a screen-wide tap interceptor. Tier 2 is the one that needs the accessibility service.

Key files: the widget module's map view, tap overlay and widget service, and `:core:location/ScreenCaptureSource.kt`. Settings keys and their defaults live in the settings code, not here.

Bench record and measured results: [tap-to-walk-investigation.md](tap-to-walk-investigation.md).

State-of-the-art research for the automatic fit and the superposition retry: [tap-to-walk-research.md](tap-to-walk-research.md).

## Why the settings sit where they do

Tier 1's toggle is in the Privacy group, next to Hide Teleport Features, because Floating Map Quick Walk is a standalone feature. It is not one of the features that setting hides ([hide-teleport.md](hide-teleport.md)). Tier 2 lives under the Tap to Walk menu. Screen access has no toggle of its own: it is on whenever the accessibility service is granted.

The map scale is a single slider with a default calibrated against a fully zoomed-out AR game map. An earlier version asked players to tap two landmarks and type the real distance between them. It was removed because most players do not know that distance, so the calibrated default plus a manual correction replaces it.

## Tier 1 — Floating Map Quick Walk

When the option is on, a tap on the floating map walks directly instead of opening the action panel. The option is read as composable state, and the tap callbacks use the latest values rather than values captured once at view creation.

## Tier 2 — Screen Tap-Intercept Overlay

The overlay is a full-screen window that takes every touch. It must not set the flag that lets touches pass through to the window below; that flag breaks interception.

Position is read once, at tap time. The tap pixel is in full-display device pixels, computed from the overlay's on-screen location plus the in-window tap. Never use the overlay window size: system bars inset it, and the calibration is fitted on full-display screenshots.

### Pixel to GPS

Primary path: a stored ground calibration (see "Calibration walk") that matches the current display size converts the tap pixel to a ground offset in metres east and north. The offset is applied to the current position using the flat-earth scale that the walk uses, not the haversine radius. Taps past the horizon of the fitted map yield no target.

Fallback: with no matching calibration, the tap is converted with a north-up scale formula. The fallback never blocks a tap. Its accuracy depends on the scale setting matching the game's zoom, and on the map being north-up. The fallback exists because the app assumes no map heading: heading comes from the walked-leg fit only.

Invariants:

- Never read the map orientation or take a screenshot inside the tap callback. The tap reads the cached stored calibration.
- The ground frame is flat-earth at 111320 m per degree, the frame the calibration walk was measured in. The haversine radius drifts about 0.1 percent from those measurements.
- The fallback must stay available until something user-visible can start a calibration walk. That entry must dismiss the overlay first.

## Screen access

The accessibility service exists for two things: taking screenshots for the calibration walk, and reading and tapping the connect control for Reconnect accessory ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)). It captures nothing until the user accepts the disclosure. Where the map points is never read from the screen. It is measured by fitting how the map moves between frames during the calibration walk.

Capture is gated in the bridge that consumers call, not at each call site. Android can enable the service from system settings without the app ever opening, so a call-site check would be bypassable.

### Why the service is named "screen helper"

The service was renamed from an earlier name. Android keys enabled services by component name, so users who updated must turn it on again in Android's settings. Stored choices and the game package survive the rename through a key migration in the datastore module.

### Platform limits

Screenshots require API 30. No fallback exists below it. On older devices the screen-access section is hidden and the service is disabled in the manifest, so it never shows up in Android's accessibility list where it could be enabled without the in-app disclosure. The service also skips binding on connect as a second guard. Tier 1 and Tier 2 quick-walk still work below API 30.

## Prominent Disclosure

Play's accessibility policy rejected releases that showed the disclosure only in a settings sub-screen, and rejected a release that let the accessibility service run without an in-app accept. The disclosure is therefore a single full-screen surface, shown in the normal usage flow, immediately before Android's own consent screen.

It names the accessibility API, the data it reads (periodic screenshots during a walk, and during Reconnect accessory), and the purpose. Play's policy requires the periodic screenshots to be named in this text, in the onboarding card and in the service description. The copy lives in string resources, not here; read it there.

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
- The Tap to Walk switch in Settings, with the two overlay caveats on the same screen.
- Tapping the widget crosshair, which opens the app to the disclosure.
- The screen-access row's open-settings button.
- Calibration adds no new consent path. It needs the service already granted.

The Play Console declaration and store listing are done. Do not re-check them as part of routine work.

## Warning screen

Turning on the overlay shows two caveats: some apps detect mock location and may penalise the account, and accuracy depends on the scale matching the game's zoom. The caveats and the accessibility disclosure share one screen, so the user confirms once. Decline leaves the toggle off and records nothing: the user declined the feature, not the service.

## Anti-cheat

Accessibility services running in the background can be detected by some games. The settings UI says so. The user can turn the service off in system settings; there is no separate in-app toggle, because that would only hide the same signal.

## Calibration walk

The calibration walk fits a ground map for the player's game and screen, so that a tap on the screen maps to a metre offset rather than assuming north-up.

Why it walks and does not teleport: a teleport would be a jump the game can flag, and the walk must not write the teleport cooldown. The walk uses the bench's measured speed. A faster profile risks the game's speed warning.

Why one stored record and not one per zoom or rotation: the game exposes no signal the app can read at tap time. A record keyed by zoom or rotation could never be looked up, so there is a single latest record per device. That is why the status copy asks the user to recalibrate after changing zoom, rotation or display size.

Why per device and not exported: the map depends on the phone's screen and the game's camera. A backup restored on another device would give wrong taps. The record is rejected when its screen size differs from the current display.

The walk spools its frames to the app cache, one leg at a time, because a full walk does not fit in the heap. The solver reads one leg at a time for the same reason. Frames are deleted on every exit path, success or failure.

Any failure stores nothing. The failure reasons are an enum; the copy is in string resources and selected by an exhaustive match, so a new reason fails to compile until it has text.

Invariants a change must not break:

1. Walk speed is the calibration constant, not the user's walk profile.
2. Geometry uses the flat-earth metres-per-degree constant. Do not swap in the haversine radius.
3. The feet pixel goes into the flow code in device pixels. Only the fit converts to the 1080x2340 reference frame. That reference is fixed; stored calibrations are in reference pixels.
4. Opposite legs in the synthetic test must differ in scroll size. Identical frame sets give identical temporal medians, which defeat the rejection of repeated world texture. Real walks never repeat terrain exactly.
5. A stored map is valid only for the screenshot size it was fitted at.

### Ground map

The ground map is a pure-Kotlin perspective model in the model module. It maps device pixels to ground offsets and back, and it has no Android dependency so the math can be tested on the host. A device pixel is scaled into the reference frame, so one calibration serves any screen size. Its valid range was checked only on part of the screen and up to about 31 m; outside that it is extrapolated, not rejected.

### Feet detection and frame flow

Feet detection finds the player's feet pixel by removing the moving world (a per-pixel temporal median over a leg), then looking for the sharp edges of the figure in a central column. Taking the minimum over legs drops roads that run parallel to one walk. The row prior exists because the detector can be pulled up by a range ring or a shadow. The prior is a fallback, and each use of it counts as a failed registration.

Frame flow removes static content (HUD, range rings, companion sprite) from the frames, then measures how each horizontal band of the screen shifted between frames a known distance apart. The perspective fit uses those shifts with robust weighting and rejects bands near the horizon.

Constants here are tuned values with a reason attached. Do not "fix" one back to a value from an older bench version. Known deviations from the bench, each covered by a test:

- Hole filling is a smoothed fill, not the library inpainting. Without the smoothing, boundary streaks copy inward and pull flows to zero.
- Bands that overlap the player disc are down-weighted, because the bench gave them full weight and they biased the forward scale.
- The reported camera is always the positive-scale solution. Both signs describe the same homography.

Parity: each recorded device has a fixture of bench numbers, and a test checks the port's fit and tap offsets against them. Fixtures hold numbers only, never frames. Frames are not committed because the game HUD shows the player's name.

### Status and open work

Accepted state of the no-compass investigation (branch `tap-to-walk-no-compass`; the bench record is in [tap-to-walk-investigation.md](tap-to-walk-investigation.md)):

- Heading and scale come from the walked-leg fit. No north is assumed.
- Wide zoom on one device passes the bar. Close-up zoom does not yet.
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
- Do not use the overlay window size as the screen size.
- Do not use the haversine radius for the ground-map offset.
- Do not read pixels from a hardware bitmap. Copy it to a software config first.

## Shared disclosure

The accessibility disclosure also covers Reconnect accessory ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)). Accepting from the Tap to Walk switch does not set the reconnect acceptance. Reconnect asks for its own acceptance only while neither is accepted; once the accessibility choice is accepted, it records the reconnect acceptance without a dialog.

## Guided path

On the Pikmin Bloom path the Tap to Walk section is removed from the settings tree. The stored setting is untouched. See [guided-paths.md](guided-paths.md).
