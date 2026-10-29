# locationjoystick — Agent Reference

> Primary reference for AI coding agents. Read before touching any file.

---

## Project

Android-only mock GPS app. Background operation, minimal battery.

| Field | Value |
|---|---|
| Package | `com.locationjoystick.app` |
| Language | Kotlin |
| UI | Jetpack Compose |
| Min SDK | API 28 |
| Distribution | GitHub Releases APK + Play Store (AAB, live: https://play.google.com/store/apps/details?id=com.locationjoystick.app) |
| Storage | Room + DataStore |
| Backend | None |
| Open source | Yes |

Constraints:

- Offline-first
- No accounts
- All data on-device in Room + DataStore

---

## Documentation Maintenance Policy

**Docs give context; code holds the data.** Never copy what a symbol, string resource, constant or type already states (field lists, enum values, defaults, UI wording, numeric cadences, hex or dp values, layout). Point to the symbol or file instead. Keep the why: constraints, platform policy, tradeoffs, rejected approaches, and anything a reader cannot find in code.

Work is NOT complete until affected docs are updated where they state context. These files must stay accurate:

| File | Update when |
|------|-------------|
| `AGENTS.md` (this file) — Feature Specifications table | Adding or removing a feature |
| `AGENTS.md` — Key Services table | Adding, removing, or renaming a service or singleton |
| `docs/architecture.md` — module table | Adding or removing a Gradle module (also update `docs/architecture.md` for architecture pattern changes) |
| `docs/domain-models.md` | A model's rationale or a cross-module rule changes (a new field or enum value needs no edit) |
| `docs/design-system.md` | A design rule or a use-when judgement changes (a new token or component needs no edit) |
| `docs/features/<feature>.md` | The rationale or a constraint of the feature changes |
| `docs/features/export-import.md` | The import/export scope or its rationale changes |
| `README.md` — feature list | Adding or removing a user-visible feature |
| `docs/wiki/<feature>.html` | Adding or changing any user-visible feature |
| `app/src/androidTest/kotlin/com/locationjoystick/app/smoke/` (docs/testing.md) | Any app UI or flow change: a label, content description, screen, onboarding step or default the smoke tests tap or assert. Update the test in the same commit; `make smoke-test` must stay green. |
| `scripts/screenshot-gallery.sh` | Renaming, moving, or reordering any UI element it taps (labels, content descriptions, screen flow, defaults such as a FAB or setting that ships off). Run the affected step with `make screenshot` args `--auto --steps <N>` and confirm no `Could not find UI element` warning. |
| `docs/wiki/changelog.html` + `docs/wiki/changelog/<version>.json` (docs/features/whats-new.md) | Any release with user-visible changes — the JSON is now the sole authored source for both: it feeds the in-app What's New popup directly, and the HTML is generated from it via `make wiki-changelog` |

Rules:
- New feature → create `docs/features/<feature>.md` AND add row to AGENTS.md's Feature Specifications table AND README.md's feature list.
- New Gradle module → add row to `docs/architecture.md`'s module table (the single source of truth — README.md links to it, doesn't duplicate it).
- Deleted feature/module → remove from all tables above.
- Doc changes go in the same commit as the code change, not a follow-up.
- New user-visible feature → create `docs/wiki/<feature>.html` AND add it to `NAV_ITEMS` in `docs/wiki/wiki-init.js`. Follow `docs/wiki/CONTRIBUTING.md` for page structure, nav ordering, and writing style. Wiki pages are for **app users, not developers** — no code symbols, class names, Android internals, or library names.
- Wiki prose must pass the audience test in `docs/wiki/CONTRIBUTING.md`: could a non-technical user understand every sentence? If not, rewrite.
- **README.md and the wiki are user-facing, not developer docs.** Wiki is the source of truth for feature detail; README only covers what the app does, how to install it, and how to join the Discord — for everything else, link to the wiki or to AGENTS.md/CONTRIBUTING.md. Write both in plain English: short sentences, no code symbols, no class/module names, no build steps. Assume the reader already knows how to install an app. Developer-facing detail (build steps, architecture, tech stack) goes in CONTRIBUTING.md/AGENTS.md, never README.md.

