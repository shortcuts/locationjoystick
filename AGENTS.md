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
| Distribution | GitHub Releases APK + Play Store (AAB) |
| Storage | Room + DataStore |
| Backend | None |
| Open source | Yes |

Constraints:

- Offline-first
- No accounts
- All data on-device in Room + DataStore

---

## Documentation Maintenance Policy

Work is NOT complete until affected docs are updated. These files must stay in sync with the code:

| File | Update when |
|------|-------------|
| `AGENTS.md` (this file) — Feature Specifications table | Adding or removing a feature |
| `AGENTS.md` — Key Services table | Adding, removing, or renaming a service or singleton |
| `docs/architecture.md` — module table | Adding or removing a Gradle module (also update `docs/architecture.md` for architecture pattern changes) |
| `docs/domain-models.md` | Any change to `core/model/` data classes or enums |
| `docs/features/<feature>.md` | Behaviour change in the corresponding feature |
| `docs/features/export-import.md` | Any change to `ExportData` fields or import/export scope |
| `README.md` — feature table | Adding or removing a user-visible feature |
| `docs/wiki/<feature>.html` | Adding or changing any user-visible feature |
| `docs/wiki/changelog.html` | Any release with user-visible changes |

Rules:
- New feature → create `docs/features/<feature>.md` AND add row to AGENTS.md's Feature Specifications table AND README.md's feature table.
- New Gradle module → add row to `docs/architecture.md`'s module table (the single source of truth — README.md links to it, doesn't duplicate it).
- New domain model or field → update `docs/domain-models.md`.
- Deleted feature/module → remove from all tables above.
- Doc changes go in the same commit as the code change, not a follow-up.
- New user-visible feature → create `docs/wiki/<feature>.html` AND add it to `NAV_ITEMS` in `docs/wiki/wiki-init.js`. Follow `docs/wiki/CONTRIBUTING.md` for page structure, nav ordering, and writing style. Wiki pages are for **app users, not developers** — no code symbols, class names, Android internals, or library names.
- Wiki prose must pass the audience test in `docs/wiki/CONTRIBUTING.md`: could a non-technical user understand every sentence? If not, rewrite.

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

---

## Architecture

→ See @docs/architecture.md

---

## Constants

→ See @docs/constants.md

---

## Feature Specifications

→ See @docs/features/

| Feature | Doc |
|---------|-----|
| Mock Location Engine + GPS Realism | @docs/features/mock-location.md |
| Foreground Service | @docs/features/foreground-service.md |
| Floating Joystick | @docs/features/joystick.md |
| Map (MapLibre) | @docs/features/map.md |
| Route System | @docs/features/routes.md |
| Favorite Locations | @docs/features/favorites.md |
| Speed Profiles | @docs/features/speed-profiles.md |
| Floating Widget | @docs/features/widget.md |
| Click-to-Move / Teleport | @docs/features/click-to-move.md |
| Roaming Mode | @docs/features/roaming.md |
| Export / Import | @docs/features/export-import.md |
| QR Share / Transfer | @docs/features/qr-transfer.md |
| Deep Links & Location Sharing | @docs/features/deep-link.md |
| Last Remembered Location | @docs/features/last-location.md |
| Onboarding | @docs/features/onboarding.md |
| Group Sync | @docs/features/group-sync.md |
| Tap to Walk | @docs/features/tap-to-walk.md |
| Theme | @docs/features/theme.md |
| Hide Teleport Features | @docs/features/hide-teleport.md |

---

## Domain Models

→ See @docs/domain-models.md

---

## Key Services

