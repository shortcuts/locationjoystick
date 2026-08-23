# Onboarding

Multi-step first-run flow. Completion tracked via `ONBOARDING_COMPLETE` DataStore key. Module: `:feature:onboarding`.

Key files: `:feature:onboarding:impl/OnboardingScreen.kt`, `:feature:onboarding:impl/OnboardingViewModel.kt`

## Steps

1. Welcome
2. Grant `ACCESS_FINE_LOCATION`
3. Grant `SYSTEM_ALERT_WINDOW`
4. Enable mock location (deep link to Developer Options; "Check again" button re-checks `AppOpsManager`)
5. Done → MapScreen

## Permission Checks

| Permission | Check method |
|------------|-------------|
| `ACCESS_FINE_LOCATION` | `ContextCompat.checkSelfPermission` |
| `SYSTEM_ALERT_WINDOW` | `Settings.canDrawOverlays(context)` |
| Mock location | `AppOpsManager.checkOpNoThrow(OPSTR_MOCK_LOCATION)` |

## Skip Mock-Location Check

Step 4's card ("Set as fake GPS app") has a secondary action, "This check
doesn't work for me", next to the primary "Open Developer Options" button.
Some modified mock-location apps evade `AppOpsManager` detection, so
`isMockLocationEnabled()` (`core/common/util/AppOpsUtils.kt`) never reports
`MODE_ALLOWED` for them even though mock location actually works. Tapping it
calls `OnboardingViewModel.skipMockLocationCheck()`, which persists
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