---

## Pre-Commit Validation Policy

Work is NOT complete until lint and test passes.

```bash
make format
make lint
make test
```

To verify the AAB builds locally (manual Play Store upload — no automated CI deployment):

```bash
make bundle
```

Rules:
- Fix every lint error before declaring done. Warnings acceptable; errors not.
- Run after every set of edits, not just end of session.
- If check fails, fix root cause. Don't suppress unless genuine false positive + inline comment explaining why.
- Never suppress `Errors` category rules. Never batch-suppress with `@file:Suppress`.
- Never add co-authoring or "Claude-Sessions" to the commit
- If the disk is full, run rmgradle or clear the previous gradle builds/caches.

---

## Architecture

→ See docs/architecture.md

---

## Constants

→ See docs/constants.md

---

## Feature Specifications

→ See docs/features/

Working memory for long-running features: a feature doc that has a "Status" line (none is open today; Tap to Walk, in docs/features/tap-to-walk.md, closed on 2026-10-27) is the memory between sessions. Before work on it, read that section and its backlog epic (`radin backlog show`). Before a session ends, update the status, the measured numbers, the open work and any kill there, and update the epic's entries through `radin backlog`. Move superseded detail to the feature's investigation doc so the main doc stays short.

| Feature | Doc |
|---------|-----|
| Mock Location Engine + GPS Realism | docs/features/mock-location.md |
| Foreground Service | docs/features/foreground-service.md |
| Floating Joystick | docs/features/joystick.md |
| Map (MapLibre) | docs/features/map.md |
| Map tiles (OSM HTTP) | docs/features/map-tiles.md |
| Route System | docs/features/routes.md |
| Favorite Locations | docs/features/favorites.md |
| Speed Profiles | docs/features/speed-profiles.md |
| Floating Widget | docs/features/widget.md |
| Click-to-Move / Teleport | docs/features/click-to-move.md |
| Roaming Mode | docs/features/roaming.md |
| Export / Import | docs/features/export-import.md |
| QR Share / Transfer | docs/features/qr-transfer.md |
| Location Links (Automatic & Capture) | docs/features/location-links.md |
| Last Remembered Location | docs/features/last-location.md |
| Onboarding | docs/features/onboarding.md |
| Guided Paths (game setup) | docs/features/guided-paths.md |
| Group Sync | docs/features/group-sync.md |
| Tap to Walk | docs/features/tap-to-walk.md |
| Reconnect Accessory (Bluetooth reconnect tap) | docs/features/bluetooth-reconnect-tap.md |
| Theme | docs/features/theme.md |
| Hide Teleport Features | docs/features/hide-teleport.md |
| What's New Popup | docs/features/whats-new.md |
| Update Available Check | docs/features/update-check.md |
| Localization | docs/features/localization.md |

---

## Domain Models

→ See docs/domain-models.md

---

## Design System

→ See docs/design-system.md

---

## Key Services

