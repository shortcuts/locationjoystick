# Route System

Waypoints on the map form a polyline. Save, edit, replay, loop, and record real-time routes.

Key files: the routes feature module (screens, view models), `:core:common/util/GpxRoutes.kt`, `:core:data/GpxOpenRepository.kt`, `:core:database/RouteDao.kt`, `:core:routing/RouteReplayEngine.kt`.

## Route types

- **Straight:** straight segments, no network.
- **Guided:** road-following through the routing service. On failure the creator shows an error. There is no silent fallback to straight lines, because the user asked for roads.
- **Teleport:** instant jumps between waypoints, waiting at each. See "Teleport routes".

## Storage

A route is stored as one row with its waypoints in a child table. Waypoints keep their order index. The app reads a route as one observable value so screens update when it changes.

Routes can also come from GPX files. The sizes and point limits are constants in the code. Points beyond the limit are dropped, and the user is told how many.

Opening a GPX file from another app is supported. Chat apps often send GPX as a generic binary type on a content link with no extension, so the app sniffs the content rather than trusting the type.

Why a pending GPX file waits in a buffer until the paste sheet consumes it: the map is torn down when the app stops, and an early read used to wipe a replay that a later open still needed. The file is consumed only when the user acts on it.

## Groups

Routes use the same grouping as favorites ([favorites.md](favorites.md)). Hot routes cannot be moved and always show as ungrouped. Updating a route over the Control API keeps its group.

## Search and sort

Search matches the route name. It is shared by the Routes screen, the map sheet and the widget panel, so the three stay consistent.

Sort order is shared too. The selected order also controls route ordering on the map and the widget. Saved-time sorts use the time the route was added, so the order is stable.

## Paste coordinates

Pasted text is cleaned into one saved route. Messy input is expected: numbered lists, emoji, degree-minute-second with compass letters, and optional swap of latitude and longitude. The parser is dedicated to this job. The single-pair search parser is separate on purpose, so the search box stays strict.

Unrecognized prose lines are ignored whole. Words on a line stop the decimal fallback from pulling unrelated numbers out of notes.

The paste form is shared between the Routes menu, the map and the widget, so all three accept the same input. Clipboard paste appends a line rather than replacing text, so a second copy from another app adds a point.

The screen stays alive when the user leaves the app to copy more coordinates. Other destinations are unloaded on stop to free the map; this one is exempt, as file pickers are.

### Build modes

- **As-is:** points as a straight polyline, saved as a straight route.
- **Walkable path:** road-following between consecutive points, saved as a guided route. A failed leg falls back to a straight segment, and the user gets one summary.
- **Teleport:** jump to each point and wait there, saved as a teleport route.

Ordering is either kept as pasted or reordered by nearest neighbour. That reorders one route. It never splits the input into several routes.

### Planting in the paste builder

Planting is a switch that combines with any build mode, not a separate mode. Save stores the ordered centres plus a radius. Circles are not baked into the route. Replay expands them at start, so changing the default radius later still applies to saved routes that do not pin their own.

Planting is hidden on the path that hides it ([guided-paths.md](guided-paths.md)). A stored radius is kept, so a planted route still plants when started.

## Paste on map and widget

The map's add menu and the widget use the same form. They offer the same actions for one point and for many points:

- One valid point: teleport, walk, or walk via roads.
- Several valid points: route actions, and a start-route button.
- Saving a favorite uses the first point. Saving a route needs at least two points.

### Play without saving

Start route saves the pasted points into one reserved temporary route, then replays it. The next paste overwrites that temporary route only.

Two rules keep this safe:

- The reserved id must not look like a hot route, and must not be matched by name. Otherwise a name collision would overwrite a real route.
- A route the user saved is never overwritten by a paste, even if the names match.

Replay of the temporary route is a route replay, not the walk-and-add-point ephemeral mode. It takes the same options as any saved route. With teleport-between-waypoints off, Start still walks to the first point when hide-teleport is on.

## Replay

- Points are interpolated at the chosen speed.
- Bearing is recomputed from the previous to the next interpolated position, so it follows the path, including road-following and planting circles.
- Each tick consumes the whole distance budget, even across several close waypoints. Carrying leftover distance one segment at a time dropped the remainder and lost progress on dense routes.
- A waypoint counts as reached inside the arrival threshold.
- Loops interpolate smoothly from the last waypoint back to the first.
- Pause keeps pushing the frozen position on every tick. Stopping ticks would make the mock fix go stale, and other apps would fall back to the real GPS until the replay resumed.
- Route replay takes precedence over roaming. Starting roaming during a replay is a no-op. A paused route is stopped before roaming starts, so the two engines never write at the same time.
- Moving the joystick pauses the route and keeps its progress. Releasing the stick leaves it paused, so the user decides when to resume. See [joystick.md](joystick.md).

