# Group Sync

Sync spoofed location across multiple devices on the same Wi-Fi network. No account required.

Key files: `:feature:group:impl/GroupSyncScreen.kt`, `:feature:group:impl/GroupSyncViewModel.kt`, `:core:data/GroupRepository.kt`, `:core:location/GroupNsdManager.kt`

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

## Jitter

Followers apply independent per-device jitter to the received position, preserving the configured jitter profile without cloning the leader's exact coordinates.

## Edge Cases

- Joining while leading → leader exits first, then joins as follower.
- Network unavailable → follower silently disconnects; UI shows last known role.
- QR regeneration: leader can regenerate QR (new port/session) without changing the group code.
- Code discovery timeout (10 s) → error snackbar shown, user can retry.
- NSD registration failure → logged; QR still works as fallback.
- **Group no longer exists**: `FollowerSyncClient` detects this two ways — an immediate `403 Forbidden` from `/position` (token rejected, e.g. leader restarted with a new group) or `AppConstants.SyncConstants.MAX_CONSECUTIVE_POLL_FAILURES` (5) consecutive request failures (leader server unreachable/torn down). Either condition fires `onGroupLost`, which calls `exitFollowerMode()` and `GroupRepository.leaveGroup()` to fully clear follower state automatically — no manual exit required.