| Service | Module | Type | Purpose |
|---------|--------|------|---------|
| `MockLocationService` | `:core:location` | ForegroundService | Owns `LocationManager` test provider. Exposes `StateFlow<SpoofState>`. Commands: `startSpoofing`, `updatePosition`, `stopSpoofing`. Suspended-phase state held in `AtomicReference<SuspendedPhaseState>`; transitions via `advanceSuspendedPhase()` pure function (testable independently). |
| `JoystickOverlayService` | `:feature:joystick:impl` | Service | Extends `OverlayService`. Manages `WindowManager` overlay. Reads joystick input → `LocationRepository.updatePosition()`. |
| `FloatingWidgetService` | `:feature:widget:impl` | Service | Manages widget overlay. Binds to `MockLocationService`. |
| `RoamingEngine` | `:core:routing` | Class (not service) | Instantiated by `MockLocationService`. Owns OSRM client + random waypoint picker. Runs on service scope. |
| `ReplayOrchestrator` | `:core:location` | Class (not service) | Instantiated by `MockLocationService`. Owns all route-replay and walk-to orchestration (`handleStart`/`handlePause`/`handleResume`/`handleStop`/`handleCancel`) extracted from the service. Communicates back via lambdas (`onStateChange`, `onPositionChange`, `pushLocationUpdate`, etc.) instead of holding its own state directly. |
| `FollowerCatchUpCoordinator` | `:core:location` | Class (not service) | Instantiated by `MockLocationService`. Owns the FOLLOWER-mode catch-up target (`AtomicReference<LatLng?>`), the per-tick step logic (`advance()`), and the leader-active bootstrap/pause state machine (`handleLeaderActiveUpdate()`) — all extracted from the service, mirroring the `WalkCoordinator` pattern: state ownership + step logic live in one small class instead of scattered `@Volatile` fields and call-site-local flags on the service. |
| `AltitudeAnchorCoordinator` | `:core:location` | Class (not service) | Instantiated by `MockLocationService`. Owns the altitude-anchor convergence state (`currentBaseAltitudeMeters`/`targetBaseAltitudeMeters`, the elevation-fetch-in-flight guard) and the per-tick `stepConverge()` call — mirrors `FollowerCatchUpCoordinator`: state ownership + step logic live in one small class instead of scattered `@Volatile` fields on the service. |
| `OverlayNotificationReactor` | `:core:location` | Class (not service) | Instantiated by `MockLocationService`. Owns the two settings-reactive collectors that keep the foreground notification (@docs/features/foreground-service.md, "Hiding the Notification Icon") and the widget overlay (@docs/features/widget.md, "Hiding the Overlay") in sync with live setting changes mid-session — previously inline in the service. |
| `EphemeralReplayController` | `:core:location` | Class (`@Singleton`) | Owns the walk→ephemeral-replay transition. Injected by both `MapViewModel` and `FloatingWidgetService`. `addWaypoint()` decides whether to start a new ephemeral replay (walk→replay transition) or append to an existing one. Eliminates duplicated state-machine logic across call sites. |
| `WalkCoordinator` | `:core:data` | Class (`@Singleton`) | Thin facade over `WalkToEngine`. Cancels any in-flight walk before starting a new one, forwards position ticks to `LocationRepository`, clears `walkTarget` on arrival or cancellation. |
| `ActivityStateRepository` | `:core:data` | Repository (`@Singleton`) | Single source of truth for unified pause state across all movement modes. Exposes `isActivityPaused: Flow<Boolean>` combining walk-to, route replay, and roaming pause. Prefer over manually combining individual flows from `LocationRepository` and `RoamingRepository`. |
| `TeleportUseCase` | `:core:data` | Class (`@Singleton`) | Single entry point for all teleport operations — fires the update-position intent to `MockLocationService`, persists last location + last teleport time. Injected by both `MapViewModel` and `FavoritesViewModel` so every teleport path shares the same persistence and cooldown logic (`cooldownFor`/`cooldownsFor`). |
| `StartRouteReplayUseCase` | `:core:location` | Class (`@Singleton`) | Starts a route replay: resolves the route's speed profile, optionally teleports to the start waypoint first (via `TeleportUseCase`), then sends the start-replay intent to `MockLocationService`. Dedupes route-replay-start logic previously duplicated in `MapViewModel` and `FloatingWidgetService`. |

---

## Permissions

→ See @docs/permissions.md

---

## Technical Constraints

→ See @docs/technical-constraints.md

---

## Code Style Rules

→ See @docs/code-style.md

---

## Testing Strategy

→ See @docs/testing.md

```bash
make coverage        # generate HTML + XML reports
make coverage-open   # open HTML report in browser
```

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
| `docs/wiki/routes.html` | Routes list + creator + detail |
| `docs/wiki/favorites.html` | Favorites list + map picker |
| `docs/wiki/share.html` | Share & deep link URL reference |
| `docs/wiki/group.html` | Group Sync (leader/follower Wi-Fi sync) |
| `docs/wiki/tap-to-walk.html` | Tap to Walk (quick-walk + screen overlay) |
| `docs/wiki/settings.html` | Settings + QR transfer |
| `docs/wiki/overlays.html` | Joystick + widget overlays |
| `docs/wiki/troubleshooting.html` | First-run setup + troubleshooting |
| `docs/wiki/changelog.html` | Curated, user-facing release notes |
| `docs/wiki/privacy.html` | Privacy policy |
| `docs/wiki/acknowledgements.html` | Third-party credits |
| `docs/wiki/style.css` | Single stylesheet — all pages share it |
| `docs/wiki/screenshots/` | Phone screenshots (PNG, numbered 01–17, plus `*_playstore` crops) |

Nav order/labels are the single source of truth in `NAV_ITEMS` (`docs/wiki/wiki-init.js`) — every HTML page's sidebar renders from that script, so a new page needs an entry there, not a hand-edited `<nav>` block per file.

### Regenerating screenshots

Screenshots are captured from a connected device/emulator via:

```bash
make screenshot   # outputs to docs/wiki/screenshots/
```

The script (`scripts/screenshot-gallery.sh`) navigates the app and captures 17 canonical screens (see the script's header comment for the current numbered list — it is the source of truth, not this doc). Re-run after any UI change. Commit updated PNGs alongside the code change. `--playstore-only` regenerates the `*_playstore` crops from existing screenshots without a device. Overlay screens (joystick, widget) require manual activation — the script pauses and prompts at those steps.

### Maintaining content

- Each HTML page maps 1-to-1 to a feature. Update the page when the feature changes.
- Screenshots are referenced by number. Renaming a file breaks the page — update both together.
- Sidebar nav is generated by `docs/wiki/wiki-init.js` (`NAV_ITEMS`), shared by every page. Add new pages there, not by hand-editing a `<nav>` block per file.
- No external resources for page content — no CDN fonts, no JS libraries for content rendering. (`wiki-init.js` itself loads the DocSearch CDN script and calls the GitHub API for the star count — an accepted exception for search/chrome, not precedent for page content.)

### Design changes

Invoke `/frontend-design:frontend-design` for any visual redesign or layout iteration. The skill owns aesthetic decisions; pass the desired direction and constraints as arguments. After the skill runs, verify with `make wiki-serve` and check all pages render correctly.
