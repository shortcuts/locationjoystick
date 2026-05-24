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
| `IdleSmokeTest` | Idle loads; drawer open/close; all 5 cards navigate; Map + Settings + Routes + Favorites + About via drawer |
| `MapSmokeTest` | Map loads; hamburger opens drawer |
| `FavoritesSmokeTest` | Favorites loads; seeded item visible; "Add options → from map" reaches `MapPickerScreen` |
| `RoutesSmokeTest` | Routes loads; seeded route visible |
| `RouteCreatorSmokeTest` | Creator loads via FAB; back returns to Routes |
| `RouteDetailSmokeTest` | Detail loads via overflow Edit; back returns to Routes |
| `SettingsSmokeTest` | Settings loads; speed unit toggle; export button |
| `AboutSmokeTest` | About loads; back returns to Idle |

Helpers in `SmokeTestHelpers.kt`: `skipOnboarding()`, `openDrawer()`, `navigateViaDrawer()`, `navigateFromIdle()`.

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