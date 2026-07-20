# Group Sync

Sync spoofed location across multiple devices on the same Wi-Fi network. No account required.

Key files: `:feature:group:impl/GroupSyncScreen.kt`, `:feature:group:impl/GroupSyncViewModel.kt`, `:core:data/GroupRepository.kt`, `:core:location/GroupNsdManager.kt`, `:core:location/FollowerCatchUpCoordinator.kt`

## Roles

- **Leader**: hosts the group. Starts a local server, registers an NSD service, and broadcasts position to followers when sharing is enabled.
- **Follower**: joins via QR scan or 6-character group code. Mirrors leader's spoofed location when follower mode is enabled.
- **None**: not in a group (default state).

## Group Code

The leader's group ID is a 6-character uppercase alphanumeric code (e.g. `AB3X9K`), generated from `CODE_CHARS` (`A–Z` excluding `I` and `O`, `2–9`). This replaces the previous UUID. The code is used as:
- The auth token for the HTTP sync server (`/position?token=CODE`)
- The NSD service instance name for code-based discovery
- The value shown prominently on the leader screen

## Flow

### Create a group (leader)
1. Open Group Sync screen → "Create group — I'm the leader".
2. `GroupSyncViewModel.createGroup()` generates a 6-char code and sends `ACTION_START_LEADER`.
3. `MockLocationService.enterLeaderMode(code)`:
   - Starts `LeaderSyncServer` (OS-assigned port).
   - Registers NSD service via `GroupNsdManager.startLeader(code, port)`.
   - Calls `GroupRepository.createGroup(host, port, code)`.
4. QR code generated encoding `locationjoystick://group?host=HOST&port=PORT&id=CODE`.
5. Leader screen shows both the 6-char code and the QR. Toggle **Sharing** to broadcast.

### Join a group (follower) — via QR
1. Tap "Scan QR" on the Group Sync screen → `GroupQrScannerScreen` opens.
2. Scan the leader's QR → raw URL parsed by `GroupSyncViewModel.joinViaScannedUrl()`.
3. `GroupRepository.joinGroup(invite)` stores connection details.
4. Toggle **Follow leader** to start mirroring.

### Join a group (follower) — via code
1. Tap "Enter code" on the Group Sync screen → 6-char input dialog.
2. `GroupSyncViewModel.joinByCode(code)` calls `GroupNsdManager.discoverByCode(code)`.
3. NSD discovery finds the leader's advertised service by name, resolves host:port.
4. Joins group with resolved host:port and the entered code as groupId.

### Leave
- Leader: `ACTION_EXIT_LEADER` → server stopped + `GroupNsdManager.stopLeader()`.
- Follower: `ACTION_EXIT_FOLLOWER` if active → group cleared.

## NSD (Network Service Discovery)

`GroupNsdManager` (`@Singleton`, `:core:location`) wraps Android's `NsdManager`:

- **Leader**: registers a `_ljsync._tcp.` service with the 6-char code as instance name.
- **Follower**: discovers `_ljsync._tcp.` services, matches by instance name, resolves to host:port.
- Discovery timeout: `AppConstants.SyncConstants.NSD_DISCOVERY_TIMEOUT_MS` (10 s).
- NSD is only used for code-based join. QR join uses the embedded host:port directly.

## Deep Link / QR Format

```
locationjoystick://group?host=HOST&port=PORT&id=CODE
```

The `id` parameter is the 6-char group code. Parsed by `GroupSyncViewModel.joinViaScannedUrl()`. The same format is used for `GroupRepository.pendingGroupInvite` (system QR scanner / deep link path via `MainActivity`).

## State Machine

`GroupRepository` exposes `groupState: StateFlow<GroupState>`. Transitions:

```
NONE → LEADER   (createGroup)
NONE → FOLLOWER (joinGroup via QR or code)
LEADER → NONE   (leaveGroup)
FOLLOWER → NONE (leaveGroup)
LEADER → FOLLOWER (joinGroup while leading — exits leader first)
```

## Service Commands

All group state changes are sent as `Intent` actions to `MockLocationService`:

| Action | Extra keys |
|--------|-----------|
| `ACTION_START_LEADER` | `EXTRA_LEADER_GROUP_ID` (6-char code) |
| `ACTION_EXIT_LEADER` | — |
| `ACTION_ENTER_FOLLOWER` | `EXTRA_FOLLOWER_HOST`, `EXTRA_FOLLOWER_PORT`, `EXTRA_FOLLOWER_GROUP_ID` |
| `ACTION_EXIT_FOLLOWER` | — |

## Follower Catch-Up Walk

Followers never snap straight to the leader's position. `FollowerSyncClient`'s `onPosition` callback calls `FollowerCatchUpCoordinator.setTarget()` (owner of `target: AtomicReference<LatLng?>`); the actual movement happens once per tick in `advanceFollowerCatchUp()` (called at the top of `pushLocationUpdate()`, before `captureSnapshot()`), which delegates to `FollowerCatchUpCoordinator.advance()`.

This logic was extracted from `MockLocationService` into a dedicated `FollowerCatchUpCoordinator` class (`:core:location`, commit `bd2e5335`), mirroring the `WalkCoordinator` pattern (`:core:data`) used for walk-to — state ownership and per-tick step logic live in one small class instead of scattered `@Volatile` fields on the service. Its interface: `setTarget(position, leaderBearing)` records the latest leader position and the leader's own reported bearing, `clear()` resets it (e.g. on exiting follower mode), `currentTarget()` returns the last-known leader position (or `null`), and `advance()` computes one catch-up step.

