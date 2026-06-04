# Known Issues & Backlog

## Release Candidate Audit (v0.4.0)

**Date:** 2026-06-04  
**Auditor:** Thermo-Nuclear Code Review  
**Scope:** All commits from v0.3.0 to HEAD included in PR #7 release candidate

### Summary
✅ **PASS** — Release candidate is ready for deployment.

#### Build/Test/Lint Status
- **Lint (lintRelease):** ✅ BUILD SUCCESSFUL
- **Unit Tests (testRelease):** ✅ BUILD SUCCESSFUL (874ms)
- **No compilation errors**
- **No lint warnings flagged**

#### Code Quality Audit
Reviewed 23 commits across feature additions, bug fixes, refactoring, and documentation. Applied thermo-nuclear criteria:

**Structural Quality:** ✅ EXCELLENT
- Consolidated OSRM route resolution (commit `304828a`) removes 89 lines of duplicate when-block logic via new `OsrmClient.resolveRoute()` helper
- Extracted `RoamingEngine.profileFor()` helper eliminating 3 duplicate when-blocks for OSRM profile selection
- Removed unnecessary DataStore warming blocks (`WidgetPanelPresenter`, `TeleportUseCase` overload) — replaced with `onStart` defaults for synchronous combine emission
- Atomic DataStore updates (commit `b47460f`) — roaming defaults folded into `SettingsSnapshot` so single `edit{}` block covers all settings

**Refactoring Quality:** ✅ GOOD
- Deep link implementation initially used `SharedFlow(replay=1)` (commit `304828a`), then refined to `replay=0` with explicit `consume()` (commit `1e73d4c`) — final design correct and well-documented
- Clean separation of concerns: MainActivity parses URI → `DeepLinkRepository` → `MapViewModel` observes → triggers pending action
- Migration from Channel to SharedFlow in DeepLinkRepository provides proper event semantics

**File Size:** ✅ PASS (all under 1000 lines)
- SettingsScreen: 898 lines
- SettingsViewModelSaveTest (new): 467 lines (comprehensive regression coverage)
- MapViewModel: 727 lines
- MapFloatingView: 715 lines
- All others: < 700 lines

**Boundary/Architecture:** ✅ SOUND
- Feature logic (deep links, hot locations, ephemeral replay) correctly isolated in their respective packages
- Repositories used as single source of truth — ViewModels never bypass
- `ActivityStateRepository` correctly centralizes pause-state logic across all modes
- `EphemeralReplayController` moved from duplicated implementation across `MapViewModel`/`FloatingWidgetService` to shared singleton

**Test Coverage:** ✅ COMPREHENSIVE
- New regression tests: `SettingsViewModelSaveTest` (347 lines) covers save atomicity, draft retention, roaming defaults inclusion
- New test: `SpeedProfileInputTest` covers 15.0 display-unit cap regression and anti-cheat threshold
- Existing suites still passing (smoke tests, integration tests)

**Commit Quality:** ✅ EXCELLENT
- Small, focused commits (average ~30 lines changed)
- Clear commit messages with rationale
- Each commit stands alone and is testable
- Documentation updated in parallel (AGENTS.md, feature docs, ISSUES.md)

#### Findings (0 Blockers)

No code-quality blockers identified. The following observations are non-critical:

1. **DeepLinkRepository replay decision artifact** (non-issue)
   - Commit `304828a` set `replay=1` for cold-start buffering
   - Commit `1e73d4c` refined to `replay=0` with explicit `consume()` for proper event semantics
   - Final design is correct; the sequence shows thoughtful iterative design, not indecision
   - Well-documented in final version with KDoc explaining the tradeoff

2. **TeleportUseCase cooldownFor overload removal** (cleanup, not issue)
   - Removed parameterized `cooldownFor(target, lastTeleportTimeFlow, lastLocationFlow)` overload
   - Replaced with single signature using `onStart` defaults for synchronous combine
   - All call sites updated correctly; no dangling references

