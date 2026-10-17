# Export / Import

Settings exports all user data to a JSON file, and imports a file from a previous export.

Key files: the settings feature module, the settings repository, and the export codec in `:core:data`.

## Scope

The export holds routes, favorites, speed profiles, the widget and map feature choices and their shared order, roaming defaults, jitter settings, the planting radius, the hot-list state, sort choices and the general settings. The exact field list is the export data model in `:core:model`. It is the source of truth; this file records only the rules.

Rules for what is and is not exported:

- **Per-device, never exported:** the accessibility disclosure answers, the tap-to-walk ground calibration, the control-API key, the last remembered location, the capture on/off state and its list, the launch-after-link app, and update and what's-new bookkeeping.
  - Calibration is per device because it depends on that phone's screen and camera. A map from another phone would give wrong taps.
  - Consent answers are per device because a restored backup must not carry consent to a new phone.
  - The API key is a secret.
- **Feature choices are exported.** A restored backup brings its own button set, and the import marks the defaults as seen so the upgrade merge does not undo the choice.

## Compatibility rules

Every new field must import from an older file that lacks it. An old file imports with a documented default, and an unknown value falls back to the default rather than failing the import. Adding a field is a schema decision, so a new field needs a default, a lenient parse, and a test with an old file.

Specific rules that have bitten us:

- A file that lists the feature set without the newer buttons keeps that set. The import marks them as seen, so the upgrade merge does not add them back.
- An install that never imported such a file still gains new default-on buttons through the upgrade merge.
- The planting defaults for roaming apply to files that lack them.
- Old sort choices (two booleans) migrate to the matching new sort mode. The booleans remain in new exports for older builds.
- A Home favorite id is written only when set. Import with replace resets Home to the file's value. Import with merge only sets it when the file has one. A Home id whose favorite is missing is cleared.
- Route waypoint wait times default to zero when absent.

## Export and import flow

Export serialises to JSON and shares the file. The QR-code export and import are separate features ([qr-transfer.md](qr-transfer.md)).

Import reads the file, checks the schema version is within the supported range, asks for confirmation, then replaces the stored data. All file and database work runs off the main thread. A file that is malformed, too large, or has the wrong schema shows an error and changes nothing.

## GPX import (routes only)

Routes can be imported from GPX files.

- Every track and route element becomes its own route. Elements are never merged, even if unnamed. Elements with no name take a numbered default.
- A file with no track or route elements but with bare waypoints imports those waypoints as one route.
- A file with no points at all shows an import error, rather than an empty route.
- Routes over the waypoint limit are skipped, and the user is told how many.

The parser is shared with opening a GPX file from another app. That path shows the paste sheet rather than saving at once ([routes.md](routes.md)).

## Third-party imports

Two other apps can be imported:

- A GPS Joystick export: routes and favorites. Its list order is kept. Its routes become straight routes.
- A YAMLA export: favorites and the walk, run and bike speeds. Its routes are not imported, by design. The speeds are the only part the app can map cleanly.

## Reset all data

Reset clears all routes, favorites and settings, and keeps only the onboarding-complete flag. A user who resets should not need to re-grant system permissions, only re-enter their data. The action is irreversible, and the confirmation says so.
