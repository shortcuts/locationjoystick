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
| Min SDK | API 31 |
| Distribution | GitHub Releases APK + Play Store (AAB) |
| Storage | Room + DataStore |
| Backend | None |
| Open source | Yes |

Constraints:

- Offline-first
- No accounts
- All data on-device in Room + DataStore

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
| About Page | @docs/features/about-page.md |
| Elevation Controls (Experimental) | @docs/features/elevation-controls.md |

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
| `docs/wiki/index.html` | Overview + card grid + quick start |
| `docs/wiki/home.html` | Home screen + background service |
| `docs/wiki/map.html` | Map screen + bottom sheets |
| `docs/wiki/routes.html` | Routes list + creator + detail |
| `docs/wiki/favorites.html` | Favorites list + map picker |
| `docs/wiki/share.html` | Share & deep link URL reference |
| `docs/wiki/settings.html` | Settings + QR transfer |
| `docs/wiki/overlays.html` | Joystick + widget overlays |
| `docs/wiki/onboarding.html` | First-run setup + troubleshooting |
| `docs/wiki/style.css` | Single stylesheet — all pages share it |
| `docs/wiki/screenshots/` | Phone screenshots (PNG, numbered 01–15) |

### Regenerating screenshots

Screenshots are captured from a connected device/emulator via:

```bash
make screenshot   # outputs to docs/wiki/screenshots/
```

The script (`scripts/screenshot-gallery.sh`) navigates the app and captures all 15 canonical screens. Re-run after any UI change. Commit updated PNGs alongside the code change.

Missing screenshots (14 `joystick_overlay`, 15 `widget_overlay`) require the overlay services running — capture manually if the script can't reach them.

### Maintaining content

- Each HTML page maps 1-to-1 to a feature. Update the page when the feature changes.
- Screenshots are referenced by number (`01_idle.png` … `15_widget_overlay.png`). Renaming a file breaks the page — update both together.
- All pages use the same sidebar nav snippet. When adding a new page, add its `<a>` entry to the `<nav>` block in **every** HTML file.
- No external resources — no CDN fonts, no JS libraries. Keep it that way.

### Design changes

Invoke `/frontend-design:frontend-design` for any visual redesign or layout iteration. The skill owns aesthetic decisions; pass the desired direction and constraints as arguments. After the skill runs, verify with `make wiki-serve` and check all pages render correctly.