### Progress

While replay is active, a `current / total` chip shows the last named stop reached. Only named stops count. Road vertices and planting vertices do not, so the number matches what the user sees on the route.

### Speed profile per route

A route may pin a speed profile. The pin only sets the starting speed. The user can change speed mid-replay, and the pin does not lock the session. A teleport route ignores the speed profile, because nothing moves at a speed.

### Next and previous waypoint

While a named route replay is active, previous and next buttons jump the position to the adjacent named stop. They are gated by two switches: hide-teleport must be off, and a separate opt-in must be on. The opt-in is off by default, so the buttons never appear unasked.

Jumps snap to named stops, not to road or planting vertices. Otherwise one tap would skip two named stops, and "previous" would look like a no-op.

## Start flow

Starting a route opens a sheet with options and a Start button. Options:

- **Loop:** on by default. Exclusive with return to location.
- **Planting:** each saved point is replaced by a circle around it, and replay walks the circles. Planting always loops. Planting forces loop on and return off.
- **Reverse:** replays the points in reverse order.
- **Return to location:** goes back to where replay started. Exclusive with loop.
- **Follow roads:** road-following between the route's own points. Per-leg failures fall back to straight lines for that leg only, with one summary message.
- **Teleport between waypoints:** hop to each named stop instead of walking, with a linger time at each stop. Not stored on the route, so it is a per-start choice.
- **Teleport:** jump to the first point without starting replay. The sheet stays open.

The sheet opens fully expanded. The Control API starts routes with hide-teleport bypassed, so teleport-between-waypoints still applies there ([group-sync.md](group-sync.md)).

### Interactions that must hold

- Follow roads combined with planting applies only to the connectors between circles. The circles stay geometric.
- Follow roads combined with teleport between waypoints is skipped for legs between stops, because the hop replaces them. It still applies to the walk to the start when hide-teleport is on.
- A user teleport during route planning cancels the pending start. Otherwise a late routing result could snap the position back to the route.
- A route that pins its own planting radius always plants at that radius, and the sheet shows planting as locked on. A pinned guided route shows follow roads locked on too. Unplanting is a route-detail action, not a per-start choice.
- Randomise order is ignored on a planted teleport route.
- A route that holds an OSRM-resolved path shows that path on the map during replay, not the saved straight line. The saved shape returns after the session ends.

## Teleport routes

A teleport route replays with a separate engine, because nothing moves between stops. Each stop has a wait time. Its position is frozen there and still pushed to the mock provider on every tick, so the fix never goes stale.

- **Placing a stop:** a modal asks for the wait time before the point is added. The route detail screen can edit each stop's wait later, or set all of them at once. Large routes need the bulk option.
- **Randomise order:** a per-route setting, saved with the route. The engine shuffles once at start and again on each loop restart, never mid-loop. Loop is still controlled by the start sheet, not by this setting.
- **Jumping while waiting:** a jump resets the wait at the landed stop, from either direction.
- **Speed:** a teleport route's speed is never read. The speed cycle control shows zero while a teleport replay runs.
- **No road-following:** the option is hidden for teleport routes, because there is no walking to route.

## Recording

Location is sampled at the normal update interval. Points are simplified (Ramer-Douglas-Peucker) and saved when recording stops.

## Hot routes

Settings has a toggle to show curated hot routes. When on, the app inserts the list into the route database. When off, it removes only the routes it inserted.

- Identity is the name plus city. Keep both stable, or users lose their selections.
- Inserted routes use an id prefix so they can be told apart from user routes.
- Upsert also deletes hot routes that were removed upstream, so the list can change without an app update.
- Export and import carry the toggle and the selection. Importing a backup with the toggle on re-applies the upsert.

The list is the wiki's `hot/routes.json`, fetched and cached the same way as hot locations ([favorites.md](favorites.md)). A body is rejected whole if its schema version is wrong or any entry is malformed. Waypoints are plain coordinate pairs in the file.

## Edge cases

- Fewer than two waypoints: replay is disabled.
- Resume after restart: the waypoint index is persisted so a restart can continue.

## API

Routes can be listed, created, replaced and deleted over the leader Control API. See [group-sync.md](group-sync.md), "Content API".
