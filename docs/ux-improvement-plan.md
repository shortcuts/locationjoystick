# UI/UX Improvement Plan

Audit date: 2026-06-23. Goal: clarity, consistency, reliability, minimal-yet-understandable design. Tracks as a living checklist — check items off as fixed, add new findings inline.

## Baseline (already good — don't touch)

- Dark-only Material3 theme, centralized tokens (`LjColors.kt`, `LjTypography.kt`)
- `LjShapes.kt` already defines a real scale: extraSmall 4 / small 8 / medium 16 / large 24 / extraLarge 32 — **the shape token system exists, it's just inconsistently applied** (see Phase 2)
- `UiConstants.kt` defines FAB sizing tokens
- Shared `LjScaffold`, `LjTopBar`, `LjButton`, `LjCard`, `EmptyState`, `LoadingIndicator` components already exist in `:core:designsystem`
- `LjButton` enforces 48dp min height

## Phase 1 — Accessibility sweep (CRITICAL, cheap) — DONE 2026-06-23

Most `contentDescription = null` instances checked are actually fine (icon sits next to a visible text label — e.g. `RoutesScreen.kt:329/337/345` dropdown items, `WidgetPanelContent.kt:412/471/565` labeled buttons). Real gaps to fix:

- [x] `GroupSyncScreen.kt:205,243,384` — verified: all three sit next to visible text (hero icon above heading, "Scan QR" button label, "Scan to join" caption). No change needed.
- [x] `SettingsScreen.kt:666` — verified: icon sits in a Card row next to title+description text. No change needed.
- [x] `OnboardingScreen.kt:287` — verified: icon sits next to title text in the same row. No change needed.
- [x] Idle screen card icons (`IdleScreen.kt:231`) — verified: same pattern, icon next to title+description text. No change needed.
- [x] Map screen FABs (`MapFabColumn.kt`) — verified: every `LjMapIconButton` already has a real, state-aware `contentDescription` (e.g. "Stop walk", "Resume roaming"). Already consistent.
- [x] Favorites menu icons (`FavoritesScreen.kt:148,151,311`) — verified: "Sort", "Add favorite", "More options" all have real descriptions already.

Rule going forward: `contentDescription = null` is only correct when a visible text label sits next to the icon. Standalone icon buttons (FABs, icon-only toolbar actions) always need a real description.

## Phase 2 — Apply existing token system consistently (MEDIUM)

The shape scale already exists in `LjShapes.kt` — problem is screens use raw `RoundedCornerShape(Xdp)` literals instead of `MaterialTheme.shapes.small/medium/...`.

- [ ] Grep all screens for `RoundedCornerShape(` literals outside `LjShapes.kt`/`LjCard.kt`; replace with theme shape tokens where the corner radius matches an existing tier (8/16/24/32)
- [ ] For the 50dp "pill" outlier found in audit — decide: is it a legitimate one-off (e.g. a chip/pill control) or should `LjShapes` gain an explicit `pill`/`full` tier? Don't silently absorb it into `extraLarge`.
- [ ] Spacing: no `LjSpacing` token object exists yet. Add one to `:core:designsystem` (e.g. `xs=4dp, sm=8dp, md=16dp, lg=24dp, xl=32dp`) mirroring the `LjShapes` pattern, then sweep raw `.dp` padding/spacing literals in screens to match the nearest tier. Don't invent new in-between values (12dp/20dp) going forward.

## Phase 3 — Unify state handling (HIGH — ties directly to "reliability" goal) — DONE 2026-06-23

