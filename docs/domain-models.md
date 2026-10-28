# Domain Models

The models live in `:core:model`, which is pure Kotlin: no Android, no Room. The code is the source of truth for fields, enums and defaults. This file records only the context a reader cannot get from the code.

## Context

- `RoamingDefaults.plantingSpeedProfileId` is independent of the walk-around `speedProfileId`. Planting usually runs at bike speed while walk-around runs at walking speed, so one value cannot serve both.
- `GuidedPath` flags drive UI visibility only. They do not change behaviour. `fromName()` is lenient and falls back to the default path, so an unknown value from an older backup or a link never crashes.
- `GeocodingProviderId.fromName()` is lenient for the same reason: a removed provider name must not break settings.
- `StoredGroundCalibration` is per device. It is deliberately not in `ExportData`: the fit depends on the phone's screen and camera, so a copy made on another device would be wrong.
- `GroundMap`, `GroundFlowFit` and `PhaseCorrelation` are pure-Kotlin fit code in `:core:model`, so they can be tested without a device. `AutoFitCoordinator` in `:core:location` runs the automatic fit during a walk.
- `ExportData` is the backup contract. Adding a field there is a schema decision, not a routine model change; see [features/export-import.md](features/export-import.md).
- `CalibrationWalkMath` holds the flat-earth ground math for tap to walk. Its name is historical. The dedicated calibration walk was dropped, and `AutoFitCoordinator` still uses this object to fit the ground map from ordinary walks.
- The Room schema went from 6 to 8 in two steps. Schema 7 adds `routes.category`. Schema 8 adds `routes.plantingRadiusMeters`. Both columns are nullable, so existing rows read as null and need no backfill. Migrations live in `LjDatabase`.
- A route pins its own planting radius in `routes.plantingRadiusMeters`. `null` means no pin: replay falls back to the default radius in settings. The column exists so a pinned route keeps its radius when the default changes.

## Mapping

`:core:database` Room entities mirror domain models. Conversion goes through extensions in the repository layer, never in ViewModels.
