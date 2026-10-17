# Permissions

## Matrix

| Permission | Type | When Required | Manifest |
|---|---|---|---|
| `ACCESS_FINE_LOCATION` | Dangerous | Map center, route record | Yes |
| `ACCESS_COARSE_LOCATION` | Dangerous | Fallback if fine denied | Yes |
| `SYSTEM_ALERT_WINDOW` | Special (AppOps) | Joystick + widget overlays | Yes |
| `FOREGROUND_SERVICE` | Normal | Run `MockLocationService` | Yes |
| `FOREGROUND_SERVICE_LOCATION` | Normal (API 34+) | `foregroundServiceType="location"` | Yes (API 34+) |
| `BIND_ACCESSIBILITY_SERVICE` | Special (user enables in Android Accessibility settings, after the in-app disclosure) | Screenshots for the Tap to Walk calibration and, with `canPerformGestures`, the Reconnect accessory tap | Yes (service declaration) |
| `ACCESS_MOCK_LOCATION` | Dev Options only | Register mock loc provider | Yes (with `tools:ignore="MockLocation"` — IS mock loc app, not test-only) |

## Notes

- `SYSTEM_ALERT_WINDOW` not via `requestPermissions`. Need `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- API 34+: `FOREGROUND_SERVICE_LOCATION` must be in manifest or `startForeground` crashes.
- `ACCESS_MOCK_LOCATION` dev-options only. `tools:ignore="MockLocation"` intentional — app IS mock loc provider.
- `BIND_ACCESSIBILITY_SERVICE`: two in-app disclosure flags gate it. `accessibility_disclosure_choice` gates screenshots; `reconnect_tap_disclosure_accepted` additionally gates the tap, so users must re-accept the reworded disclosure to enable Reconnect accessory. See docs/features/bluetooth-reconnect-tap.md.