- [x] Map screen loading affordance: `MapViewModel.generateRoamingPreview()` (an OSRM network call that can take seconds) gave zero feedback while in flight. Added `MapUiState.isRoamingPreviewLoading`, set around the call in a `try/finally`, threaded through `RoamingSheet` → `RoamingSheetContent` to show an inline `CircularProgressIndicator` in the "Generate" button and disable both Generate/Start while loading. Mirrored the same fix in the floating-widget's `OverlayRoamingSheet` (`MapFloatingView.kt`), which has its own independent OSRM-calling code path. Map's "search loading" has no async step to show a spinner for (Nominatim search bar already has its own internal debounce/loading — out of scope here) — error feedback for routing failures was already wired via `routingErrors`/`completionMessages` snackbars in `MapRoute`.
- [x] Added `SnackbarHost`/error surface to Routes screen: `RoutesViewModel` gained `errorMessage: StateFlow<String?>` + `clearError()`, set on GPX export failure (previously only `Log.e`, silently dropped on the user). `RoutesScreen`/`RoutesRoute` now follow the same `snackbarHostState` + `LaunchedEffect(errorMessage)` pattern already used by Group Sync/Favorites/Map. Also swapped the raw `CircularProgressIndicator` for the existing `LoadingIndicator` component to match the rest of the app.
- [x] Onboarding error handling: re-examined — there's no exception/failure path here, only "permission granted vs not". `OnboardingStepCard` already shows a warning-colored card with an inline fix action per ungranted step (matches `docs/features/onboarding.md`'s "Show a banner if a required permission is missing"). A snackbar on top would be redundant (it'd re-fire every `onResume` until the user acts). No change needed.
- [x] Canonical state-handling pattern (write-up below).

### Canonical state-handling pattern

- **Loading**: expose a `Boolean` in the screen's `UiState`, flip it around the suspending call in `viewModelScope.launch { ... try { } finally { isXLoading = false } }`. Render with the shared `LoadingIndicator` component (or inline `CircularProgressIndicator` sized to the trigger control, e.g. inside a button).
- **Empty**: render the shared `EmptyState` component when a list/collection is empty and not loading.
- **Errors**: ViewModel exposes `errorMessage: StateFlow<String?>` (`null` = no pending error) + a `clearError()` function. Screen holds `val snackbarHostState = remember { SnackbarHostState() }`, wires `LaunchedEffect(errorMessage) { if (errorMessage != null) { snackbarHostState.showSnackbar(errorMessage!!); viewModel.clearError() } }`, and passes `snackbarHost = { SnackbarHost(snackbarHostState) }` into `LjScaffold`. This is already the convention in Group Sync, Favorites, and Map — Routes now follows it too.

## Phase 4 — Unify dialog/sheet patterns (MEDIUM)

- [ ] `RoutesScreen.kt` StartRouteDialog uses custom `LjCard`-based dialog while delete confirmations use `AlertDialog` — pick one pattern (recommend `AlertDialog` — accessible-by-default, focus trap, back-button handling) and migrate the outlier
- [ ] `RoutesPickerSheet` vs `FavoritesPickerSheet` — diff their styling, align padding/header/handle treatment

## Phase 5 — Touch targets (CRITICAL) — DONE 2026-06-23

- [x] Audited raw `IconButton`/`Modifier.clickable` usages outside `LjButton`. All plain `IconButton(...)` calls (Settings, Favorites, Routes, Group, Widget, drawer, top bar) rely on Material3's built-in 48dp minimum interactive size — none override it smaller. `LjCheckboxRow`/routes `CheckboxRow` clickable rows are tall enough (Checkbox itself enforces 48dp) so no fix needed there.
- [x] Found a real violation: `UiConstants.FAB_CONTAINER_SIZE` was **36dp** — below the 48dp/44pt minimum — used by every `LjMapIconButton` (all Map screen FABs) and every button in the Widget panel (`WidgetPanelContent.kt`). Bumped to `48.dp` (and `FAB_ICON_SIZE` 20dp → 24dp to match) in `UiConstants.kt` — single source of truth, fixes touch target everywhere it's consumed. All map icon buttons already route through `LjMapIconButton`, confirmed no ad-hoc `IconButton` usage on the map screen.
- [ ] Follow-up: regenerate screenshots (`make screenshot`) — FAB visual size changed slightly (36dp → 48dp).

## Sequencing

Recommended order: Phase 1 → Phase 5 (both cheap, mechanical, high a11y/reliability payoff) → Phase 3 (state handling, biggest "reliability" win) → Phase 2 (token sweep) → Phase 4 (dialog unification, lowest urgency).

## Notes

- Each phase should land as its own PR/commit per `AGENTS.md` doc-sync rules — if a phase touches a documented feature's behavior (e.g. new error states), update the relevant `docs/features/*.md` in the same commit.
- Re-run `make lint && make test` after each phase per project pre-commit policy.
