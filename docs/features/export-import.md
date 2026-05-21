# Export / Import

Settings → export all data to JSON, import from a previous export.

Key files: `:feature:settings:impl/SettingsScreen.kt`, `:core:data/SettingsRepository.kt`

## Scope

Covers: routes, favorites, speed profiles, widget config, roaming defaults, jitter settings.

Schema version: `AppConstants.ExportConstants.SCHEMA_VERSION`.

## Export Flow

1. Serialize via `kotlinx.serialization`.
2. Write to `getExternalFilesDir(null)`.
3. Share via `FileProvider` + `Intent.ACTION_SEND`.

## Import Flow

1. File picker (`OpenDocument`, MIME `AppConstants.ExportConstants.MIME_TYPE`).
2. Parse + validate `schemaVersion == AppConstants.ExportConstants.SCHEMA_VERSION`.
3. Confirm "replace all data?".
4. Clear Room + DataStore.
5. Insert new data.

All I/O runs on `Dispatchers.IO`.

## Edge Cases

- Malformed JSON → show "Invalid file" error.
- Missing fields → use `@SerialName` defaults.
- Skip confirmation on fresh install (empty DB).
