# Tap to Walk

Two shortcuts for triggering walk-to without a confirmation sheet, useful when you want to act quickly inside another app. Tier 1 is a quick-walk option on the floating map. Tier 2 is a screen-wide tap interceptor. Tier 2 is the one that needs the accessibility service.

Key files: the widget module's map view, tap overlay and widget service, and `:core:location/ScreenCaptureSource.kt`. Settings keys and their defaults live in the settings code, not here.

Agents: this file is the working memory for Tap to Walk. Read "Ground map: flow-measured" before any work on it, and record each result there (kills included) before the session ends. Keep only what is still relevant: history and superseded detail go to [tap-to-walk-investigation.md](tap-to-walk-investigation.md), not here.

## Why the settings sit where they do

Tier 1's toggle is in the Privacy group, next to Hide Teleport Features, because Floating Map Quick Walk is a standalone feature. It is not one of the features that setting hides ([hide-teleport.md](hide-teleport.md)). Tier 2 lives under the Tap to Walk menu. Screen access has no toggle of its own: it is on whenever the accessibility service is granted.

## Tier 1 — Floating Map Quick Walk

When the option is on, a tap on the floating map walks directly instead of opening the action panel. The option is read as composable state, and the tap callbacks use the latest values rather than values captured once at view creation.

## Tier 2 — Screen Tap-Intercept Overlay

The overlay is a full-screen window that takes every touch. It must not set the flag that lets touches pass through to the window below; that flag breaks interception.

Position is read once, at tap time. The tap pixel is in full-display device pixels, computed from the overlay's on-screen location plus the in-window tap. Never use the overlay window size: system bars inset it, and the calibration is fitted on full-display screenshots.

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

It names the accessibility API, the data it reads (periodic screenshots while walking with Tap to Walk on and the game in front, and during Reconnect accessory), and the purpose. Play's policy requires the periodic screenshots to be named in this text, in the onboarding card and in the service description. The copy lives in string resources, not here; read it there. Every one of those texts says screenshots are taken only while Pokémon GO is on screen, which `ScreenCaptureSource.captureGrayFrame` enforces.

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

Status (2026-10-27): done. The lead accepted the 4-row subset below as the gate pass, Reykjavik included, and closed the backlog epic. The measured rows: Pixel 5, 1080x2340, Pokémon GO, normal scenario, 12 scored taps per row, 0 rejected, `make tap-smoke ONLY="1 3 4 7"`. Each row took 303 to 316 s.

| Row | p50 m | p90 m | App distance / ruler distance (median) | Verdict |
|---|---|---|---|---|
| New York | 4.6 | 8.1 | 0.88 | PASS |
| Tokyo | 6.3 | 12.0 | 0.91 | PASS |
| Sydney | 6.5 | 11.8 | 0.85 | PASS |
| Reykjavik | 10.9 | 17.5 | 1.35 | accepted (p90 over 15 m) |

