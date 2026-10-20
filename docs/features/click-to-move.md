# Click-to-Move / Teleport

Long-press the map to open a sheet with "Walk here" or "Teleport here". The sheet's title row has a share action for the tapped point ([location-links.md](location-links.md)).

Tapping the map while spoofing, and opening a deep link, both pin a point and show this same sheet. The paste sheet does not pin. Its walk and teleport buttons act directly on the first valid point. Extra points do not change that. A future "open in Maps" intent should pin a point the same way as a deep link.

With two or more valid points, the sheet can also save a route, or start a named route replay from the reserved temporary route. Start is real route replay, not the ephemeral "add next point" mode. See [routes.md](routes.md). Teleport is hidden when hide-teleport is on. Start still walks to the first stop.

Key files: the map view model, the ephemeral replay controller, and the walk coordinator.

## Walk here

The walk heads straight for the target at the current walking speed, and snaps to it once inside the arrival threshold.

## Teleport

A teleport sets the position and pushes one fix at once, without waiting for the one-second loop. It needs no network.

Rules that must hold:

- A user-initiated teleport stops any walk, roam or route first. Otherwise the engine would overwrite the new position on its next tick.
- The stop covers a route that is still planning its road path. The mode has not yet changed to route replay while the routing service is answering, so the stop must not depend on the mode. The pending start is cancelled before the teleport applies.
- Teleports from favorites, the map, pasted coordinates and captured links all share this path.
- Starting a route in teleport-between-waypoints mode teleports to the first stop without stopping the replay that follows. Otherwise Start would cancel itself.
- Route-replay hops (teleport routes, next and previous stop, teleport between waypoints, resume hop) also restart the cooldown clock. The replay tick treats any step longer than one tick of walking plus the snap threshold as a teleport, because these hops bypass the shared teleport path.
- Cancelling a walk is race-free across threads. Each walk carries a generation token, and every start and cancel bumps it. A tick that was already running when a teleport landed is dropped, and so is a routing answer that arrives after a teleport from any screen. Without this, the position would walk back toward the old target.
- A parked widget (paused from the widget, mock location idle) does not resume on teleport. Teleport is not a substitute for Start. The feature buttons stay faded until the user starts.

## Walk via roads

The walk fetches a road route from the current position to the target, then walks it. If routing fails, the walk falls back to a straight line and says so. The routing fallback ladder is in [roaming.md](roaming.md), "Routing reliability".

## Add next point (ephemeral replay)

While a walk is active, the user can add the next point, either straight or via roads, without saving a route. Each tap chooses its own road setting for its own leg. The setting is not inherited from the walk or from earlier points, because inheriting it mixed up road and straight legs across taps.

The ephemeral replay controller owns this transition. The map and the widget both use it, so the logic lives once.

- The first tap cancels the walk and starts an ephemeral replay from the current position, through the rest of the walk, to the new point. The rest of a road walk keeps its road shape and is not fetched again. Only the new leg is routed, and only if that tap asked for roads.
- Later taps append to the live route, each with its own road choice.
- With no active walk, the tap does nothing.

If a road leg falls back to a straight line, the user is told.

## Edge cases

- A new walk cancels the previous one.
- Moving the joystick pauses a walk and keeps its target, including during a road lookup. Releasing the stick does not resume it. Resume continues from the moved position. A new destination replaces a paused walk.
- A cancelled walk must not clear the target of a newer walk, even when both have the same coordinates. The check and the clear happen under one lock.
- Walking here while a route plays asks the user to stop the route first.
- Add next point while roaming does nothing. It is valid only during a walk or an ephemeral replay.
