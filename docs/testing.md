# Testing

## What `make test` runs

`make test` runs `testRelease`. Android modules run their release unit tests. JVM modules (`:core:model`, `:lint:checks`) get a `testRelease` alias from the JVM convention plugin, so their `test` task runs too. CI runs `make test`, so a failing JVM test fails CI.

## Coverage

Coverage via [kotlinx-kover](https://github.com/Kotlin/kotlinx-kover) (v0.9.9). All modules use convention plugins. Root aggregates into merged report.

```bash
make coverage        # generate HTML + XML reports
make coverage-open   # open HTML report in browser
```

Per-module:

```bash
./gradlew :<module>:koverHtmlReport
```

Reports:
- HTML: `build/reports/kover/html/index.html`
- XML (CI): `build/reports/kover/report.xml`

## Smoke Tests (`:app` androidTest)

End-to-end navigation suite. Requires a connected device or emulator. Runs against the debug build via Hilt test module (in-memory Room, real DataStore).

```bash
make smoke-test
```

One smoke test class per screen, each checking that the screen loads, its main actions are present, and its navigation returns the user to where they came from. The test files are the reference for the exact assertions. The Control API smoke test is the exception to "UI only": it checks that the leader exposes exactly the endpoints documented in the wiki, so the public contract and the code cannot drift apart unnoticed. It never asserts endpoint behaviour.

Helpers are in the smoke test helper file.

## Test phone setup (screenshot harness)

Tap to Walk's Screen access block in Settings (screenshot steps 21 and 22) only renders the service state when the Tap to Walk accessibility service is granted and Tap to Walk is on. A force-stop clears the grant, so `scripts/screenshot-gallery.sh` re-grants it over adb after its own force-stop (`grant_tap_to_walk_accessibility`). Test devices only. To repeat the grant by hand:

```bash
adb shell settings put secure enabled_accessibility_services com.locationjoystick.app/com.locationjoystick.feature.widget.impl.ScreenAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell settings get secure enabled_accessibility_services   # must list the service
```

Re-run the grant after any `am force-stop com.locationjoystick.app`. The helper appends to the existing list, so other enabled services stay on.

The same grant lets an agent run the Reconnect accessory check on a device without asking. The service reads the foreground app only from window events, so after the grant background the game and launch it again, or every run ends `GATE_CLOSED`. `adb shell cmd bluetooth_manager disable` makes the companion unreachable and forces `FAILED` runs; `enable` brings it back. Watch `adb logcat -d | grep "Reconnect check"` and `adb shell dumpsys notification --noredact | grep com.locationjoystick.app` (id 1003 is the failure notice). `installDebug` replaces the app, so re-check the grant afterwards.

With the grant in place, Enable Tap to Walk shows "Enable anyway" instead of the Play accessibility disclosure. The disclosure acceptance flag is set only by tapping Accept in the app; adb does not set it.

Check: `./scripts/screenshot-gallery.sh --auto --steps 21` exits 0 and writes `docs/wiki/screenshots/21_screen_access.png` with no `Could not find UI element` warning.

## Live check: resume after joystick steering

Proves that Resume walks back from the steered position instead of snapping. Positions come from `adb shell dumpsys location | grep -m1 "last mock location"`; `adb logcat` shows no per-tick position and may miss the "Replay paused" line.

1. Wake and swipe up to unlock. Run `adb shell appops set com.locationjoystick.app android:mock_location allow`, then `installDebug` and start spoofing from the Home "Start" action.
2. Start a route from Routes (play, then Start). The widget launcher is the compass bubble at the screen edge. A tap expands its panel; the joystick toggle is the second icon. Dragging the bubble moves the whole overlay, so never start a drag on it.
3. With the stick visible, hold a swipe from the stick centre, for example `adb shell input swipe X Y X Y-90 45000`. A 45 s hold moves the position about 12 m at walking speed: speed, not distance, limits the test. The route row then shows play instead of pause.
4. Record the position, tap the route row's play (or, for roaming, the compass in the panel and then the green play in its sub-menu), and sample the position every second.

Pass: the first sample after Resume equals the steered position and later samples advance by about one speed step per second, with no jump back to the pre-pause position. Verified on route replay and on roaming.

## Tap-to-walk global smoke gate

On-device regression gate for tap-to-walk landing: 20 fixed locations across the globe, 12 scored taps each.
It drives the bench (`scripts/registration_bench.py` `walkcap`, `persp`, `taptrials`) per location, so scoring is the
same masked-NCC landing error as the bench.

```bash
export LJ_CONTROL_API_KEY=... LJ_CONTROL_API_HOST=<phone ip>:<port>
make tap-smoke                    # all 20 locations, roughly 3 hours
make tap-smoke SERIAL=<adb serial> ONLY="1 2"   # a subset; a subset never meets the 19 of 20 bar
make tap-smoke-selfcheck          # pure verdict logic, no device
```

- **Throwaway account only.** The run teleports across the globe through the Control API (`POST /teleport`, so
  `TeleportUseCase` persists last location and cooldown). The script refuses to start without
  `--ack-throwaway-account`, which `make tap-smoke` passes: running it is the acknowledgement. Never use the main account.
- **Cooldown.** The app's cooldown is advisory. By default the run does not wait; pass `--respect-cooldown` to the script
  to sleep the advised time before each teleport. `--settle <s>` (default 45) is the wait for tiles after each teleport.
- **Per location.** Teleport, wait for the position and tiles, walk eight 40 m legs (bearings 0, 180, 90, 270, 45, 225, 135, 315) from the host, fit the
  perspective ground map, then score 12 taps 5 to 40 m away. Tap order is seeded, so a failure reproduces.
- **Pass bar.** A location passes with at least 11 of 12 scored taps within 15 m. Ambiguous matches are skipped, not
  counted. A rejected fit (`no_calibration`) or fewer than 12 scored taps (`insufficient_scored`) fails; so do
  more than one miss (`below_bar`). The suite passes with at least 19 of 20 locations. Exit code is 0 only on suite PASS.
- **Unverified taps are misses.** A tap whose match confidence is too low to confirm the landing (NCC below 0.4) counts as a
  miss, not as unscored. Its error is reported but never passes. Skipping it would let a weak verifier raise the pass rate,
  so the gate does not do that. Ambiguous taps (no match at all) are the only ones skipped, and they stay visible as a count.
- **Locations.** `scripts/tap_smoke_locations.json`, ordered `{name, lat, lon, kind}`, committed and never regenerated at
  run time. To add one, append an entry (keep names and coordinates unique) and update the bar constants in
  `scripts/tap_smoke.py` and `scripts/test_tap_smoke.py` if the count changes. Rural and polar entries may fail
  honestly: record the result, do not tune thresholds to pass.
- **Report.** `scripts/registration-bench/smoke/<UTC stamp>/report.md` (and `report.json`): one row per location with
  lat/lon, device, resolution, density, scored, passes, unverified and ambiguous counts, prior-anchor fallback count
  (the feet anchor fell back to the prior, a sign of failed registration), p50/p90 error in metres, verdict and reason, then
  `TAP SMOKE: <p>/<n> locations pass (bar 19/20) -> PASS|FAIL`. Per-location frames live beside it.
- **Frames and username.** The game HUD shows the player's username at the bottom of the screen. Frames and annotated
  images stay under the gitignored `scripts/registration-bench/`; never copy or commit one without masking that region.
  The report files contain numbers only.
- **Resolution-agnostic.** No size is passed; the bench reads the attached device's size and the report records it.
- **Gate.** A failing run blocks the Kotlin port entry and the "Prove tap-to-walk 100% reliable" entry
  (docs/features/tap-to-walk.md, "Ground map: current direction").

## Unit Tests (`:core:*`)

- Repo logic w/ fake DAO (in-memory Room)
- Route replay interpolation: waypoints A+B → assert position after N ticks
- RDP simplification: known path → assert simplified output
- Bearing: known lat/lon pairs → expected bearing
- `randomPointInRadius`: output always within radius
- Export/import: round-trip full `ExportData` through JSON
- OSM tile HTTP: required recipe docs/features/map-tiles.md (`MapTileUserAgentTest`)
- Room schema bump: a `LjDatabase` version change needs `LjDatabaseMigrationTest` updated to the new version (`LjDatabase.ALL_MIGRATIONS` feeds it)

Shared utils in `:core:testing`.

### Bench parity fixtures

`DeviceParityTest` (`:core:model`) checks the ground-map port against numbers exported from the bench, one fixture
directory per device under `core/model/src/test/resources/device_golden/` (Pixel 5 1080x2340 and Pixel 7 1080x2400
today). Each fixture records where its numbers came from. Scope limits are in [features/tap-to-walk.md](features/tap-to-walk.md).

Regenerate one device (bench venv, legs already captured; `--density` is the screen density in dpi from
`adb shell wm density`; `--camera` names the game camera state the legs were walked in):

```bash
scripts/registration-bench/.venv/bin/python scripts/export_device_golden.py pixel5_1080x2340 \
    scripts/registration-bench/data --model "Pixel 5" --density 440 --camera "wide zoom, no twist" \
    --captured-on 2026-10-13
```

`--captured-on` is the day the legs were captured; it is never derived from file timestamps.

Then run `./gradlew :core:model:test --tests '*DeviceParityTest*'`. Rerun the exporter when `clean_leg`,
`band_moves`, `detect_feet`, `_feat`, `fit_perspective*` or `tap_to_ground_persp` change in
`scripts/registration_bench.py`, or when the legs are recaptured. Commit the regenerated text files with the change
that caused them.

Tolerances are fixed in the test and must not be widened to pass. A failing parity test means the port drifted from the bench: fix the port, or record the finding with `/radin-record`.

The fixtures hold numbers only. Game frames are never committed (the HUD shows the player's name, and the bench
folder is git-ignored), so `FrameFlow` and `FeetDetector` on real frames are not covered here.

## Integration Tests (`:feature:*`)

- Hilt w/ `@HiltAndroidTest`
- Full route save → list → replay w/ in-memory Room
- Favorites: add → list → teleport → delete

## UI Tests (Compose)

- `ComposeTestRule` for screen-level tests
- Onboarding: mock permission states, assert screen transitions
- Route editor: add waypoints, assert polyline updates

## What NOT to Test

- MapLibre rendering (GPU, not unit-testable)
- `WindowManager` overlay (requires real device)
- `LocationManager.addTestProvider` (requires real device + Developer Options)
