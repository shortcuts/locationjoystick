# Onboarding

Multi-step first-run flow. Completion tracked via `ONBOARDING_COMPLETE` DataStore key. Module: `:feature:onboarding`.

Key files: `:feature:onboarding:impl/OnboardingScreen.kt`, `:feature:onboarding:impl/OnboardingViewModel.kt`

## Steps

1. Welcome
2. Grant `ACCESS_FINE_LOCATION`
3. Grant `SYSTEM_ALERT_WINDOW`
4. Enable mock location (deep link to Developer Options; re-checked automatically on resume — see "Permission Checks" below)
5. Done → MapScreen

A "Step X of 3" label and progress bar sit above the three permission cards, computed from how
many of them are currently granted — steps 2–4 above, not Welcome/Done.

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
@docs/features/joystick.md and @docs/features/widget.md) — everything else (teleport, walk,
routes) keeps working.

Losing `ACCESS_FINE_LOCATION` is unaffected by this and still forces a restart (see
`MockLocationService.onStartCommand`'s `setOnboardingComplete(false)` on missing location
permission) — only the overlay permission is exempted, since it's the one permission every
other feature already tolerates being absent.

Implemented via the pure `isNavGateReachable()` gate in `LjNavHost.kt`, fed by
`NavGateViewModel.onboardingComplete` — mirrors the existing `bypassMockLocationCheck` pattern
used for the mock-location check skip above.

## Skip Mock-Location Check

Step 4's card ("Set as fake GPS app") has a secondary action, "Skip", next
to the primary "Open Developer Options" button. Some modified mock-location
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
data" (@docs/features/export-import.md).

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
