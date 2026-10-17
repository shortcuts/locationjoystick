# Onboarding

Multi-step first-run flow. Completion tracked via `ONBOARDING_COMPLETE` DataStore key. Module: `:feature:onboarding`.

Key files: `:feature:onboarding:impl/OnboardingScreen.kt`, `:feature:onboarding:impl/OnboardingViewModel.kt`

## Steps

One `ONBOARDING_ROUTE`; a local `rememberSaveable` flag in `OnboardingRoute` switches between two
screens. There is no separate disclaimer screen — the header "Set up locationjoystick" is the
"Welcome". System back on the picker returns to the permission screen.

Screen 1 (`OnboardingScreen.kt`), "Continue" button:

1. Grant `ACCESS_FINE_LOCATION`
2. Grant `SYSTEM_ALERT_WINDOW`
3. Enable mock location (deep link to Developer Options; re-checked automatically on resume — see "Permission Checks" below)

On a fresh install the Accessibility prominent disclosure (`AccessibilityDisclosureDialog`) opens over screen 1
before any permission card, on every API level, whichever path is later picked
(`OnboardingAccessibilityDisclosure` in `PathPickerStep.kt`, shown by `OnboardingRoute`). It shows while the stored
choice is unanswered; `OnboardingRoute` only renders while onboarding is incomplete, so updates never see it.
Accept (opens Android's Accessibility settings on API 30+, otherwise only records the choice), Decline and
Skip each store an answer and reveal the permission screen. See docs/features/tap-to-walk.md,
"Prominent Disclosure".

Screen 2, path picker (`PathPickerStep.kt`), "Start using locationjoystick" button, no skip:

4. Pick a guided path: Default (preselected), Pokémon Go or Pikmin Bloom. Selection is held in
   `OnboardingUiState.selectedPath` and written to `guidedPath` (`SettingsRepository.setGuidedPath`) right
   before `setOnboardingComplete(true)` when the user finishes. The picker only says which features
   each path hides; see docs/domain-models.md, `GuidedPath`. The full matrix is in docs/features/guided-paths.md.
5. Optional Tap to Walk card, shown on Default and Pokémon Go and hidden on Pikmin Bloom (every API
   level; card text names the calibration screenshots). On API 30+ the disclosure also
   opens by itself when the picker first appears while the choice is unanswered (fallback; normally
   already answered on screen 1) and the path shows Tap to Walk, with Accept, Decline and a small Skip. Accept opens Android's
   Accessibility settings (below API 30 it only records the choice). Every button stores an answer, so
   it never auto-shows again; Tap to Walk entry points still ask until Accept. The card's "Review and
   turn on" reopens it. Not required to proceed. Installs that already finished onboarding never see the
   auto-show. See docs/features/tap-to-walk.md, "Prominent Disclosure".
6. Done → MapScreen

A "Step X of 4" label and progress bar sit above the cards. On screen 1 it counts how many of the
three permissions are granted (progress = granted / 4); the picker is always "Step 4 of 4" with a full bar.

An optional language dropdown (EN / CN / TW / System default) sits in the header of both screens —
it is not one of the numbered/counted steps and can be left alone. See docs/features/localization.md.

## Permission Checks

| Permission | Check method |
|------------|-------------|
| `ACCESS_FINE_LOCATION` | `ContextCompat.checkSelfPermission` |
| `SYSTEM_ALERT_WINDOW` | `Settings.canDrawOverlays(context)` |
| Mock location | `AppOpsManager.checkOpNoThrow(OPSTR_MOCK_LOCATION)` |

There is no manual "Check again" button. `OnboardingScreen` re-runs
`viewModel.checkPermissions()` automatically whenever the screen resumes —
a `DisposableEffect`/`LifecycleEventObserver` on `Lifecycle.Event.ON_RESUME` —
so returning from Settings or Developer Options re-evaluates every
permission without user action.

## Returning After Revoking the Overlay Permission

Once onboarding is completed (`AppSettings`-adjacent `ONBOARDING_COMPLETE` DataStore flag,
`SettingsRepository.getOnboardingComplete()`), later revoking `SYSTEM_ALERT_WINDOW` in system
Settings does **not** force the user back through the full onboarding flow on the next app
launch — only `ACCESS_FINE_LOCATION` and mock location remain required to reach `IDLE_ROUTE`.
Joystick and widget overlays simply stay unavailable until the permission is re-granted
(`MockLocationService` already gates auto-starting them on `Settings.canDrawOverlays`, see
docs/features/joystick.md and docs/features/widget.md) — everything else (teleport, walk,
routes) keeps working.

Losing `ACCESS_FINE_LOCATION` is unaffected by this and still forces a restart (see
`MockLocationService.onStartCommand`'s `setOnboardingComplete(false)` on missing location
permission) — only the overlay permission is exempted, since it's the one permission every
other feature already tolerates being absent.

Implemented via the pure `isNavGateReachable(onboardingComplete, coreGranted)` gate in `LjNavHost.kt`
(`coreGranted && onboardingComplete`), fed by `NavGateViewModel.onboardingComplete`. A first-run user is
never gated through, so granting permissions or skipping the mock-location check cannot jump past the
path picker; only finishing it sets `onboardingComplete`. A cold start with everything already granted
still goes straight to `IDLE_ROUTE`, so existing installs are never shown the picker. Mirrors the existing `bypassMockLocationCheck` pattern
used for the mock-location check skip above.

## Skip Mock-Location Check

Step 3's card ("Set as fake GPS app") has a secondary action, "Skip", next
to the primary "Open Developer options" button. Some modified mock-location
apps evade `AppOpsManager` detection, so `isMockLocationEnabled()`
(`core/common/util/AppOpsUtils.kt`) never reports `MODE_ALLOWED` for them
even though mock location actually works. Tapping it shows a confirmation
dialog ("Skip this check?") warning that most devices need this check to
pass for spoofing to work — "Skip anyway" calls
`OnboardingViewModel.skipMockLocationCheck()`, which persists
`AppSettings.bypassMockLocationCheck` (DataStore key
`bypass_mock_location_check`, default `false`) and re-runs
`checkPermissions()`, marking the step done immediately.

This is an onboarding action, not a Settings toggle — there is no way to
re-enable the check from the UI short of a fresh install or "Reset all
data" (docs/features/export-import.md).

Both gates honor the persisted flag:
- `OnboardingViewModel.checkPermissions()` treats mock location as enabled
  once it's set, regardless of what `isMockLocationEnabled()` reports.
- `LjNavHost`'s start-destination check (`app/navigation/LjNavHost.kt`) does
  the same — via `NavGateViewModel`, since the DataStore read is async and
  the nav graph's start destination is otherwise fixed at first composition,
  it corrects an already-chosen `ONBOARDING_ROUTE` to `IDLE_ROUTE` once the
  flag loads and the other permissions are granted. This is also what keeps
  onboarding from ever being shown again after a skip.

Round-trips through `ExportData` like `hideTeleportFeatures`.

## Edge Cases

- Each permission step can be skipped. Show a banner if a required permission is missing.
- Re-check for revoked permissions on `onResume`.
