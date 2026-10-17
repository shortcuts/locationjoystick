# Domain Models

The models live in `:core:model`, which is pure Kotlin: no Android, no Room. The code is the source of truth for fields, enums and defaults. This file records only the context a reader cannot get from the code.

## Context

- `RoamingDefaults.plantingSpeedProfileId` is independent of the walk-around `speedProfileId`. Planting usually runs at bike speed while walk-around runs at walking speed, so one value cannot serve both.
- `GuidedPath` flags drive UI visibility only. They do not change behaviour. `fromName()` is lenient and falls back to the default path, so an unknown value from an older backup or a link never crashes.
- `GeocodingProviderId.fromName()` is lenient for the same reason: a removed provider name must not break settings.
- `StoredGroundCalibration` is per device. It is deliberately not in `ExportData`: a calibration depends on the phone's screen and camera, so a copy made on another device would be wrong.
- `GroundMap`, `FeetDetector`, `FrameFlow`, `PhaseCorrelation` and `GroundMapFit` are pure-Kotlin ports of the calibration bench. They live in `:core:model` so they can be tested without a device.
- `ExportData` is the backup contract. Adding a field there is a schema decision, not a routine model change; see [features/export-import.md](features/export-import.md).

## Mapping

`:core:database` Room entities mirror domain models. Conversion goes through extensions in the repository layer, never in ViewModels.
