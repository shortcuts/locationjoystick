# Tap to Walk

Two shortcuts for triggering walk-to without a confirmation sheet, useful when you want to act quickly inside another app. Tier 1 is a quick-walk option on the floating map. Tier 2 is a screen-wide tap interceptor that walks, walks via roads or teleports, as the user chose. Tier 2 is the one that needs the accessibility service.

Key files: the widget module's map view, tap overlay, `TapToWalkController` and widget service, and `:core:location/ScreenCaptureSource.kt`. Settings keys and their defaults live in the settings code, not here.

Agents: read "Ground map: flow-measured" before any work on it. Record the rationale of a result and each rejected approach there. Keep dates, devices and raw measurements out of the doc.

## Why the settings sit where they do

Tier 1's toggle is in the Teleport and walking group, next to Hide Teleport Features, because Floating Map Quick Walk is a standalone feature. It is not one of the features that setting hides ([hide-teleport.md](hide-teleport.md)). Tier 2 lives under the Tap to Walk menu. Screen access has no toggle of its own: it is on whenever the accessibility service is granted.

## Tier 1 — Floating Map Quick Walk

When the option is on, a tap on the floating map walks directly instead of opening the action panel. The option is read as composable state, and the tap callbacks use the latest values rather than values captured once at view creation.

## Tier 2 — Screen Tap-Intercept Overlay

The overlay is a full-screen window that takes every touch. It must not set the flag that lets touches pass through to the window below; that flag breaks interception.

Position is read once, at tap time. The tap pixel is in full-display device pixels, computed from the overlay's on-screen location plus the in-window tap. Never use the overlay window size: system bars inset it, and the calibration is fitted on full-display screenshots.

### Move options

A tap does not always walk. Settings holds a set of allowed moves: walk, walk via roads and teleport. At least one stays checked, so a tap never has nothing to do.

- One effective move: the tap runs it at once, as before.
- Two or more: the tap opens the floating map's action panel inside the overlay window, with only the checked moves plus "Do nothing". The panel is reused so the cooldown badge, share action and wording stay in one place. The overlay stays up until the user picks, and a tap outside the panel does nothing, so a stray tap cannot start a move.
- Hide teleport removes Teleport from the settings and from the panel but keeps the stored choice ([hide-teleport.md](hide-teleport.md)). If Teleport was the only stored move, a tap falls back to walk.
- The target is resolved again when the user picks. A ground map stored while the panel was open then applies.
- Teleport goes through `TeleportUseCase` via the map controller, so it keeps persistence and the cooldown.
- A newer ground map re-aims a walk and re-routes a road walk. A teleport never re-aims: it is instant.

Known limit: a road walk gets its target only after the router answers. A map that arrives before the answer does not re-aim it.

### Pixel to GPS

A stored ground map that matches the current display size converts the tap pixel to a ground offset in metres east and north. The offset is applied to the current position with the flat-earth scale that the walk uses, not the haversine radius. A tap past the horizon of the map yields no target. With no stored map for this display, the tap uses the provisional map and the walk re-aims it (see below). Below API 30 no flow can correct it, so the tap walks nowhere and a toast says why. A tap past the horizon gets its own toast.

An earlier version fell back to a north-up formula with a hand-set scale. It was removed: the game camera has no north, and the scale asked the player for something they cannot know.

Invariants:

- Never take a screenshot or read the map orientation inside the tap callback. The tap reads stored terms only. Screenshots are taken outside the tap path, while the app walks.
- The ground frame is flat-earth at 111320 m per degree. The haversine radius drifts about 0.1 percent from it.

## Screen access

The accessibility service exists for two things: taking screenshots for the flow-measured ground map, and reading and tapping the connect control for Reconnect accessory ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)). It captures nothing until the user accepts the disclosure. Where the map points is measured from small screenshots taken while the app walks, never from the tap.

Capture is gated in the bridge that consumers call, not at each call site: disclosure accepted, screen on and the game in front. Android can enable the service from system settings without the app ever opening, so a call-site check would be bypassable. The disclosure names the game in front as the only time frames are taken, so the gate is part of the consent, not only of battery care.

### Why the service is named "screen helper"

The service was renamed from an earlier name. Android keys enabled services by component name, so users who updated must turn it on again in Android's settings. The stored disclosure choice survives the rename through a key migration in the datastore module.

### Platform limits

Screenshots require API 30. Below it no map can exist, so the overlay walks nowhere. The status block is one shared Screen access block above both feature sections, shown while either switch is on, so a Reconnect-only user still sees whether the service runs; each section adds a service-off hint when its switch is on without the service. On older devices the block is hidden and the service is disabled in the manifest, so it never shows up in Android's accessibility list where it could be enabled without the in-app disclosure. The service also skips binding on connect as a second guard. Tier 1 and Tier 2 quick-walk still work below API 30.

