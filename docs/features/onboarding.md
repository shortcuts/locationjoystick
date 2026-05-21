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

## Edge Cases

- Each permission step can be skipped. Show a banner if a required permission is missing.
- Re-check for revoked permissions on `onResume`.
