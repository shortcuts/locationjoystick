# Permissions

## Matrix

| Permission | Type | When Required | Manifest |
|---|---|---|---|
| `ACCESS_FINE_LOCATION` | Dangerous | Map center, route record | Yes |
| `ACCESS_COARSE_LOCATION` | Dangerous | Fallback if fine denied | Yes |
| `SYSTEM_ALERT_WINDOW` | Special (AppOps) | Joystick + widget overlays | Yes |
| `FOREGROUND_SERVICE` | Normal | Run `MockLocationService` | Yes |
| `FOREGROUND_SERVICE_LOCATION` | Normal (API 34+) | `foregroundServiceType="location"` | Yes (API 34+) |
| `ACCESS_MOCK_LOCATION` | Dev Options only | Register mock loc provider | Yes (with `tools:ignore="MockLocation"` — IS mock loc app, not test-only) |

## Notes

- `SYSTEM_ALERT_WINDOW` not via `requestPermissions`. Need `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- API 34+: `FOREGROUND_SERVICE_LOCATION` must be in manifest or `startForeground` crashes.
- `ACCESS_MOCK_LOCATION` dev-options only. `tools:ignore="MockLocation"` intentional — app IS mock loc provider.