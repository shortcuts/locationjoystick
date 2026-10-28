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

## Driving the app on a device (agents)

Use any attached `adb devices` entry. Never ask the user for device state: every step below runs over adb. `scripts/screenshot-gallery.sh` and the smoke `BaseSmokeTest.setup` encode the same flow; the code wins if this section disagrees.

1. **Unlock.** `adb shell input keyevent KEYCODE_WAKEUP`, then a full-height swipe up, for example `adb shell input swipe 540 1900 540 400 300` on a 1080x2340 screen. Check `adb shell dumpsys window | grep isKeyguardShowing` reads `false`. `wm dismiss-keyguard` and short swipes fail on a PIN-less lock screen.
2. **Run a smoke test.** `make smoke-test-one TEST_FILE=app/src/androidTest/kotlin/com/locationjoystick/app/smoke/<Name>.kt`. No other device state is needed. `HiltTestRunner` clears the app's DataStore and grants location, overlay, mock location and notifications over adb, then `BaseSmokeTest.setup` taps through onboarding once per run. An unlocked, awake screen is the only precondition. In a git worktree, copy `local.properties` from the main checkout first, or Gradle reports "SDK location not found".
3. **Launch by hand.** `make start-on-phone`, or `adb shell am start -n com.locationjoystick.app/com.locationjoystick.app.MainActivity`. Confirm with `adb shell dumpsys window | grep mCurrentFocus`.
4. **Read the screen.** `adb shell uiautomator dump /sdcard/u.xml && adb pull /sdcard/u.xml`, then search `text=` and `content-desc=`. Tap the centre of a node's `bounds`. The `ui_dump`, `tap_text` and `tap_text_exact` helpers in the screenshot script do this.
5. **Pass onboarding.** A screen with "Set up locationjoystick" is onboarding. Grant over adb:
   ```bash
   adb shell pm grant com.locationjoystick.app android.permission.ACCESS_FINE_LOCATION
   adb shell pm grant com.locationjoystick.app android.permission.ACCESS_COARSE_LOCATION
   adb shell pm grant com.locationjoystick.app android.permission.POST_NOTIFICATIONS
   adb shell appops set com.locationjoystick.app SYSTEM_ALERT_WINDOW allow
   adb shell appops set com.locationjoystick.app android:mock_location allow
   ```
   Then `am force-stop` and relaunch. Tap "Decline" if the accessibility disclosure shows, "Continue" on screen 1, then "Start using locationjoystick" on the path picker. `./scripts/screenshot-gallery.sh --auto` does this on a fresh install; a full reset is `make reinstall-on-phone`.
6. **Reach a screen.** The app opens on the Home hub (cards such as "Settings", "Routes", "Favorites"). Other screens open from a Home card or from the drawer: tap the "Open navigation menu" content description, then the label. Match drawer labels inside the `nav_drawer` test tag, because the cards reuse the same text.
7. **Recover from a wrong screen.** Back keeps the Compose nav stack, so it can land anywhere. The reliable reset is `adb shell am force-stop com.locationjoystick.app`, then relaunch (step 3). A session that was running at kill time restores onto the map: tap "Stop", then drawer, then "Home". Home is confirmed by the "Replay saved routes" text. `go_idle` in the screenshot script is this recovery.
8. **App not in the foreground.** The launch command above brings it forward. If `mCurrentFocus` shows another window (lock screen, a permission dialog, the system settings), unlock first (step 1), dismiss the dialog with Back, and relaunch.
9. **App sits on onboarding after a restart.** Onboarding stays until the path picker finishes, even with every permission granted. Repeat step 5.
10. **A force-stop clears the Tap to Walk accessibility grant.** See "Test phone setup" above.

Check: with an unlocked device, `make smoke-test-one TEST_FILE=app/src/androidTest/kotlin/com/locationjoystick/app/smoke/IdleSmokeTest.kt` ends `BUILD SUCCESSFUL`.

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
- **Minutes, not hours.** The account is a throwaway, so the run never waits for walking pace or cooldowns. Every walk
  uses the Run profile, the fastest one under the app's flow speed limit; slower walks only cost device time: one Walk row (`make tap-smoke ONLY=1 SPEED_PROFILE=walk`) took 703 s and passed (p90 8.1 m). Each tap
  teleports back to its start. One Run row takes about 5 minutes. Never start a run that takes hours: split it into rows.
- **Truth is the reference ruler.** The bench's eight-leg perspective fit is a rich fit (hundreds of flows) and only
  scores the tap; the app under test measures its own map from one 40 m leg. The ruler fits only the band flows at or
  below row 1200 of a 2340 px frame, because one perspective term over the whole band misread the scale at the feet,
  where every tap lands. A fit with a positive perspective term, or whose lateral scale differs by more than 25% from a
  fit on every other leg, is rejected. Each scored tap keeps the app target and the ruler truth, so a later ruler can
  rescore a run offline. The script reads the target from the
  tap-time position and compares it with the ruler's ground point for the tapped pixel. A first version walked to
  arrival and matched the tapped patch against the frame after the walk; 8 to 11 of 12 taps came back ambiguous
  (repeating street content, the sprite and the animated rings in the patch), so it could not score a row.
- **Per location.** Teleport, wait for tiles, measure the reference ruler (eight short legs with a frame every second, then the perspective fit), walk
  one 40 m leg north so the app stores its own map, then tap 12 scored points 5 to 40 m away. Each tap reopens the overlay,
  because it closes after every tap. A tap is rejected when the app starts no walk within 3 s. In the fresh and twist scenarios the
  script waits 12 s so the re-aim can land and reads the target again; elsewhere the first target is final. It then stops
  the walk and teleports back.
- **Pass bar.** A row passes when the 90th percentile error is at most 15 m, at most 20% of taps are rejected, and 12 taps
  score. The suite passes with at least 19 of 20 normal rows.
  Every extra scenario row must also pass. A failing extra row is recorded and is not retried.
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
- **Frames and username.** The game HUD shows the player's username at the bottom of the screen. Frames and annotated
  images stay under the gitignored `scripts/registration-bench/`; never copy or commit one without masking that region.
  The report files contain numbers only.
- **Resolution-agnostic.** No size is passed; the bench reads the attached device's size and the report records it.
- **Gate.** The lead accepted the gate on 2026-10-27 (docs/features/tap-to-walk.md, "Ground map: flow-measured"). After a
  change to the ground map or the overlay tap path, rerun a 4-row batch and compare it with the numbers there.

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