An earlier New York row on the same build gave p50 4.1 m, p90 7.0 m. The median angle error is -2 to -8.5 degrees in every row, so the heading is good. The remaining error is scale: the app walks 9 to 15% short in three rows and 35% too far in Reykjavik. Three rows used the prior feet anchor (the report's prior fallback count).

The 2026-10-26 failures (11 of 12 rows) came from the old full-band ruler, not the app. See "Gate error cause" in [tap-to-walk-investigation.md](tap-to-walk-investigation.md). Refitted near the feet, the 12 stored references of run `20261026T160909Z` all pass the ruler checks. Their lateral scale is within 3% of the near-feet fits in that section. Those taps cannot be rescored, because the old `trials.json` kept only the error length.

Gate cost: about 5 min per row (Run profile, short reference legs, teleport back after each tap; see docs/testing.md). Not run: rows 2, 5, 6 and 8 to 20, and the fresh, twist and zoom rows. If tap to walk work reopens, start there.

Always run `./gradlew installDebug` before the gate when app code changed since the phone's install, then start spoofing and turn on the Control API (the port changes on each toggle). A stale APK once scored 0 taps.

Forward ratio. The near-feet refit of the 12 stored references gives forward / lateral 0.47 to 0.67 (10 of 12 within 0.53 to 0.59), against `FORWARD_RATIO` 0.40. Most taps land beside the player, so this costs little today. It matters once taps spread up the screen. Raising it is the first candidate if the scale error needs work.

### Goal

A tap on the game screen walks to the spot under the finger, out of the box, on any phone, at any camera heading and zoom.

### Constraints (user, 2026-10-23 and 2026-10-24)

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
- A fresh install has no stored map. Taps then use the provisional map (`PROVISIONAL_HEADING_DEG`, `PROVISIONAL_LATERAL_PX_PER_M`). The walk the tap starts is measured, and the widget service re-aims the same tap from its tap-time position once a new map is stored and moves the target by at least `REAIM_MIN_METERS`. The same re-aim corrects a stored heading after a camera twist.
- `AutoFitCoordinator` fits the moves of the current walk only (at most `FLOW_WINDOW_MOVES`), and needs two moves before it stores a map. A stop, a teleport-speed step or a closed gate ends the walk and drops its crops. So a twist between walks never mixes two cameras.
- A walk replaces the stored map only through `GroundFlowFit.replaces`: the fit must be self-consistent (flow count, residual); one that agrees with the stored map refreshes it, and one that disagrees (a camera change) needs more moves and a tighter residual. `move` drops screenshot pairs where no crop moved (loading screens, popups). The thresholds are unmeasured guesses; tune them from the device gate. Tests use PokeMiners loading and warning screens (`core/model/src/test/resources/pokeminers/`) as static-frame fixtures.
- The stored record is per device, never exported, and rejected when the display size differs. Version 2 of the record rejects maps from the old motion fit.

Accuracy on synthetic frames (unit tests): heading within 2.5 degrees, scale within 5%, tap error under 5% of the tap distance. On the bench (Python prototype, one 8-frame leg per fit, 2026-10-24): heading usually within 8 degrees of the old four-term fit, landing p90 12 to 75 m against that fit. The old fit is itself noisy, so these numbers are not truth. The device gate is the next step.

### Open work

None scheduled. Ideas that were not needed to finish: tune `FORWARD_RATIO` from the measured fits, real-game crop fixtures for a regression test, a second screen size, pruning the bench of the deleted motion fit, keeping a heading across walks when one walk is weak, reading only the crop rows from the screenshot, and zoom changes within a walk.

### Rejected, do not retry

- The range ring as the scale source. It is an animated pulse, not a fixed circle: its radius changes between screenshots 3 s apart (about 110 px and 500 px in New York), and a detector found it on few frames in Mumbai, Moscow, Nairobi and at night (2026-10-24).
- A per-latitude (Web Mercator) scale table. The scale times cos(lat) spread 1.96 to 10.57 across ten places.
- Screenshot registration against the app's own map, road and water colour masks against OSM tiles, and zoom as one scale parameter. A single tap-time screenshot superposed on a known-position map was proposed again on 2026-10-24 and declined for the flow-measured draft: both past attempts landed hundreds of metres off, and a street grid looks alike at 90 and 180 degrees.
- The four-term motion fit from a frame spool: minutes of CPU per fit and a learning walk. Deleted in the flow-measured draft.

Details for each are in [tap-to-walk-investigation.md](tap-to-walk-investigation.md).

### Facts from PokeMiners and the community

- Game master ([PokeMiners/game_masters](https://github.com/PokeMiners/game_masters)), template `MAP_OBJECTS_INTERACTION_RANGE_SETTINGS`: `interactionRangeMeters` 80, `whitePulseRadiusMeters` 50. The committed copy is `core/common/src/test/resources/pokeminers/interaction_range_settings.json`, written by `scripts/pokeminers_fetch.py`. `TapToWalkRingConstantsTest` pins `TapToWalkConstants` to it. The game master has no camera pitch, field of view or zoom setting.
- The 50 m white pulse explains why the ring changes size: the drawn ring grows over time.
- Reddit blocks plain curl and the fetch tool. Read posts through the Arctic Shift API (`https://arctic-shift.photon-reddit.com/api/posts/ids?ids=<id>`).

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
