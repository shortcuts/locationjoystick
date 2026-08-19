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

## Hiding the Notification Icon

Settings → Menus → Privacy → "Hide notification icon"
(`AppSettings.hideForegroundNotification`, DataStore key
`hide_foreground_notification`, default `false`) switches the notification
to an `IMPORTANCE_MIN` channel instead of the default `IMPORTANCE_LOW`
channel. This removes the status bar icon, lock-screen visibility, and
heads-up alerting — the maximum degree of hiding Android permits.

Android requires every foreground service to post an active notification;
there is no API to run one with zero notification. The notification still
exists and is reachable by pulling down the notification shade (collapsed
under "Show silent notifications"), which satisfies the platform
requirement without keeping a persistent icon visible.

Implemented as two pre-registered `NotificationChannel`s
(`MockLocationNotification.kt`) — `location_spoof_channel` (`IMPORTANCE_LOW`)
and `location_spoof_channel_minimized` (`IMPORTANCE_MIN`) — selected per
notification build via `notificationChannelId(hideNotification)`, since a
channel's importance can't be changed after creation. Round-trips through
`ExportData` like `hideWidgetOverlay`.

`onStartCommand()` re-posts the notification via `startForeground()` on
every call, not just service creation (e.g. once per joystick-driven
`ACTION_UPDATE_POSITION` intent). It reads the setting from the
`@Volatile` field the same reactive collector writes, instead of a
hardcoded default — otherwise every re-post would silently revert the
notification back to the visible channel mid-session.