| Service | Module | Type | Purpose |
|---------|--------|------|---------|
| `MockLocationService` | `:core:location` | ForegroundService | Owns the mock location provider and the foreground lifecycle. Single owner of the spoofing state exposed to the UI. |
| `JoystickOverlayService` | `:feature:joystick:impl` | Service | Overlay window for the joystick. Moving the stick pauses automated movement; the widget lock button owns locking. |
| `FloatingWidgetService` | `:feature:widget:impl` | Service | Overlay window for the widget. Binds to `MockLocationService`. |
| `RoamingEngine` | `:core:routing` | Class (not service) | Random roaming waypoints and road routing. Owned by `MockLocationService`. |
| `ReplayOrchestrator` | `:core:location` | Class (not service) | Owns route-replay and walk-to orchestration, extracted from the service. Reports back through callbacks rather than holding UI state. |
| `FollowerCatchUpCoordinator` | `:core:location` | Class (not service) | Owns follower catch-up state so the service does not hold it in loose fields. Mirrors `WalkCoordinator`. |
| `FollowerResumeCoordinator` | `:core:location` | Class (not service) | Owns the wait that returns a follower to Follow leader after a manual mode. Owned by `MapController`; the service still performs the re-entry. |
| `GroupModeController` | `:core:location` | Class (not service) | Owns leader advertising and the follower restoration and rediscovery jobs. Reports back through lambdas. |
| `TestProviderController` | `:core:location` | Class (not service) | Owns the GPS test provider registration. Reports a refused provider or revoked mock app through `onError`. |
| `SpoofingErrorHandler` | `:core:location` | Class (not service) | Moves the service to ERROR, releases the wake lock and stops walk, replay and roaming. |
| `ReplayStartLifecycle` | `:core:location` | Class (not service) | Owns route-start sessions and the lifecycle lock, so pause, resume and abort live in one place. |
| `AltitudeAnchorCoordinator` | `:core:location` | Class (not service) | Owns altitude convergence state. Same pattern as `FollowerCatchUpCoordinator`. |
| `PositionJitterCoordinator` | `:core:location` | Class (not service) | Owns the jitter offset. Its motion model is a constraint: it must not teleport between draws. See [features/mock-location.md](docs/features/mock-location.md). |
| `OverlayNotificationReactor` | `:core:location` | Class (not service) | Keeps the notification icon and widget overlay in sync with live settings mid-session. |
| `ContentApiRoutes` | `:core:location` | Class (`@Singleton`) | Content CRUD routes on the local sync server, installed beside `ControlApiRoutes`. |
| `EphemeralReplayController` | `:core:location` | Class (`@Singleton`) | Owns the walk-to-ephemeral-replay transition. Shared by the map and the widget so the state machine lives once. |
| `BackupRepository` | `:core:data` | Repository (`@Singleton`) | Applies an import in one transaction. Throws so the caller can report failure. |
| `WalkCoordinator` | `:core:data` | Class (`@Singleton`) | Facade over the walk engine. Cancels an in-flight walk before a new one starts. |
| `ActivityStateRepository` | `:core:data` | Repository (`@Singleton`) | Single source of truth for pause state across movement modes. Prefer it over combining the individual flows. |
| `TeleportUseCase` | `:core:data` | Class (`@Singleton`) | Single entry point for teleports, so every path shares persistence and cooldown. |
| `CaptureCoordinatesRepository` | `:core:data` | Repository (`@Singleton`) | Owns Capture mode state. Jumps go through `TeleportUseCase`. |
| `RealLocationRepository` | `:core:data` | Repository (`@Singleton`) | Reads a real GPS fix without touching the mock-position cache or cooldown. |
| `LaunchAfterLinkUseCase` | `:core:data` | Class (`@Singleton`) | Opens the user's chosen app after a handled map link. |
| `GpxOpenRepository` | `:core:data` | Repository (`@Singleton`) | Holds externally opened GPX files until the map consumes them. |
| `HotLocationsRepository` | `:core:data` | Repository (`@Singleton`) | Hot-location list from a cached wiki JSON with a bundled seed. Sibling of `UpdateCheckRepository`. |
| `HotRoutesRepository` | `:core:data` | Repository (`@Singleton`) | Same pattern as `HotLocationsRepository`, for hot routes. |
| `UpdateCheckRepository` | `:core:data` | Repository (`@Singleton`) | Latest-release check for the update badge. Cached; there is no toggle. |
| `MockLocationServiceStarter` | `:core:data` (interface), `:core:location` (impl) | Class (`@Singleton`) | Single start path for `MockLocationService`. Checks location permission first; see [features/foreground-service.md](docs/features/foreground-service.md). |
| `StartRouteReplayUseCase` | `:core:location` | Class (`@Singleton`) | Shared route-replay start for the map and the widget. `bypassHideTeleport` is Control API only. |
| `ControlApiRoutes` | `:core:location` | Class (`@Singleton`) | Control API routes. Each calls the same controller method as the UI; see [features/group-sync.md](docs/features/group-sync.md). |
| `GroundFlowFit` / `PhaseCorrelation` | `:core:model` | Objects | Pure-Kotlin tap to walk ground map: crop flow and the two-term heading and scale fit. See [features/tap-to-walk.md](docs/features/tap-to-walk.md). |
| `ReconnectControlDetector` | `:feature:widget:impl` | Object | Classifies the target app's reconnect control from screenshot pixels. See [features/bluetooth-reconnect-tap.md](docs/features/bluetooth-reconnect-tap.md). |
| `Geocoding` / `GeocodingProvider` | `:core:common` | Object + interface | Search and reverse lookup entry point. Falls back between providers with a shared cooldown. |
| `AutoFitCoordinator` | `:core:location` | Class (`@Singleton`) | Measures the tap to walk ground map from the walks the app already makes. Keeps crops in memory only. See [features/tap-to-walk.md](docs/features/tap-to-walk.md). |
| `ScreenCaptureSource` | `:core:location` | Class (`@Singleton`) | Bridges the screen accessibility service to consumers. Capture is gated on the in-app disclosure. Also carries the in-memory reconnect status for Settings. |
| `ScreenshotReader` | `:feature:widget:impl` | Class | Owns the paced screenshot, the software copy of the hardware bitmap and the recycling for the screen accessibility service. Callers never hold a bitmap. |
| `ReconnectNotifier` | `:feature:widget:impl` | Class | Maps each reconnect result to a post or cancel of the failure and not-found notices. Owns their streaks and channel. |

