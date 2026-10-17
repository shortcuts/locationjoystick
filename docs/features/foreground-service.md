# Foreground Service

A persistent notification is shown while spoofing is active. It keeps the app alive when it is minimised or the screen is off.

Key files: the mock location service in `:core:location`.

## Why a foreground service

Without the foreground service, Android stops the spoofing loop as soon as the app leaves the screen. The notification is the platform's requirement for a long-running service that uses location. It is not decorative.

The service restarts itself with no intent after the system kills it, so the spoofing loop can recover without the user opening the app. The restart rules are in the service code and in [group-sync.md](group-sync.md), "Boot and restore".

## Lifecycle

- The update loop runs in a coroutine scope that one failed child cannot cancel. A tick error must not stop the whole loop.
- On destroy, the scope is cancelled and the test provider is removed. See [mock-location.md](mock-location.md), "Core mechanics", for why removal is mandatory.

## Wake lock

Route replay and walk-to can slow down when the screen locks, because some devices throttle background apps. While spoofing is active, the service holds a partial wake lock, so these engines keep advancing.

The lock is taken when spoofing starts, and released on stop, on park, and on destroy. Holding it while idle would drain the battery for nothing.

Parking keeps the service running. A parked widget must not race a dead service when the user taps Start again. A restart of a parked service stays parked, so the phone does not start spoofing on its own. A group leader's stop keeps the service running too, because the sync server must keep serving followers. See [widget.md](widget.md), "Pause and stop".

## Hiding the notification icon

A setting moves the notification to the lowest importance channel. That removes the status bar icon, lock-screen visibility and alerts. It is the most hidden a notification can be on Android.

Android still requires an active notification for a foreground service. There is no API for zero notification. The notification stays reachable in the shade, under silent notifications. That meets the platform's rule without a visible icon.

Two channels exist, one per importance level. A channel's importance cannot change after it is created, so the app switches channels rather than changing one.

The notification is posted again on every start command, not only at creation. Otherwise a joystick-driven update would quietly restore the visible channel mid-session. The re-post reads the setting from the same live value that the settings collector writes.
