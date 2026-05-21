# Click-to-Move / Teleport

Long-press map → bottom sheet with "Walk here" or "Teleport here".

Key files: `:feature:map:impl/MapViewModel.kt`

## Walk Here

- Bearing computed from current position to target.
- Advances at `currentSpeed` m/s per tick.
- Snaps to target when within `AppConstants.LocationConstants.WALK_ARRIVAL_THRESHOLD_METERS`.

## Teleport

- Sets position directly.
- Pushes one update.

## Edge Cases

- New walk-here cancels the previous one.
- Walk-here while route replay is active → show confirmation dialog to stop replay before proceeding.
