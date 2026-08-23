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

## Bypass Mock-Location Check

Settings → Menus → Privacy → "Bypass mock location check"
(`AppSettings.bypassMockLocationCheck`, DataStore key
`bypass_mock_location_check`, default `false`) skips the
`AppOpsManager.OPSTR_MOCK_LOCATION` check that step 4 (and the post-onboarding
gate below) otherwise requires. Some modified mock-location apps evade
AppOpsManager detection, so `isMockLocationEnabled()`
(`core/common/util/AppOpsUtils.kt`) never reports `MODE_ALLOWED` for them
even though mock location actually works — this setting lets those users
finish onboarding and start spoofing anyway. Off by default, since for most
users the check catches a real misconfiguration.

Both gates honor it:
- `OnboardingViewModel.checkPermissions()` treats mock location as enabled
  when the bypass is on, regardless of what `isMockLocationEnabled()` reports.
- `LjNavHost`'s start-destination check (`app/navigation/LjNavHost.kt`) does
  the same — via `NavGateViewModel`, since the DataStore read is async and
  the nav graph's start destination is otherwise fixed at first composition,
  it corrects an already-chosen `ONBOARDING_ROUTE` to `IDLE_ROUTE` once the
  bypass value loads and the other permissions are granted.

Round-trips through `ExportData` like `hideTeleportFeatures`.

## Edge Cases

- Each permission step can be skipped. Show a banner if a required permission is missing.
- Re-check for revoked permissions on `onResume`.
