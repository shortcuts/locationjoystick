# Roaming Mode

Automatic wandering, started from the roaming sheet on the map, the floating widget, or settings. Only one roaming session can run at a time. A walk to a tapped point stops roaming first. The walk starts only after the stop finishes, because the stop resets the mode and would otherwise erase the walk.

There are two modes. The roaming sheet shows one mode's settings at a time, with a toggle to switch. Settings shows both modes' defaults at once, because it pre-configures what either mode starts with.

- **Walk around the block:** a random walk inside a radius, for a set distance. It can run in a straight line or follow roads. This was the original roaming mode.
- **Planting:** a spiral out from the current point to an outer radius and back. One loop is one full expand and contract. The user can repeat it a set number of times, run it forever, or expand once and stop. It is straight-line only. It is unrelated to planting circles around route stops ([routes.md](routes.md)).

Defaults for both modes persist and are exported with the rest of the settings. The exact fields and their ranges are in the roaming configuration code. The code is the source of truth for them.

Key files: the routing module's roaming engine and OSRM client, the roaming repository, and the roaming sheet.

## Why the route is planned up front

The whole path is planned before walking starts. The map shows the complete wandering path at once, and the preview is the plan. Walking then follows the plan, so what the user sees is what runs.

If no preview exists at start, or the mode changed since the last preview, planning runs at start.

Two details matter:

- Walking starts toward the first point ahead of the start, not the start itself. Targeting the start point would make the first tick a zero-distance snap that wastes the tick's movement.
- Movement is shared with route replay, so dense road geometry gets the same full-tick movement budget.

## Planting spiral

The spiral's ring spacing is a whole number of metres, with a minimum of one. Each arm has at least two revolutions, so the loop closes. "Expand only" builds just the outbound arm and stops at the outer radius. Its loop settings are greyed out but kept.

## Walk-around planning

Straight-line planning generates all points up front, with an optional return to the centre at the end.

Road-following planning picks random points one at a time, asks the routing service for each leg, and adds up road distance until the budget is spent. A call budget caps the number of routing requests. If one leg fails, only that leg falls back to a straight line. Road legs already fetched are kept.

The routing requests always use the foot profile, never the speed profile's transport mode. A walking route is the only one that matches a walking-speed move. The speed profile sets the speed only.

## Routing reliability

Public routing servers have no service promise. They throttle, fail and time out, often in busy areas. The routing client is built to degrade rather than fail:

- Two backends, each with its own foot, bike and car routes. The primary has separate graphs for each profile. The secondary has one graph for all, so it cannot tell profiles apart.
- A ladder of attempts across backends and profiles, with short backoffs. A route found by a later rung may be shaped for a different profile. That was accepted over a straight line.
- A waypoint with no nearby road is snapped to the nearest road and retried once.
- Failures are classified by type, not by parsing messages. A rate-limit answer uses the server's own retry time when present.
- A hard time budget bounds the whole ladder, and each attempt has its own timeout. A slow server counts as down.
- A long leg that still fails is split at its midpoint and each half is resolved on its own, in parallel, up to a depth and time limit.
- Successful results are cached in memory for a short period. Degraded results are never cached.
- A backend that rate-limits or errors is cooled down for a while, and the cooldown survives an app restart. This stops the app from hammering a server that asked it to stop.

The user is told when routing fails. A walk falls back to a straight line and says so. A planned route reports one summary of how many legs used straight lines, rather than failing silently.

Roaming legs are short enough that they never need the split. They use their own per-leg straight-line fallback.

## State

- The roaming repository holds the running and paused state.
- The roaming engine owns the session. Starting a new session waits for the old one to stop, so two sessions never move the position at once.
- A natural finish resets the mode and clears the route. A stop by the user does not show a completion badge ([widget.md](widget.md)).
- The travel direction is reported as the bearing between consecutive ticks, the same as route replay. Speed is reported too, so mock fixes carry real motion.

## Speed

Walk-around starts at the walk profile. Planting starts at the bike profile. The two are independent, and each has its own row in settings and the roaming sheet.

Starting a roam sets the global speed to the mode's profile, so the widget icon matches. After that, changes made by the user during the session win. They are not written back into the roaming defaults.

## Movement priority

Only one movement engine writes the position at a time.

- Route replay blocks roam start. A paused route is stopped first, so both engines never tick together.
- Starting a saved route stops roaming.
- Moving the joystick pauses roaming and keeps the session. Releasing the stick leaves it paused until the user resumes. See [joystick.md](joystick.md).
- While roaming is paused, a retained stick direction steers. It does not restart the roam by itself.

The priority rules are in the shared movement-priority code. Read them there.

## Guided path

On the path that hides planting, planting is hidden. Roaming then always starts a walk-around. Stored planting values and a running planting session are untouched. See [guided-paths.md](guided-paths.md).
