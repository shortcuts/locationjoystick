# Tap to Walk

Two shortcuts for triggering walk-to without a confirmation sheet, useful when you want to act quickly inside another app. Tier 1 is a quick-walk option on the floating map. Tier 2 is a screen-wide tap interceptor. Tier 2 is the one that needs the accessibility service.

Key files: the widget module's map view, tap overlay and widget service, and `:core:location/ScreenCaptureSource.kt`. Settings keys and their defaults live in the settings code, not here.

Agents: this file is the working memory for Tap to Walk. Read "Ground map: current direction" before any work on it, and record each result there (kills included) before the session ends. Keep only what is still relevant: history and superseded detail go to [tap-to-walk-investigation.md](tap-to-walk-investigation.md), not here.

## Why the settings sit where they do

Tier 1's toggle is in the Privacy group, next to Hide Teleport Features, because Floating Map Quick Walk is a standalone feature. It is not one of the features that setting hides ([hide-teleport.md](hide-teleport.md)). Tier 2 lives under the Tap to Walk menu. Screen access has no toggle of its own: it is on whenever the accessibility service is granted.

## Tier 1 — Floating Map Quick Walk

When the option is on, a tap on the floating map walks directly instead of opening the action panel. The option is read as composable state, and the tap callbacks use the latest values rather than values captured once at view creation.

## Tier 2 — Screen Tap-Intercept Overlay

The overlay is a full-screen window that takes every touch. It must not set the flag that lets touches pass through to the window below; that flag breaks interception.

Position is read once, at tap time. The tap pixel is in full-display device pixels, computed from the overlay's on-screen location plus the in-window tap. Never use the overlay window size: system bars inset it, and the calibration is fitted on full-display screenshots.

### Pixel to GPS

A stored ground map that matches the current display size converts the tap pixel to a ground offset in metres east and north. The offset is applied to the current position with the flat-earth scale that the walk uses, not the haversine radius. A tap past the horizon of the map yields no target. With no usable map (below API 30, or no stored terms for this display), the tap walks nowhere and a toast says why.

An earlier version fell back to a north-up formula with a hand-set scale. It was removed: the game camera has no north, and the scale asked the player for something they cannot know.

Invariants:

- Never take a screenshot or read the map orientation inside the tap callback. The tap reads stored terms only. Screenshots are taken outside the tap path, while the app walks.
- The ground frame is flat-earth at 111320 m per degree. The haversine radius drifts about 0.1 percent from it.

## Screen access

The accessibility service exists for two things: taking screenshots for the automatic fit, and reading and tapping the connect control for Reconnect accessory ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)). It captures nothing until the user accepts the disclosure. Where the map points is measured from small screenshots taken while the app walks, never from the tap.

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

## Automatic fit

The app ships a motion fit: it spools screenshots of every straight walk, measures how bands of the screen move, and fits the four ground-map terms. It is being replaced (see below) and deleted once the replacement passes its gate. Do not optimise or extend it. Its design, bench numbers and the 2026-10-23 cost audit are in [tap-to-walk-investigation.md](tap-to-walk-investigation.md). The stored record stays per device and is never exported, because the map depends on the phone's screen and the game's camera. A record is rejected when its screen size differs from the current display.

## Ground map: current direction

### Constraints (user, 2026-10-23)

- No user training, no user configuration, no calibration step and no user action. Every constant comes from pre-release work.
- A landing error up to 15 m is acceptable.
- Cost must fit the problem: a few small screenshots and milliseconds of CPU. No frame spool, no analysis that takes minutes.
- The released app must work out of the box: no configuration, no extra walk, no message asking the user to move. Tests and the bench may do anything (teleports, probe walks, Control API) to prove the implementation.

### Killed, do not retry