- Distance to `currentTarget()` is computed via `haversineDistance`. Within `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS`, the follower snaps to the exact target (negligible distance) and zeroes speed.
- Otherwise it advances one step (`advancePosition` along the bearing from `calculateBearing`) at whatever speed profile is currently active (`RealismSettingsState.activeProfileSpeedMs`) — the same profile shown in the floating widget, user-changeable at any time. If the step would overshoot the target, it snaps instead.
- **Idle bearing**: once arrived (snapped or overshot), the reported bearing is the leader's own `leaderBearing` rather than whatever direction this follower's catch-up walk last pointed. Two followers reaching the same leader position from different prior paths would otherwise freeze at different, device-specific headings even though nothing is actually moving — the leader's real bearing is the only value that's consistent and meaningful once idle. `teleportToLeaderNow()`'s `markArrived()` applies the same rule.
- Because the walk uses the follower's own speed profile — not the leader's — a follower that starts far from the leader may take a long time (or never fully catch up if the leader keeps moving). This is intentional: no distance-based auto-teleport.
- **Bootstrap exception**: if spoofing wasn't already running when follower mode is enabled, the first position received starts spoofing directly at the leader's coordinates (`startSpoofing(lat, lon)`) — nothing was being reported to other apps yet, so this carries no anti-cheat risk. If spoofing was already active (already being reported), that first position is treated like any other: walked toward, never snapped.
- **Manual override**: "Teleport to leader now" button (shown on the Follower screen while follow mode is enabled) sends `ACTION_FOLLOWER_TELEPORT` to `MockLocationService`, which calls `teleportToLeaderNow()` — reads `FollowerCatchUpCoordinator.currentTarget()` and is the only path that snaps directly to the last-known leader position.

## Jitter

Followers apply independent per-device jitter to the received position, preserving the configured jitter profile without cloning the leader's exact coordinates.

## Follower Boot Sequence

After a device restart, a follower that was previously synced automatically rejoins the group in the background without requiring a visit to the Group Sync screen.

There is no `RECEIVE_BOOT_COMPLETED` receiver in this app, so nothing runs after a genuine
device reboot until the user opens the app:

1. **App launch trigger**: `LjApplication.onCreate()` reads the persisted `GroupRepository.groupState`. If the role is `LEADER`, or `FOLLOWER` with `followerModeEnabled = true`, it starts `MockLocationService` directly with a plain launch intent — this is what actually resumes sync after a device reboot, since the service was never running to be recovered by the OS.
2. **OS-restart trigger (secondary path)**: if the app process is killed while `MockLocationService` is already running (e.g. low memory), Android restarts the service via `START_STICKY` with a null intent — no reboot involved.
3. **Async restoration**: either trigger routes into the same `onStartCommand` `null ->` branch. If the follower was in `FOLLOWER` mode with `followerModeEnabled = true`, the service launches a background async job to re-discover the leader via NSD.
4. **Exponential backoff retries**: the discovery job retries up to 5 times with exponential backoff (500 ms, 1 s, 2 s, 4 s, 8 s + random jitter ±100 ms), accounting for the leader not having advertised yet if both devices rebooted simultaneously.
5. **Success**: once the leader is found, the follower enters sync mode and begins following.
6. **Exhaustion**: if discovery fails after all retries (e.g., both devices on different Wi-Fi networks, or group deleted), the follower stays idle. If the follower app is opened again, `GroupSyncViewModel` re-triggers discovery as a final safety net.

### Boot timing scenarios

Timing below is measured from app launch (post-reboot) or service restart (process killed
while running) — not from the device's own boot time, since the app has no way to run
earlier than that.

- **Leader's app opens first**: follower discovers it on first or second attempt.
- **Follower's app opens first**: follower retries until leader advertises (within ~30 seconds of the follower's restoration start).
- **Both open around the same time**: exponential backoff with jitter prevents thundering herd; most followers reconnect within 10–15 seconds.
- **Wi-Fi disconnected at restoration start**: restoration retries continue; once Wi-Fi reconnects, discovery succeeds on the next attempt.

## Edge Cases

- Leader pauses its route/roaming/walk → broadcasting to followers continues (frozen position, refreshed each tick) instead of going stale. `MockLocationService.observeLocationState` keeps the update loop alive on `PAUSED` when `leaderSharingEnabled` is true.
- Joining while leading → leader exits first, then joins as follower.
- Network unavailable → follower's boot restoration fails, but retries are queued. Once network is available, restoration succeeds. UI shows last known role until sync resumes.
- QR regeneration: leader can regenerate QR (new port/session) without changing the group code.
- Code discovery timeout (10 s) → error snackbar shown, user can retry.
- NSD registration failure → logged; QR still works as fallback.
- **Group no longer exists**: `FollowerSyncClient` detects this two ways — an immediate `403 Forbidden` from `/position` (token rejected, e.g. leader restarted with a new group) or `AppConstants.SyncConstants.MAX_CONSECUTIVE_POLL_FAILURES` (15) consecutive request failures (leader server unreachable/torn down). The higher threshold (raised from 5) gives a brief Wi-Fi reassociation or Doze-deferred network access on the background service enough real-world time to recover before this fires. Either condition fires `onGroupLost`, which — before actually leaving — retries NSD re-discovery up to `AppConstants.SyncConstants.NSD_REDISCOVERY_RETRY_COUNT` (2) extra times (`MockLocationService.enterFollowerMode`'s `onGroupLost` callback) to recover from a leader that changed host/port (e.g. its own app restarted). Only once all retries are exhausted does it call `exitFollowerMode()` and `GroupRepository.leaveGroup()` to fully clear follower state automatically — no manual exit required.
