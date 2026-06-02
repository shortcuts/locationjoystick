# Known Issues & Backlog

## Documentation Outdated Items

No outstanding documentation issues.

---

## Bugs

- **Map zooms out on search/favorite selection** — selecting a location from Nominatim search or Favorites teleports the camera but uses an incorrect zoom level instead of `AppConstants.MapConstants.DEFAULT_ZOOM`. Should call `CameraUpdateFactory.newLatLngZoom(position, DEFAULT_ZOOM)` (or equivalent MapLibre call) rather than `newLatLng` alone.

---

## Technical Debt (pre-1.1)

- **FloatingWidgetService decomposition** — 923 lines, approaching 1k limit. Extract floating-view builders into `WidgetPanelPresenter` collaborator; extract 3 `ServiceConnection`s into `WidgetServiceBinder` holder. Target: <500 lines of pure lifecycle/window plumbing. No unit tests exist for this class.
- **RealismSettingsState extraction** — `MockLocationService` holds 25 `@Volatile` realism fields + 15 `observeSetting()` wirings inline. Extract into a `RealismSettingsState` class that owns those fields and exposes `observe(scope, repo)` + `captureInto(snapshot)`.
- **Position writeback race** — `locationRepository.currentPosition.collect` writes `currentLat/currentLon` on `Dispatchers.Default`; tick loop reads them separately. Funnel all position writes through a single entry point to eliminate the TOCTOU window.

---

## Frontend UI/UX

- **Black text on dark bottom sheet background** — bottom drawer sheet renders black text against a dark background, making content unreadable. Root cause: likely hardcoded `Color.Black` or missing `contentColor` propagation instead of `MaterialTheme.colorScheme.onSurface`. Audit all `ModalBottomSheet` / `BottomSheetScaffold` usages + any custom `Surface` wrappers across all feature screens — verify text, icons, and labels inherit correct content color from the design system. Use `/frontend-design` for the fix pass.

---

## Wiki

No outstanding wiki items.
