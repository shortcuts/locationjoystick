# Permissions

## Matrix

| Permission | Type | When Required | Manifest |
|---|---|---|---|
| `ACCESS_FINE_LOCATION` | Dangerous | Map center, route record | Yes |
| `ACCESS_COARSE_LOCATION` | Dangerous | Fallback if fine denied | Yes |
| `SYSTEM_ALERT_WINDOW` | Special (AppOps) | Joystick + widget overlays | Yes |
| `FOREGROUND_SERVICE` | Normal | Run `MockLocationService` | Yes |
| `FOREGROUND_SERVICE_LOCATION` | Normal (API 34+) | `foregroundServiceType="location"` | Yes (API 34+) |
| `BIND_ACCESSIBILITY_SERVICE` | Special (user enables in Android Accessibility settings, after the in-app disclosure) | Screenshots for the Tap to Walk automatic fit and, with `canPerformGestures`, the Reconnect accessory tap | Yes (service declaration) |
| `POST_NOTIFICATIONS` | Dangerous (API 33+) | Foreground notification, learning progress and Cancel | Yes |
| `ACCESS_MOCK_LOCATION` | Dev Options only | Register mock loc provider | Yes (with `tools:ignore="MockLocation"` — IS mock loc app, not test-only) |

## Notes

- `SYSTEM_ALERT_WINDOW` not via `requestPermissions`. Need `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- API 34+: `FOREGROUND_SERVICE_LOCATION` must be in manifest or `startForeground` crashes.
- `ACCESS_MOCK_LOCATION` dev-options only. `tools:ignore="MockLocation"` intentional — app IS mock loc provider.
- `BIND_ACCESSIBILITY_SERVICE`: the in-app disclosure gates it, not just the system grant. The screenshot consent and the tap consent are separate, and the tap needs its own acceptance. See docs/features/tap-to-walk.md and docs/features/bluetooth-reconnect-tap.md.
- `POST_NOTIFICATIONS`: asked once on a fresh launch, not on recreation such as rotation. Denied, the foreground notification may not show. The widget crosshair still shows learning (active tint) and cancels it, so learning never depends on the notification.
