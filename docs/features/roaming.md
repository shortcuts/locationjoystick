# Roaming Mode

Set a center, radius, and duration. Walks randomly within the radius. Configured via bottom sheet on the Map screen.

Key files: `:core:routing/RoamingEngine.kt`, `:core:routing/OsrmClient.kt`, `:feature:settings:impl/SettingsScreen.kt`

## Modes

- **Simple** (straight-line): no network required.
- **Road-following** (OSRM routes): opt-in. On OSRM failure, falls back to straight-line automatically.

## Algorithm (Road-Following)

1. Pick a random destination within the radius using uniform disk distribution.
2. Fetch OSRM route to that destination.
3. Walk the route.
4. Repeat until duration elapsed.

## OSRM Configuration

Base URL, overview, geometries, and profile constants in `AppConstants.OsrmConstants` and `AppConstants.RoamingConstants`.

## Edge Cases

- Cache the last OSRM route — do not re-fetch while walking it.
- Radius/duration changes apply on the next waypoint pick.
- Persist start time in DataStore so roaming survives service restarts.