- Screenshot registration against the app's own map: p50 221 m, no score separates a right match from a wrong one.
- Road and water colour masks against OSM tiles (entry B): true heading top peak on 32% of frames, p90 646 m. The game styles roads differently from OSM, and its colours change with day, night and weather.
- Zoom as one scale parameter: the focal length spread 38% across four zooms, because a zoom also changes the pitch.

Details are in the investigation doc.

### Model

The ground map has four terms: heading, lateral scale, forward scale and perspective.

- Scale and perspective come from latitude. The game draws OSM in Web Mercator, so at a fixed zoom, screen pixels per metre scale with 1/cos(lat). Per-device terms are stored in equator units and divided by cos(lat) at tap time.
- The range ring gives the same terms on devices with no pre-release record. The ring is a ground circle of known radius centred on the feet, so its fitted ellipse gives scale and perspective from one screenshot. At default zoom, position alone predicts the ring; a ring that differs from the prediction means the user zoomed.
- Zoom: a pre-release table of terms per zoom step, per device. The ring picks the row.
- Heading: a circle cannot give it, so it is the only term measured at run time. During any walk the app already makes, two small low-resolution crops near the feet give the screen direction of the ground flow (one phase correlation), and the mock bearing is known. The heading is stored until a measurement says it changed. The game keeps the camera heading across teleports until the user twists it.
- The tap walk carries the measurement. With a stored heading the walk starts at once, and its flow confirms the heading or corrects it. With no heading (fresh install), the walk starts under a provisional heading (the game's start-up heading if the bench shows it is fixed, an open fact below), and the same tap is re-aimed from its tap-time position as soon as the first flow gives the heading. The cost is a few metres in the wrong direction, never an extra walk.

### Facts from PokeMiners and the community

- Game master ([PokeMiners/game_masters](https://github.com/PokeMiners/game_masters), `latest`, timestamp 1787902550208, read 2026-10-23), template `MAP_OBJECTS_INTERACTION_RANGE_SETTINGS`: `interactionRangeMeters` 80, `whitePulseRadiusMeters` 50, `farInteractionRangeMeters` 2000, `remoteInteractionRangeMeters` 3500. `previous_game_masters` holds the history, so a radius change can be dated. The game master has no camera pitch, field of view or zoom setting.
- The committed copy is `core/common/src/test/resources/pokeminers/interaction_range_settings.json`, written by `scripts/pokeminers_fetch.py`. `TapToWalkRingConstantsTest` pins the ring constants in `TapToWalkConstants` to it.
- Radius history (`scripts/pokeminers_fetch.py --history`, 240 versions from 2021-06 to 2026-08, three of them without the template): the interaction range is the same in every version that carries it. The white pulse first appears at 40 m in the 2024-01-23 version and becomes 50 m on 2024-02-07. No version before 2021-06 is in the repo, so the "40 m before 2020" premise cannot be checked here.
- [pogo_assets](https://github.com/PokeMiners/pogo_assets) has no camera or ring geometry. Folders worth using as detector templates and test material: `Images/Loading Screens` (a static loading screen fooled five smoke locations), `Images/Warnings` (the speed warning), `Images/Pokestops and Gyms` (objects that cover the ring), `Images/Weather` (weather changes the map colours) and `3D Assets/Characters` (the avatar).
- [Interaction range post](https://www.reddit.com/r/TheSilphRoad/comments/od48zr/clearing_up_some_misconceptions_regarding_the/) (2021): the radius is 80 m and its real distance does not vary with latitude. Only its on-screen size does.
- [Rings and map size post](https://www.reddit.com/r/TheSilphRoad/comments/1vytd28/your_rings_of_influence_and_map_size_gets_larger/) (2026): the rings and the visible map grow on screen with latitude (Web Mercator). Its screenshots at 35.5 to 69.6 degrees N show two rings centred on the feet, drawn as ellipses. At 69.6 degrees the outer ring runs off both sides of the screen.

Measured on the existing smoke fits (Pixel 5, run `20261017T132945Z`, one zoom, camera untouched between teleports):

| Place | Latitude | Lateral px/m | Lateral x cos(lat) | Forward x cos(lat) |
|---|---|---|---|---|
| Tokyo | 35.7 | 5.60 | 4.55 | 1.94 |
| New York | 40.8 | 6.25 | 4.73 | 1.90 |
| London | 51.5 | 7.74 | 4.82 | 1.92 |
| Sydney | -33.9 | 6.30 | 5.23 | 2.05 |

The raw scale spreads about 16%; after the cos(lat) correction, about 7%. A 7% scale error at a 30 m tap is about 2 m. The heading stayed near -16 degrees across all four teleports (Sydney's 162.9 degrees is the same map with the sign flipped). This is one device and one zoom.

### Open facts

Check these on the bench before writing app code for them:

- Which drawn ring is 80 m and which is 50 m, and whether the 50 m white pulse changes size over time.
- Ring detection at close-up zoom, at high latitude (partial arcs) and under map objects.
- Whether the game restores a fixed zoom and a fixed heading on each start.
- Whether zoom is stepped or continuous. Continuous zoom needs the table interpolated.
- How the terms carry to another screen size. Pixel 7 (1080x2400) bench data exists.
- How exact one-move heading is. The two-move prototype had a standard deviation of 1.7 to 3 degrees. One degree is about 6 m at the top of the frame, so 15 m allows about 2.5 degrees for far taps.

### Gate

p90 landing error at most 15 m with at most 20% of taps rejected, first on recorded frames with truth from commanded bearings, then with the tap smoke command in [testing.md](../testing.md). Kill this direction if the ring is not found on more than 20% of default-zoom frames, or if the one-move heading alone exceeds the 15 m budget. Record a kill here and move its detail to the investigation doc.

### Test material

- Real frames may be committed only as map crops with the HUD rows blanked (the username is at the bottom of the screen), at reduced resolution. A check must prove the blanked rows are empty. Full frames stay under the git-ignored `scripts/registration-bench/`.
- Detector templates from `pogo_assets` are downloaded, not committed: `scripts/pokeminers_fetch.py --assets` writes them to the git-ignored `scripts/registration-bench/pokeminers/assets/`. Its `MANIFEST.json` records the commit read.
- The smoke fits are under `scripts/registration-bench/smoke/` (git-ignored, local only). A fit with a scale near 0 is a static loading screen and carries no truth.
- Reddit blocks plain curl and the fetch tool. Read posts and comments through the Arctic Shift API (`https://arctic-shift.photon-reddit.com/api/posts/ids?ids=<id>` and `/api/comments/tree?link_id=<id>`). Download images with a browser user agent.

## Anti-patterns to avoid

- Do not add the flag that lets touches pass through to the overlay window. It breaks interception.
- Do not read position again inside the tap callback. Capture it once at tap time.
- Do not persist the active state of Tap to Walk. It is session state.
- Do not capture a screenshot or read a heading inside the tap callback.
- Do not search heading, scale and zoom together in one free fit. Latitude gives the scale, the ring gives the zoom, and only the heading is measured.
- Do not use the overlay window size as the screen size.
- Do not use the haversine radius for the ground-map offset.
- Do not read pixels from a hardware bitmap. Copy it to a software config first.

## Shared disclosure

The accessibility disclosure also covers Reconnect accessory ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)). Accepting from the Tap to Walk switch does not set the reconnect acceptance. Reconnect asks for its own acceptance only while neither is accepted; once the accessibility choice is accepted, it records the reconnect acceptance without a dialog.

Both features share one screenshot budget ([bluetooth-reconnect-tap.md](bluetooth-reconnect-tap.md)): every screenshot is serialized and spaced, because the platform rejects shots taken too close together.

## Guided path

On the path that hides it, the Tap to Walk section is removed from the settings tree. The stored setting is untouched. See [guided-paths.md](guided-paths.md).
