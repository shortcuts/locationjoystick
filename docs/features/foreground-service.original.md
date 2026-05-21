# Foreground Service

Persistent notification while spoofing is active. Keeps the app running when minimized or screen off.

Key files: `:core:location/MockLocationService.kt`

## Setup

- Declared in manifest with `foregroundServiceType="location"`.
- Started via `ServiceCompat.startForeground` with `FOREGROUND_SERVICE_TYPE_LOCATION` (required API 34+).
- Restart behavior: `START_STICKY`.
- Notification channel: `IMPORTANCE_LOW`, channel ID `AppConstants.NotificationConstants.CHANNEL_ID_ACTIVE`.

## Lifecycle

- Update loop runs as a coroutine with `SupervisorJob()` scope.
- On `onDestroy`: cancel coroutine scope + call `locationManager.removeTestProvider`.

## Service Interface

`MockLocationService` exposes `StateFlow<SpoofState>`. Commands: `startSpoofing`, `updatePosition`, `stopSpoofing`.

Clients bind via `LocalBinder` inner class using a `ServiceConnection`. Unbind in `onDestroy`/`onCleared`.
