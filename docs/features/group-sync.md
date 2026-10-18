# Group Sync

Sync spoofed location across multiple devices on the same Wi-Fi network. No account required.

Key files: the group feature module (screen, view model), the group repository, and the NSD and follower code in `:core:location`.

## Roles

- **Leader:** hosts the group. Runs a local server on the Wi-Fi network and broadcasts its position when sharing is on.
- **Follower:** joins with a QR code or a group code. Mirrors the leader's spoofed location when follow mode is on.
- **None:** not in a group. The default.

## Group code

The group code is a short uppercase code that avoids easily confused characters. It is used for three things: the token the sync server checks, the name the leader advertises on the network, and the code shown on the leader screen. A code is not a secret; the Control API has its own key (see below).

## Joining

- **By QR:** the leader's QR carries the host, port and code, so no discovery is needed. Join works even where discovery fails.
- **By code:** the follower looks up the leader by its advertised name on the network, then joins with the resolved address.
- **Nearby leader hint:** while the idle screen is visible and started, the app looks for a leader on the network every ~30 s and shows a one-tap Join. It never joins on its own, because a shared Wi-Fi can hold a stranger's group. It polls only on screen (battery), shows the first leader found, and uses the same discovery as join by code, so hotspots that block multicast show nothing (see below).

Following starts on join. The follow switch is saved, but only for new joins: a join never overwrites a stored choice. Leaving resets the one-shot checks so a rejoin on the same screen starts following again.

## Why discovery is secondary

Network discovery depends on multicast. Many phone hotspots do not forward multicast between clients, so code-based join cannot find the leader there. QR join uses a unicast address and is the workaround. If the hotspot also isolates clients, no app change can fix it.

The leader's advertised address prefers the Wi-Fi or hotspot interface. A cellular address behind carrier NAT is unreachable from another phone, so it must never be put in the QR.

## State

Group state is one value: role, group code, leader address, and the flags for sharing and follow. Transitions go through the group repository. A leader that joins another group leaves its own group first.

All changes reach the spoofing service as intents. The service owns the server and the discovery; the UI does not start them directly.

## Follower catch-up

A follower walks toward the leader at the leader's reported speed. It does not snap to the leader's position, because a snap looks like a teleport to the game and to anyone watching.

Rules that must hold:

- The follower walks, never snaps, unless the leader teleported (below), the user asked for "teleport to leader now", or the follower is starting from nothing.
- Once the follower arrives, its heading is the leader's heading, not whatever direction its own walk last pointed. Two followers arriving from different paths would otherwise show different headings while nothing moves.
- Because the walk matches the leader's speed, the gap stays constant while the leader moves. A far-away follower takes a long time to catch up. This is intentional. There is no distance-based auto-teleport.
- If the follower was not already spoofing when it joined, the first position starts spoofing at the leader's coordinates. Nothing was being reported yet, so there is no anti-cheat risk. If it was already spoofing, the first position is walked to like any other.
- Followers apply their own jitter to the received position, so two followers never show identical coordinates.

"Teleport to leader now" is the only manual snap. It is a real teleport, so it shares the same cooldown clock as every other teleport in the app. If the leader's position is not yet known, the button says so instead of doing nothing.

A distance and cooldown hint sits next to the button. It is advisory: it shows how far away the leader is and how long to wait.

## Following leader teleports

A follower mirrors the leader's teleports, so a jump on the leader is not left as a long walk on the follower.

- The leader counts explicit teleports only. Walk arrivals and route hops do not count, so followers still walk to those. The follower never guesses a teleport from distance.
- A follower compares the counter with a change check, not an order check, because a leader restart resets it. The first value seen is a baseline, so joining or reconnecting never triggers a snap.
- A leader stopping, moving and starting again counts as a teleport. With the switch off, the follower restarts at its own position and walks.
- Hide-teleport features disable the switch, and the follower always walks.
- An old leader that does not send the counter is read as "no teleports". An old follower ignores the extra field.

## Boot and restore

There is no receiver for device boot. After a reboot, nothing runs until the user opens the app:

1. App launch: if the saved role is leader, or follower with follow on, the app starts the spoofing service with a plain launch.
2. Service restart after a process kill: the system restarts the service with no intent, and the same path runs.
3. The follower rediscovers the leader in the background, with retries and backoff. Both devices may restart together, so the leader might not yet be advertising.
4. If all retries fail, the follower stays idle. Opening the app triggers discovery again.

Retries use exponential backoff with jitter, so many followers do not all probe at once.

## Control API

The leader's sync server also serves a versioned control API under `/api/v1/`. It is the public contract for integrators. Its reference lives in the wiki (`docs/wiki/control-api.html`). **Any endpoint change must update that reference.**

Rules:

- Off by default. It turns on only while the server runs, and turns off when the server stops.
- A separate persistent API key is used, not the group code. It survives leaving a group and is never exported.
- Every command calls the same controller method as the UI. A follower then sees the same signals whether a human or the API drove the leader, so followers need no API-specific handling.
- Teleport and route start from the API bypass the hide-teleport setting for teleport-between-waypoints only. Plain teleport and walk never read that setting.
- A joystick request is a timed hold, not one step. HTTP cannot keep a finger down, so the server holds a stick position for the requested time, with the same release rules as the on-screen joystick.
- A teleport inside the suggested cooldown still runs. The response carries a warning instead of refusing. The cooldown is advisory because an integrator may know better.
- Transport is plain HTTP on the local network only. There is no TLS and no internet exposure.

Errors use one shape everywhere, so an integrator parses one format.

### Content API

The content routes give JSON create, read, update and delete for favorites, routes and speed profiles. Field names match the export format, so the backup format is the single reference. Built-in speed profiles can be read and have their speed changed, but not created or deleted.

Route replacement keeps the route's id and creation time, and is refused while that route is playing.

## Leader screen

The Control API switch sits under Sharing and is enabled only once the server's address is known. It shows the address, port and key with copy buttons, a key regeneration action (the old key stops working at once), and a warning that the link is plain and local. The switch resets when the group is left or the app restarts. The key persists.

## Edge cases

- The leader pauses a route, roam or walk: sharing continues with the frozen position, refreshed each tick, so followers do not go stale.
- The leader stops spoofing (not pause): each update carries an active flag. A follower that sees inactive parks itself without stopping its service, so the notification and polling stay alive. When the leader is active again, the follower bootstraps as on first connect. A leader's stop keeps its server alive, so the group survives.
- The follower moves its own stick: follow turns off and the stick steers from the current position. The device stays in the group. Follow turns back on by itself after a short idle period, and the follower walks back to the leader. The same applies to any manual departure from follow mode, such as a favorite teleport or a route. A stop of spoofing or leaving the group cancels the return.
- The group no longer exists: a rejected token, or a long run of failed polls, triggers rediscovery first, because the leader may have restarted on a new address. Only after the retries fail does the follower leave the group automatically.
- Discovery timeout: an error appears, and the user can retry.
- Registration failure: logged. The QR code still works.