---

## Permissions

→ See docs/permissions.md

---

## Technical Constraints

→ See docs/technical-constraints.md

Before changing maps, read docs/features/map-tiles.md. Initialize MapLibre through `MapTileHttp.install`, construct maps through `createMapView`/`rememberMapView`, retain tile-client concurrency and R8 rules, and use `rememberMapView(overlay = true)` for overlay maps.

---

## Code Style Rules

→ See docs/code-style.md

---

## Testing Strategy

→ See docs/testing.md

```bash
make coverage        # generate HTML + XML reports
make coverage-open   # open HTML report in browser
```

Agents that need a device (smoke tests, screenshots, live checks) follow "Driving the app on a device" in docs/testing.md. Never ask the user for device state.

### Verification without the user (backlog tasks)

A backlog task (radin) never ends `blocked`, `inconclusive` or "needs a live check from the user". The agent verifies the task itself:

1. Run `adb devices`. Any attached device or emulator qualifies.
2. Device attached: the agent runs the check. Run `make smoke-test-one` for the affected screen, drive the flow over adb (docs/testing.md), and read `adb logcat` and `dumpsys` for the result. Unlock, grant, relaunch and recover without asking.
3. No device: the test harness is the proof. Write or extend the unit or integration test so it goes red, then green. Run `make format`, `make lint` and `make test`. Mark the limit in the commit body ("not run on a device"). Do not park the task.
4. A step the agent cannot do over adb (for example tapping Accept on the accessibility disclosure) is not a blocker. Test the layer below it, or state the exact limit in the task note and finish the rest.
5. Ask the user only for a product decision that code, docs and tests cannot settle. Never ask for a device action, a screenshot, a log paste or a "does it look right" confirmation.

---

## Website (GitHub Pages)

Static documentation site at `docs/wiki/`. Served via GitHub Pages; run locally with:

```bash
make wiki-serve   # http://localhost:8080
```

### Structure