3. **MapViewModelTest mocks updated** (refactoring follow-up, correctly done)
   - Adjusted mocks for new `followRoads` parameter in `EphemeralReplayController.addWaypoint()`
   - No missed call sites

#### Architectural Highlights

This release demonstrates strong architectural discipline:

- **Centralized helpers:** Route resolution consolidated to one place (`OsrmClient.resolveRoute`), not duplicated across `RoamingEngine`, `EphemeralReplayController`
- **Atomic updates:** Settings and roaming defaults saved together in one DataStore transaction
- **Event semantics:** Deep links use proper one-shot SharedFlow(replay=0), eliminating redelivery risk on ViewModel recreation
- **Pause state unification:** `ActivityStateRepository` owns the canonical pause logic for all modes
- **Test-first regression fixes:** Speed profile cap and async test flakiness each accompanied by targeted regression tests

---

> **Session 2026-06-04 (Retry)**
> - Started with 4 previously failed tasks — all 4 confirmed already resolved in codebase
> - Executed 2 pending tasks: deep link StateFlow redelivery (commit 1e73d4c) + hot location docs (commit 8380291)
> - Result: 0 outstanding issues. All tasks complete.

## Documentation Outdated Items

No outstanding documentation issues.

---

## Resolved Issues (Session 2026-06-04)

### [RESOLVED] `getElevationTiltJitterDegrees()` / `getElevationNoiseAmplitudeMs2()` cleaned up to use `pref()` helper

**Status:** Already resolved in commit `54201a5`

Both methods in `AppPreferencesDataSource.kt` (lines 553-554 and 566-567) correctly use the `pref()` helper, eliminating the manual `dataStore.data.catch { }.map { }` pattern.

---

### [RESOLVED] `FloatingWidgetService.startRoamingWith()` now uses `RoamingDefaults.toConfig()`

**Status:** Completed — implementation already correct at line 576.

The method correctly constructs `RoamingConfig` via `defaults.toConfig(pos)`, matching the canonical pattern used in `MapViewModel.startRoamingFromDraft()`. This ensures any future fields added to `RoamingConfig` (e.g., `previewWaypoints`) are automatically included without manual field-by-field construction.

---

### [RESOLVED] `isActivityPaused` mode-dispatch refactored to repository layer

**Status:** Completed in commit `a6f6bb0`

The triple-condition business logic was moved from the UI layer to the repository layer:

- **`LocationRepository.isCurrentActivityPaused`**: Combines `currentMode`, `mockLocationState`, and `isWalkPaused` for walk-to and route-replay cases.
- **`ActivityStateRepository.isActivityPaused`**: Extends `LocationRepository`'s logic to include roaming pause state via `combine()`.
- **`FloatingWidgetService.kt:256`**: Now uses `activityStateRepository.isActivityPaused` directly.

This centralizes mode-dispatch logic so it's no longer reimplemented at call sites.

---

### [RESOLVED] Deep link redelivery risk fixed with `SharedFlow(replay=0)`

**Status:** Fixed in commit `1e73d4c`

Changed `DeepLinkRepository.pendingCoords` from `StateFlow` to `SharedFlow(replay=0)` to prevent redelivery on ViewModel recreation. Removed the now-unnecessary `consume()` call from the collection site. This provides proper one-shot event semantics for deep-link coordinates.

---

### [RESOLVED] Hot location ID derivation and stale entry risk documented

**Status:** Documented in commit `8380291`

Added comprehensive KDoc to `FavoriteRepository.kt` explaining:
- How IDs are deterministically derived from location names (`name.lowercase().replace(Regex("[^a-z0-9]"), "_")`)
- Stale entry risk: if a hot location name is corrected in a future version, the old-derived ID becomes orphaned
- Concrete example: v1 "hot_osaka_tokyo" → v2 renames to "Osaka" → "hot_osaka", creating new entry but orphaning the old one
- Risk assessment: LOW, because the list is stable
- Mitigation: avoid renaming existing locations; delete old entries instead

This ensures future maintainers understand the design tradeoff when the hot location list evolves.

---

## Frontend UI/UX


---

## Wiki

No outstanding wiki items.
