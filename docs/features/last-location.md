# Last Remembered Location

On app restart, restores the last spoofed position. No manual re-entry needed.

## DataStore Keys (`:core:datastore`)

| Key | Type | Purpose |
|-----|------|---------|
| `REMEMBER_LAST_LOCATION` | `Boolean` | Feature toggle |
| `LAST_LATITUDE` | `Double` | Last spoofed latitude |
| `LAST_LONGITUDE` | `Double` | Last spoofed longitude |

## Behaviour

- On startup, with no position set yet: seed the initial position from `LAST_LATITUDE`/`LAST_LONGITUDE` when `REMEMBER_LAST_LOCATION` is `true` and valid coordinates exist; otherwise from the phone's real location (below); otherwise, with location permission granted, from `DEFAULT_LAT`/`DEFAULT_LON`, so the map always shows a point. Without permission the position stays unset and the next restore call retries.
- Real-location fallback (`RealLocationRepository.lastKnownRealPosition()`): the newest non-mock last-known fix across GPS and network providers. When none exists, the restore asks for one fresh fix (`getCurrentPosition()`, 10 s timeout) in the background. The default shows at once; the fresh fix then replaces it and the map follows, unless the position changed meanwhile (teleport, start). Read-only: not persisted, no cooldown, no teleport. It applies whether `REMEMBER_LAST_LOCATION` is on or off (the toggle gates only the saved position) and is skipped silently without location permission.
- The Start button begins from the current position, then the stored last location (read directly, not gated by the toggle), then `DEFAULT_LAT`/`DEFAULT_LON`.
- The startup restore (`MapController.restoreLastLocationIfNeeded()`, called from `MapController.init` and
  the map screen) is single-flight: an overlapping call is a no-op, a call after a restore that found nothing
  retries.
- While spoofing runs, `MockLocationService.pushLocationUpdate()` — the single 1 Hz tick every mode
  (joystick, walk-to, route replay, roaming, follower catch-up) routes through — writes the current
  position to DataStore, throttled to `AppConstants.LocationConstants.LAST_LOCATION_PERSIST_INTERVAL_MS`
  (5 s) so a battery death or hard reboot loses at most a few seconds of movement instead of resuming
  at wherever the session started. The write is unconditional — `REMEMBER_LAST_LOCATION` gates restore
  only, not persistence.
- `stopSpoofing()` and `onDestroy()` also persist immediately on clean stop / process kill, same as before.