| File | Purpose |
|------|---------|
| `docs/wiki/index.html` | Overview + install + first-run setup quick start |
| `docs/wiki/map.html` | Map screen + bottom sheets |
| `docs/wiki/routes.html` | Routes (lists, creator, detail, replay options, import) |
| `docs/wiki/favorites.html` | Favorites (lists, add methods, map picker, hot locations) |
| `docs/wiki/location-links.html` | Location links: sharing, opening, building a link by hand |
| `docs/wiki/capture.html` | Capture mode: setup, collecting links, saving a route |
| `docs/wiki/group.html` | Group Sync (leader/follower Wi-Fi sync) |
| `docs/wiki/tasks.html` | Common tasks hub; links goal-based how-to pages (`teleport-to-a-place`, `loop-a-route`, `follow-a-friend`, `move-to-a-new-phone`, `tap-to-walk-in-a-game`, `collect-links-into-a-route`) |
| `docs/wiki/game-setup.html` | Game setup: the three paths, what each hides, how to switch |
| `docs/wiki/settings.html` | Settings + language |
| `docs/wiki/backup-transfer.html` | Export/import + QR transfer |
| `docs/wiki/overlays.html` | Joystick + widget overlays + Tap to Walk |
| `docs/wiki/reconnect-accessory.html` | Reconnect accessory: the reconnect tap and its settings |
| `docs/wiki/troubleshooting.html` | Troubleshooting (permissions, mock location, map, crash report) |
| `docs/wiki/control-api.html` | Control API reference for developers |
| `docs/wiki/tap-to-walk.html`, `language.html`, `share.html`, `capture-coordinates.html` | Redirect stubs (canonical + noindex) |
| `docs/wiki/changelog.html` | Curated, user-facing release notes — **generated**, not hand-edited (see "Regenerating the changelog" below) |
| `docs/wiki/changelog/<version>.json` | Per-version changelog source — feeds both `changelog.html` (via `make wiki-changelog`) and the in-app What's New popup |
| `docs/wiki/privacy.html` | Privacy policy |
| `docs/wiki/acknowledgements.html` | Third-party credits |
| `docs/wiki/style.css` | Single stylesheet — all pages share it |
| `docs/wiki/screenshots/` | Phone screenshots (PNG, numbered 01–26, number = script step) |
| `docs/wiki/screenshots/marketing/` | 8 curated 1024×500 Play Store marketing images (white bg, catch-phrase + phone screenshot) — **generated**, not hand-edited |

Nav groups (Start / Guides / Features / Configure / Help / About), order and labels are the single source of truth in the grouped `NAV_ITEMS` (`docs/wiki/wiki-init.js`) — every HTML page's sidebar renders from that script, so a new page needs an entry there, not a hand-edited `<nav>` block per file.

### Regenerating screenshots

Screenshots are captured from a connected device/emulator via:

```bash
make screenshot   # outputs to docs/wiki/screenshots/
```

The script (`scripts/screenshot-gallery.sh`) navigates the app and captures the numbered screens (see the script's header comment for the current list — it is the source of truth, not this doc). Re-run after any UI change. Commit updated PNGs alongside the code change. `--marketing-only` regenerates the 8 `docs/wiki/screenshots/marketing/` images from existing screenshots without a device — the source screenshots and captions are listed in `generate_marketing_variants()` in the script. Overlay screens (joystick, widget) require manual activation — the script pauses and prompts at those steps.

### Regenerating the changelog

After adding or editing a version's JSON under `docs/wiki/changelog/`, run `make wiki-changelog`
to regenerate `docs/wiki/changelog.html`. Never hand-edit `changelog.html` — it's fully
overwritten on the next run. See docs/features/whats-new.md, "Maintaining the Changelog", for
the JSON schema and authoring rules.

### Maintaining content

- Each HTML page maps 1-to-1 to a feature. Update the page when the feature changes. `changelog.html` is the one page in this list that's generated rather than hand-edited — see "Regenerating the changelog" above.
- Screenshots are referenced by number. Renaming a file breaks the page — update both together.
- Sidebar nav is generated by `docs/wiki/wiki-init.js` (`NAV_ITEMS`), shared by every page. Add new pages there, not by hand-editing a `<nav>` block per file.
- No external resources for page content — no CDN fonts, no JS libraries for content rendering. (`wiki-init.js` itself loads the DocSearch CDN script and calls the GitHub API for the star count — an accepted exception for search/chrome, not precedent for page content.)

### Design changes

Invoke `/frontend-design:frontend-design` for any visual redesign or layout iteration. The skill owns aesthetic decisions; pass the desired direction and constraints as arguments. After the skill runs, verify with `make wiki-serve` and check all pages render correctly.
