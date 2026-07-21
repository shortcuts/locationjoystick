# Foreground Service

Persistent notif while spoofing active. Keeps app alive when minimized/screen off.

Key files: `:core:location/MockLocationService.kt`

## Setup

- Declared in manifest with `foregroundServiceType="location"`.
- Started via `ServiceCompat.startForeground` with `FOREGROUND_SERVICE_TYPE_LOCATION` (API 34+ required).
- Restart: `START_STICKY`.
- Notif channel: `IMPORTANCE_LOW`, ID `AppConstants.NotificationConstants.CHANNEL_ID_ACTIVE`.

## Lifecycle

- Update loop: coroutine with `SupervisorJob()` scope.
- `onDestroy`: cancel scope + call `locationManager.removeTestProvider`.

## Service Interface

`MockLocationService` exposes `StateFlow<SpoofState>`. Commands: `startSpoofing`, `updatePosition`, `stopSpoofing`.

Clients bind via `LocalBinder` inner class + `ServiceConnection`. Unbind in `onDestroy`/`onCleared`.

## Wakelock Handling

To keep route replay and walk-to advancing reliably when the screen locks (workaround for Doze/Adaptive Battery throttling on some devices), the service holds a `PARTIAL_WAKE_LOCK` while spoofing is active (`state != IDLE`). Acquired in `startSpoofing()`, released in `stopSpoofing()` and `onDestroy()`.