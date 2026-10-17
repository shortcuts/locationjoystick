# Last Remembered Location

On app restart, restores the last spoofed position, so the user does not re-enter it.

Stored values are per device and are listed in the settings and preferences code. The Home favorite is the one exception that has its own rules: see [favorites.md](favorites.md), "Home".

## Behaviour

- **Start position at launch**, when nothing is set yet, in this order:
  1. The Home favorite, if one is set and still exists. It wins over everything, even when remembering is off.
  2. The saved position, if remembering is on and the saved coordinates are valid.
  3. The phone's own last known real location, from a provider that is not spoofing. When none exists, one fresh fix is requested in the background. The map shows the default at once, and the fresh fix replaces it unless the position has changed since (a teleport or a start).
  4. The built-in default position, once location permission is granted, so the map always shows a point.
  Without permission, the position stays unset, and the next restore call retries.
- The real-location lookup is read-only. It is not saved, does not touch the teleport cooldown, and does not teleport. It runs whether remembering is on or off, because the switch gates only the saved position.
- **Start button** begins from the current position, then the Home favorite, then the saved position, then the default. The saved position is read even when remembering is off. Start must never begin from nothing.
- **Group Sync:** a follower's position comes from the leader, so the restore does not seed a position over it. See [group-sync.md](group-sync.md).
- The restore is single-flight. An overlapping call does nothing, and a call after a restore that found nothing tries again.

## Persistence while spoofing

Every tick writes the current position, but at most once every few seconds. A battery death or a hard reboot then loses a few seconds of movement, not the whole session. Writes are not gated by the remember switch: the switch controls only restore, not saving. A clean stop and process shutdown write immediately.
