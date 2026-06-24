# Testing

## Coverage

Coverage via [kotlinx-kover](https://github.com/Kotlin/kotlinx-kover) (v0.8.3). All modules use convention plugins. Root aggregates into merged report.

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
| `IdleSmokeTest` | Idle loads; drawer open/close; all 4 cards navigate; Map + Settings + Routes + Favorites via drawer |
| `MapSmokeTest` | Map loads (top-bar start/stop control visible); hamburger opens drawer; all 4 always-visible FABs present (favorites, routes, roaming, search) |
| `FavoritesSmokeTest` | Favorites loads; seeded item visible; "Add favorite" → from map reaches MapPicker (checks search FAB); back from MapPicker returns to Favorites; from coordinates sheet opens; item menu shows Edit/Delete |
| `RoutesSmokeTest` | Routes loads ("Add route" FAB visible); seeded route visible (waitUntil async); start route dialog shows Loop/Reverse/Return/Walk+Teleport; overflow menu shows Edit/Export/Delete |
| `RouteCreatorSmokeTest` | Creator loads via "Add route" → "from map"; search/undo/favorites FABs visible; back returns to Routes |
| `RouteDetailSmokeTest` | Detail loads via overflow "Menu" → Edit (waitUntil route visible); back returns to Routes; delete button, name field, waypoint list visible |
| `SettingsSmokeTest` | Settings loads; speed unit toggle; export/import icon buttons visible; export dropdown shows QR + file options; import dropdown shows QR + file + GPS Joystick + YAMLA; all section headers visible |

Helpers in `SmokeTestHelpers.kt`: `waitForIdleScreen()`, `openDrawer()`, `navigateViaDrawer()`, `navigateFromIdle()`.

## Unit Tests (`:core:*`)

- Repo logic w/ fake DAO (in-memory Room)
- Route replay interpolation: waypoints A+B → assert position after N ticks
- RDP simplification: known path → assert simplified output
- Bearing: known lat/lon pairs → expected bearing
- `randomPointInRadius`: output always within radius
- Export/import: round-trip full `ExportData` through JSON

Shared utils in `:core:testing`.

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