## Prominent Disclosure

Play's accessibility policy rejected releases that showed the disclosure only in a settings sub-screen, and rejected a release that let the accessibility service run without an in-app accept. The disclosure is therefore a single full-screen surface, shown in the normal usage flow, immediately before Android's own consent screen.

It names the accessibility API, the data it reads (periodic screenshots while walking with Tap to Walk on and the game in front, and during Reconnect accessory), and the purpose. Play's policy requires the periodic screenshots to be named in this text, in the onboarding card and in the service description. The copy lives in string resources, not here; read it there. Every one of those texts says screenshots are taken only while the game is on screen, which `ScreenCaptureSource.captureCrops` enforces.

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
- The ground map adds no new consent path. Its screenshots need the service granted and the disclosure accepted, both checked in the capture bridge. Keep every new screenshot inside the purpose the disclosure names: while walking, Tap to Walk on, the game in front.

The Play Console declaration and store listing are done. Do not re-check them as part of routine work.

## Warning screen

Turning on the overlay shows a caveat that the overlay intercepts taps. The caveat and the accessibility disclosure share one screen, so the user confirms once. Decline leaves the toggle off and records nothing: the user declined the feature, not the service.

## Anti-cheat

Accessibility services running in the background can be detected by some games. The settings UI says so. The user can turn the service off in system settings; there is no separate in-app toggle, because that would only hide the same signal.

## Ground map: flow-measured

Status: done. The flow-measured map met the landing-error gate on the bench rows that were run, so no work is scheduled.

- The remaining error is scale, not heading. The heading fit is good in every bench row. Scale varies by place, which is why it is measured and not tabled.
- Slow Walk moves under 1 m per 3 s tick, so the coordinator's stillness speed (`MIN_SPEED_MPS`) and `GroundFlowFit.MIN_MOVE_M` are set below it. A tick that moved less than `MIN_MOVE_M` since the last screenshot takes none and keeps the earlier frame as the reference. Bike and Drive exceed `FLOW_MAX_SPEED_MPS`, because the flow would overflow the crop search. They never measure and keep the last stored map. A phone that only uses Bike or Drive keeps the provisional map.
- `FORWARD_RATIO` came from the good bench fits. Refits spread above it, so raising it is the first candidate if the scale error needs work. It matters little while most taps land beside the player.
- A bench row that shows every target mirrored is the signature of the provisional map. Install the current APK before the gate, then start spoofing and turn on the Control API (the port changes on each toggle). A stale APK once scored 0 taps.
- A bench row can fail from the ruler, not the app. The old full-band ruler did. Judge a failure against the near-feet fit before blaming the app.
- The gate takes minutes per row on Run and longer on Walk, because the reference legs, the app leg and the teleport back all run at walking speed (docs/testing.md).

### Goal

A tap on the game screen walks to the spot under the finger, out of the box, on any phone, at any camera heading and zoom.

### Constraints

- No user training, no user configuration, no calibration step and no user action. Every constant comes from pre-release work.
- A landing error up to 15 m is acceptable. Gate: p90 at most 15 m with at most 20% of taps rejected.
- Cost must fit the problem: a few small crops and milliseconds of CPU per screenshot. No frame spool, no analysis that takes minutes.
- The released app works out of the box: no configuration, no extra walk, no message asking the user to move. Tests and the bench may do anything (teleports, probe walks, Control API).
- Ship a good-enough version first, then improve it from bench results. Do not run long benches before a draft exists.

### Model

`GroundFlowFit` in `:core:model` measures two terms; the other two are fixed ratios.

