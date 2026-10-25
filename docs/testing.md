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

On-device gate for tap-to-walk landing with the flow-measured ground map. It drives the real app: each tap goes
through the tap-to-walk overlay with `adb input tap`, and the script reads where the app walks from the Control API.

```bash
export LJ_CONTROL_API_KEY=... LJ_CONTROL_API_HOST=<phone ip>:<port>
make tap-smoke                                  # 20 locations, normal scenario
make tap-smoke SERIAL=<adb serial> ONLY="1"     # one row
make tap-smoke SCENARIOS=all                    # adds fresh, twist and zoom at locations 1 and 7
make tap-smoke-selfcheck                        # pure verdict logic, no device
```

- **Throwaway account only.** The run teleports across the globe through the Control API (`POST /teleport`, so
  `TeleportUseCase` persists last location and cooldown). The script refuses to start without
  `--ack-throwaway-account`, which `make tap-smoke` passes: running it is the acknowledgement. Never use the main account.
- **Truth is the walk, not the reference camera.** After the app walks to its own target, the script measures where the
  tapped pixel ended up relative to the feet. That residual is small, so a noisy reference ruler scales only a few
  metres. Scoring the target against the reference camera would put the whole tap distance through the noisy fit.
- **Per location.** Teleport, wait for tiles, measure the reference ruler (eight 40 m legs, then the perspective fit), walk
  one 40 m leg north so the app stores its own map, then tap 12 scored points 5 to 40 m away. Each tap reopens the overlay,
  because it closes after every tap. A tap is rejected when the app starts no walk within 3 s.
- **Pass bar.** A row passes when the 90th percentile error is at most 15 m, at most 20% of taps are rejected, and 12 taps
  score. Ambiguous taps (no pixel match) are skipped and counted. The suite passes with at least 19 of 20 normal rows.
  Every extra scenario row must also pass. A failing extra row is recorded and is not retried.
- **Unverified taps are misses.** A tap whose match confidence is too low to confirm the landing (NCC below 0.4) counts as a
  miss, not as unscored. Its error is reported but never passes. Skipping it would let a weak verifier raise the pass rate,
  so the gate does not do that.
- **Scenarios.** `fresh` forgets the stored map after the reference legs and taps with no app leg, so the provisional map and
  the re-aim path run. `twist` rotates the camera 45 degrees after the app leg. `zoom` pinches out to 1.6x before the reference.
  Both gestures are two-finger input sent with `sendevent`, because `adb shell input` has no multi-touch.
- **Locations.** `scripts/tap_smoke_locations.json`, ordered `{name, lat, lon, kind}`, committed and never regenerated at
  run time. To add one, append an entry (keep names and coordinates unique) and update the bar constants in
  `scripts/tap_smoke.py` and `scripts/test_tap_smoke.py` if the count changes. Rural and polar entries may fail
  honestly: record the result, do not tune thresholds to pass.
- **Report.** `scripts/registration-bench/smoke/<UTC stamp>/report.md` (and `report.json`): one row per run, with
  location, device, resolution, density, scenario, scored, rejected, ambiguous, unverified and prior-anchor fallback counts,
  p50 and p90 error in metres, the median re-aim distance, verdict and reason, then
  `TAP SMOKE: <p>/<n> locations pass (bar 19/20) -> PASS|FAIL`. Per-location frames live beside it.
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
- **Gate.** A failing run blocks the tap-to-walk-flow-fit epic from closing
  (docs/features/tap-to-walk.md, "Ground map: flow-measured").

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

### Tap to Walk ground map

`GroundFlowFitTest` (`:core:model`) and `AutoFitCoordinatorTest` (`:core:location`) render value-noise ground through a known map and check that a straight walk recovers its heading and scale. The old bench parity fixtures were deleted with the motion fit.

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
