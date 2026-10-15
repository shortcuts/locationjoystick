# Testing

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

Covers every nav path in `LjNavHost`:

| File | What it asserts |
|------|----------------|
| `IdleSmokeTest` | Idle loads; drawer open/close; cards navigate; Map + Settings + Routes + Favorites + Capture via drawer |
| `MapSmokeTest` | Map loads (top-bar start/stop control visible); hamburger opens drawer; the 4 default FABs present (favorites, routes, roaming, search); paste coordinates FAB hidden by default |
| `FavoritesSmokeTest` | Favorites loads; seeded item visible; "Add favorite" FAB opens add-favorite sheet showing From map / From coordinates / Paste coordinates / Use current location; "From map" reaches MapPicker (checks search FAB) and back returns to Favorites; "From coordinates" opens the coordinates dialog; paste coordinates sheet shows Name + Coordinates; item menu shows Edit/Delete |
| `RoutesSmokeTest` | Routes loads ("Add route" FAB visible); FAB opens add-route sheet showing Draw on map / Draw on map (follow roads) / Paste coordinates / Import GPX file; seeded route visible (waitUntil async); start route dialog shows Loop/Planting/Reverse/Return/Follow roads/Teleport between waypoints (delay field)/Teleport/Start; route card overflow menu shows Edit/Export/Delete |
| `RouteCreatorSmokeTest` | Creator loads via "Add route" → "from map"; search/undo/favorites FABs visible; picking a seeded favorite shows "Add as stop", which dismisses the sheet; back returns to Routes |
| `PasteCoordinatesSmokeTest` | Paste coordinates loads via "Add route" → "Paste coordinates"; coordinate field and Planting mode visible; back returns to Routes |
| `RouteDetailSmokeTest` | Detail loads via overflow "Menu" → Edit (waitUntil route visible); back returns to Routes; delete button, name field, waypoint list visible |
| `SettingsSmokeTest` | Settings loads; speed unit toggle; "More actions" overflow menu opens without crash; all section headers visible; Roaming sub-screen shows planting defaults (starting radius) |
| `ControlApiDocsSmokeTest` | Leader exposes exactly the endpoints in `docs/wiki/group.html`: API off → 404, bad key → 401, error body shape; every documented path answers 405 with an `Allow` header equal to its documented methods (no handler runs). Never asserts endpoint behaviour. The build copies `group.html` into the test APK assets |

Helpers in `SmokeTestHelpers.kt`: `waitForIdleScreen()`, `openDrawer()`, `navigateViaDrawer()`, `navigateFromIdle()`.

## Unit Tests (`:core:*`)

- Repo logic w/ fake DAO (in-memory Room)
- Route replay interpolation: waypoints A+B → assert position after N ticks
- RDP simplification: known path → assert simplified output
- Bearing: known lat/lon pairs → expected bearing
- `randomPointInRadius`: output always within radius
- Export/import: round-trip full `ExportData` through JSON
- OSM tile HTTP: required recipe docs/features/map-tiles.md (`MapTileUserAgentTest`)

Shared utils in `:core:testing`.

### Bench parity fixtures

`DeviceParityTest` (`:core:model`) checks the ground-map port against numbers exported from the bench, one fixture
directory per device under `core/model/src/test/resources/device_golden/` (Pixel 5 1080x2340 and Pixel 7 1080x2400
today). Provenance, invariants and what is not covered: docs/features/tap-to-walk.md, "Parity fixtures".

Regenerate one device (bench venv, legs already captured; `--density` is the screen density in dpi from
`adb shell wm density`; `--camera` names the game camera state the legs were walked in):

```bash
scripts/registration-bench/.venv/bin/python scripts/export_device_golden.py pixel5_1080x2340 \
    scripts/registration-bench/data --model "Pixel 5" --density 440 --camera "wide zoom, no twist"
```

Then run `./gradlew :core:model:test --tests '*DeviceParityTest*'`. Rerun the exporter when `clean_leg`,
`band_moves`, `detect_feet`, `_feat`, `fit_perspective*` or `tap_to_ground_persp` change in
`scripts/registration_bench.py`, or when the legs are recaptured. Commit the regenerated text files with the change
that caused them.

Tolerances (fixed, do not widen to pass): heading 1.5 degrees; lateral and forward scale 3 percent; perspective
term 5 percent; median residual 0.3 px; flow count `2 * n`; tap offsets within the larger of 1.0 m and 3 percent
of the bench offset length. A failing parity test means the port drifted from the bench: fix the port, or record
the finding with `/radin-record`.

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