- Heading and lateral scale are measured. During any walk the app already makes, each 3 s screenshot is reduced to five small crops (left and right of the player, and higher up). Phase correlation of each crop against the previous screenshot gives its flow. The mock move between the two screenshots is known, so a two-term fit gives the heading and the scale.
- Forward scale and perspective are fixed ratios of the lateral scale (`FORWARD_RATIO`, `PERSPECTIVE_RATIO`). The game camera's pitch does not change with the place, so these ratios held across the good bench fits (0.39 to 0.44, and -1.3e-4 to -2.5e-4). The lateral scale varied 2x across places and sessions, which is why it is measured and not tabled.
- The feet sit at a fixed screen fraction (`FEET_X`, `FEET_Y`). The game keeps the player centred.
- A fresh install has no stored map. Taps then use the provisional map (`PROVISIONAL_HEADING_DEG`, `PROVISIONAL_LATERAL_PX_PER_M`). The player turns the camera between walks at will, so a stored map can point the wrong way too. Both cases are caught by the walk the tap starts, not by a stored-map rule.
- Live walk heading: `GroundFlowFit.heading` reads the camera heading from the direction of the crop flows only. The game moves its map in bursts: it stands still for seconds while the mock position advances, then catches up. So a flow's size rarely matches the mock move between two screenshots, but its direction does. A heading counts only when the next window's heading agrees with it within `AGREE_DEG`: a one-move heading was sometimes over 90 degrees off on real frames. When a counted heading differs from the tap's map by more than `OVERRIDE_DEG`, `AutoFitCoordinator.walkFit` publishes it with the tap map's scale, and the tap to walk controller re-aims the tap from its tap-time position (at least `REAIM_MIN_METERS`). `OVERRIDE_DEG` sits above the spread of one camera's headings; a smaller gap re-aimed the walk on noise and zig-zagged it. A wrong scale only misplaces the landing; a wrong heading walks the wrong way, which is the failure this path exists for.
- `walkFit` outlives the walk: it is the freshest measurement, so the next tap uses it before the stored map. It clears only when a stored map replaces it, and the controller never re-aims on the clear: re-aiming then with the older map flipped a corrected tap back to the wrong way.
- The first moves of a walk are sampled at `FLOW_FAST_INTERVAL_MS`, later ones at `FLOW_FRAME_INTERVAL_MS`, so a wrong-way tap is corrected within seconds and a long walk still costs few screenshots.
- Players turn the camera often, so every walked tap is treated as a new camera. The tap calls `AutoFitCoordinator.startWalk`: the next tick drops the earlier moves and takes a reference frame before the player moves. That screenshot is part of the walk the tap starts, so it stays inside the disclosure. No frame is taken when the overlay opens or between walks.
- A tap made mid-walk waits `SETTLE_MS` for its reference frame. The game's camera trails the mock position, so a frame taken at once still carries the old direction. That bias entered every flow measured against the frame and confirmed headings tens of degrees off on real taps.
- The next frame checks the reference frame (`GroundFlowFit.steady`): the map must not move more than the walk since explains, or the newer frame becomes the reference. A camera still animating after the game resumed confirmed headings far off on real taps, and the bias enters every flow measured against that frame. The check uses the frame at the first mock step, which was skipped before, so a steady reference adds no delay. Rain can fail the check for a few seconds; the line then stays dashed longer, which is the safe side.
- While aiming, each frame is measured against the reference frame, not the previous one. The flow then grows with the walk, so two headings exist after about 1 and 2 m. Consecutive moves each need `MIN_MOVE_M`, which doubled the wait at walking speed. Past `REANCHOR_M` the reference moves to a newer frame, because the flow nears the crop search size.
- The aim lifecycle is the `Aim` state machine in `AutoFitCoordinator`. `aiming` is true from the tap until two headings agree within `AGREE_DEG` or the walk ends. The floating map draws the remaining walk line dashed meanwhile. The tap's first target comes from the last map, which a turned camera makes a guess. A solid line would claim a direction the app does not know yet.
- At walking speed the heading is confirmed about 3 s after a tap from standstill, and about 4 s after a tap mid-walk. The limit is the flow size: below `MIN_MOVE_M` and a few pixels of flow, the direction is noise. So the first metre or two of a tap after a camera turn still follows the old map. Only a screenshot before the walk could avoid that, and the disclosure does not cover one.
- A move whose direction turns more than `TURN_MAX_DEG` from the previous one (a re-aim, a new tap) is dropped and the window restarts. The game camera trails the mock position, so a move across a turn measures neither leg; one such move once produced a near-reverse fit.
- `AutoFitCoordinator` fits the moves of the current walk only (at most `FLOW_WINDOW_MOVES`), and needs two moves before it stores a map. A stop, a teleport-speed step or a closed gate ends the walk and drops its crops. So a twist between walks never mixes two cameras. A twist or pinch inside a walk shows as a move whose residual against the running fit is far above the fit's median (`GroundFlowFit.changesCamera`); the coordinator then drops the earlier moves and refits from the new one. The thresholds are unmeasured guesses.
- A walk replaces the stored map only through `GroundFlowFit.replaces`, and only when the flow-direction heading backs the fit's heading: the fit must be self-consistent (flow count, residual); one that agrees with the stored map is written only when heading drifts over `REWRITE_HEADING_DEG` or scale over `REWRITE_SCALE_FRACTION`, so a walk on an unchanged camera writes once (fits of one camera spread a few degrees on real frames, and each rewrite re-aims the walk), and one that disagrees (a camera change) needs more moves and either a tighter residual or a stored map that explains the same moves far worse (`GroundFlowFit.outperforms`). The fixed residual bar alone never let a turned camera through on real frames: rain and animated sprites keep a correct fit's residual above it. `move` drops screenshot pairs where no crop moved (loading screens, popups). The thresholds are only partly measured: Walk and Slow Walk are covered by the rows above. Tests use loading and warning screens (`core/model/src/test/resources/pokeminers/`) as static-frame fixtures.
- The stored record is per device, never exported, and rejected when the display size differs. Version 2 of the record rejects maps from the old motion fit.

