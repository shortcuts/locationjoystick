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
| `POST_NOTIFICATIONS` | Dangerous (API 33+) | Foreground notification | Yes |
| `ACCESS_MOCK_LOCATION` | Dev Options only | Register mock loc provider | Yes (with `tools:ignore="MockLocation"` — IS mock loc app, not test-only) |
| `INTERNET` | Normal | Map tiles, place search (reverse lookup and search), update check, follower sync to the leader phone | Yes |
| `ACCESS_NETWORK_STATE` | Normal | Declared in the app manifest. No app code calls the network-state API, so it is only needed if a library does | Yes |
| `ACCESS_WIFI_STATE` | Normal | Group Sync discovery: the Wi-Fi manager that provides its multicast lock. Merged into the app from a dependency | Yes (merged) |
| `CHANGE_WIFI_MULTICAST_STATE` | Normal | Group Sync discovery: holds a multicast lock while the leader advertises or the follower finds it | Yes |
| `WAKE_LOCK` | Normal | Partial wake lock while a mock location session runs, so updates continue with the screen off | Yes |
| `CAMERA` | Dangerous (optional) | QR scanners for Group Sync and QR transfer. The app works without it | Yes (`android:required="false"`) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Normal | Declared, but no app code requests the battery-optimization exemption. Candidate for removal | Yes |

## Notes

- `SYSTEM_ALERT_WINDOW` not via `requestPermissions`. Need `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- API 34+: `FOREGROUND_SERVICE_LOCATION` must be in manifest or `startForeground` crashes.
- `ACCESS_MOCK_LOCATION` dev-options only. `tools:ignore="MockLocation"` intentional — app IS mock loc provider.
- `BIND_ACCESSIBILITY_SERVICE`: the in-app disclosure gates it, not just the system grant. One disclosure covers the screenshots and the tap. The Reconnect switch is a separate opt-in. See docs/features/tap-to-walk.md and docs/features/bluetooth-reconnect-tap.md.
- `POST_NOTIFICATIONS`: asked once on a fresh launch, not on recreation such as rotation. Denied, the foreground notification may not show, and reconnect notices do not show either.
