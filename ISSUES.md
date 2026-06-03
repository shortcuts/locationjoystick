# Known Issues & Backlog

## Documentation Outdated Items

No outstanding documentation issues.

---

## Bugs

- FloatingWidgetService -> Map -> Search -> Select result -> Doesn't show the anti-cheat cooldown timeout

---

## Technical Debt (pre-1.1)

### [CRITICAL] saveChanges() fires 20+ non-atomic sequential DataStore writes

**File:** `SettingsViewModel.kt:296–356`

`saveChanges()` calls `settingsRepository.set*()` for each dirty field sequentially. Every call is its own `dataStore.edit {}` transaction. If any call throws mid-way, `mutableDraft.value = DraftState()` at line 349 still runs — the draft is cleared, but only a partial set of settings was persisted. The user gets "Settings saved" but some fields are reverted to their previous values with no indication.

Secondary cost: 20+ sequential DataStore writes fan out to all collectors 20+ times instead of once.

**Fix:** Add a `setSettingsSnapshot(draft: DraftState)` method to `PreferencesDataSource` that writes all dirty fields in a single `dataStore.edit {}` block. Only clear the draft after that single atomic write succeeds.

---

### [HIGH] Two `importSettings()` overloads apply different field subsets and will drift

**File:** `SettingsViewModel.kt:436–578`

`importSettings(Uri)` routes through the draft setters (`setWidgetFeatures()`, `setJitterIdleRadius()`, etc.) and does not apply `roamingDefaults`, `bearingHoldOnIdle`, `warmupEnabled`, `satelliteExtrasEnabled`, or `suspendedMockingEnabled`. `importSettings(ExportData)` writes directly to the repository and skips the draft entirely, applying a different subset. The two paths will continue to drift as new settings are added.

**Fix:** Extract a single private `applyExportData(data: ExportData, replace: Boolean)` that writes atomically to the repository. Both overloads call it. Remove the draft-setter variant of import entirely.

---

### [HIGH] `RepoState` is a redundant mirror of `SettingsSnapshot`

**File:** `SettingsViewModel.kt:80–156`

`RepoState` is a private data class with 21 fields that are copied one-for-one from `SettingsSnapshot` with no transformation at lines 131–156. It exists only to feed the `combine(repoStateFlow, draftStateFlow)` merge. `SettingsSnapshot` itself is the canonical type and already contains the same fields with the same types. The mapping is pure boilerplate that must be kept in sync with `SettingsSnapshot` by hand.

**Fix:** Use `SettingsSnapshot` directly in the `combine`. Drop `RepoState`. The `uiState` combine lambda reads `s.walkSpeedMs` instead of `repoState.walkSpeed`, etc.

---

### [MEDIUM] `FloatingWidgetService.startRoamingWith()` bypasses `RoamingDefaults.toConfig()`

**File:** `FloatingWidgetService.kt:581–605`

Manually constructs `RoamingConfig(centerPosition, radiusMeters, distanceMeters, ...)` field-by-field. `MapViewModel.startRoamingFromDraft()` uses the canonical `draft.toConfig(position)` extension for the same operation. If `RoamingConfig` or `RoamingDefaults.toConfig()` gains a field (e.g. `previewWaypoints`), the service path silently drops it — already happened: `MapViewModel` passes `previewWaypoints` via `copy(previewWaypoints = ...)` but the service path has no equivalent.

**Fix:** Replace the manual construction with `defaults.toConfig(pos)` + any overlay-specific `copy(...)` overrides.

---

### [MEDIUM] `getElevationTiltJitterDegrees()` / `getElevationNoiseAmplitudeMs2()` missed `pref()` cleanup

**File:** `AppPreferencesDataSource.kt:549–589`

The `pref(key, default)` helper was extracted in commit `54201a5` to eliminate hand-rolled `dataStore.data.catch { }.map { }` chains. These two methods still use the old pattern — they were missed. They're 10 lines each where `pref()` would make them one line each.

**Fix:**
```kotlin
override fun getElevationTiltJitterDegrees(): Flow<Float> =
    pref(Keys.ELEVATION_TILT_JITTER_DEGREES, DEFAULT_ELEVATION_TILT_JITTER_DEGREES)

override fun getElevationNoiseAmplitudeMs2(): Flow<Float> =
    pref(Keys.ELEVATION_NOISE_AMPLITUDE_MS2, DEFAULT_ELEVATION_NOISE_AMPLITUDE_MS2)
```

---

### [MEDIUM] `isActivityPaused` mode-dispatch lives in the UI layer

**File:** `FloatingWidgetService.kt:255–261`

```kotlin
val isActivityPaused =
    isWalkPaused ||
        (mockMode == ROUTE_REPLAY && mockLocationState == PAUSED) ||
        (mockMode == ROAMING && isRoamingPausedWidget)
```

This is mode-dispatch business logic embedded inline in a Composable in a service. The same concept likely exists elsewhere. `LocationRepository` or a dedicated query already owns `isWalkPaused`, `currentMode`, and `mockLocationState` — it should expose a single `isCurrentActivityPaused: Flow<Boolean>` derived property so this triple-condition isn't reimplemented at each call site.

---

### [LOW] Deep link uses `StateFlow + filterNotNull` — subtle redelivery risk on resubscription

**File:** `MapViewModel.kt:623–632`

```kotlin
deepLinkRepository.pendingCoords
    .filterNotNull()
    .collect { coords ->
        _uiState.update { it.copy(pendingTapPosition = coords) }
        deepLinkRepository.consume()
    }
```

`StateFlow` replays its last value on new subscription. Between `_uiState.update` and `deepLinkRepository.consume()`, the flow still holds the non-null coord. If the coroutine is cancelled and restarted before `consume()` completes (e.g. process death, ViewModel recreation in tests), the coord is re-applied. A `SharedFlow(replay=0)` in `DeepLinkRepository` is the correct primitive for one-shot events.

---

### [LOW] Hot location IDs are name-derived — stale entries accumulate if names change across versions

**File:** `FavoriteRepository.kt:86`

```kotlin
val id = HOT_ID_PREFIX + name.lowercase().replace(Regex("[^a-z0-9]"), "_")
```

The ID is generated from the name at insert time. If a hot location name is corrected in a future version (e.g. `"Osaka Tokyo"` → `"Osaka"`), `removeHotLocations()` will delete the new entry (`hot_osaka`) but the old entry (`hot_osaka_tokyo`) has a non-`hot_` ID only if the user had renamed it — but if it was inserted as `hot_osaka_tokyo` and the user never touched it, it stays. The upsert matches by name, not by ID, so it inserts a new `hot_osaka` and the old `hot_osaka_tokyo` is orphaned until the user deletes it manually.

Minor risk given the list is stable, but worth documenting.

---

## Frontend UI/UX


---

## Wiki

No outstanding wiki items.