Accuracy on synthetic frames (unit tests): heading within 2.5 degrees, scale within 5%, tap error under 5% of the tap distance.

Cost: the screenshot is a hardware bitmap with no pixel access, so one software copy stays. Only the five crop rectangles (about 13% of the pixels) become gray, and no full-frame gray buffer exists. The remaining cost is mostly the phase-correlation transform per crop, not the gray conversion. The transform uses `sqrt` instead of `hypot`, cached Hann windows and cached `Dft` tables per thread.

Battery: auto-fit runs only while the player walks with the game in front. It takes no screenshot while the player stands still. The cost is small next to the game's own drain, so no optimization task follows.

### Open work

- The accessibility service learns that the game is in front only from window-change events. After the service reconnects while the game stays open, it takes no screenshot until the next app switch. `uiautomator dump` causes such a reconnect, so a device harness must switch apps after it.

Nothing else is scheduled. Ideas that were not needed to finish: tune `FORWARD_RATIO` from the measured fits, real-game crop fixtures for a regression test, a second screen size, pruning the bench of the deleted motion fit, reading only the crop rows from the screenshot, and measuring the camera-change thresholds.

### Rejected, do not retry

- The two-term fit as the live correction, on one or two short moves. The game's bursty map motion gave one-move fits far off, including a near-reverse heading. The flow-direction heading replaced it.
- Weighting each flow by its phase-correlation response, and dropping flows larger than the crop search allows, to sharpen the one-move heading. A replay of the recorded walks showed no gain: the share of one-move headings off by more than 45 degrees did not drop. Requiring a second agreeing move did.

- A faster tick while aiming. The mock position moves once per `UPDATE_INTERVAL_MS`, so a tick between two steps sees no move and the heading comes no sooner.

- The range ring as the scale source. It is an animated pulse, not a fixed circle: its radius changes between screenshots 3 s apart, and a detector found it on few frames in several places and at night.
- A per-latitude (Web Mercator) scale table. The scale times cos(lat) spread over a 5x range across ten places.
- Screenshot registration against the app's own map, road and water colour masks against OSM tiles, and zoom as one scale parameter. A single tap-time screenshot superposed on a known-position map was proposed again and declined for the flow-measured draft: both past attempts landed hundreds of metres off, and a street grid looks alike at 90 and 180 degrees.
- The four-term motion fit from a frame spool: minutes of CPU per fit and a learning walk. Deleted in the flow-measured draft.

### Test material

- Real frames may be committed only as map crops with the HUD rows blanked (the username is at the bottom of the screen), at reduced resolution. Full frames stay under the git-ignored `scripts/registration-bench/`.
- The bench walks are under `scripts/registration-bench/smoke/` (git-ignored, local only). Each `walk_C_<bearing>/` holds frames and a `meta.json` with the mock position per frame. A frame of about 200 KB is a static loading screen and carries no truth.

## Anti-patterns to avoid

- Do not add the flag that lets touches pass through to the overlay window. It breaks interception.
- Do not read position again inside the tap callback. Capture it once at tap time.
- Do not persist the active state of Tap to Walk. It is session state.
- Do not capture a screenshot or read a heading inside the tap callback.
- Do not fit forward scale or perspective as free terms from one walk. One straight walk pins two terms; the camera pitch fixes the other two.
- Do not keep full screenshots past the tick that took them. Keep crops only, in memory.
- Do not use the overlay window size as the screen size.
- Do not use the haversine radius for the ground-map offset.
- Do not read pixels from a hardware bitmap. Copy it to a software config first.

## Shared disclosure

The accessibility disclosure also covers Reconnect accessory ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)). Accepting it from either switch covers both. Reconnect stays a separate opt-in: its switch must still be turned on.

Both features share one screenshot budget ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)): every screenshot is serialized and spaced, because the platform rejects shots taken too close together.

## Guided path

On the path that hides it, the Tap to Walk section is removed from the settings tree. The stored setting is untouched. See [guided-paths.md](guided-paths.md).
