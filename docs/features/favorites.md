# Favorite Locations

Save named locations. Tap from list to instantly teleport spoofed position. Rename and delete supported.

Key files: `:feature:favorites:impl/FavoritesScreen.kt`, `:feature:favorites:impl/FavoritesViewModel.kt`, `:core:database/FavoriteDao.kt`

## Search

A search field at the top of the Favorites screen filters the list by name (case-insensitive substring match), updating live as the user types. Hidden when there are no favorites at all. If the query matches nothing, a "No favorites match your search" placeholder is shown instead of the list.

## Categories

`FavoriteLocation.category` is an optional label (`String?`, `null` for most user-added favorites). When set, the Favorites list groups entries under a header for their category; uncategorized favorites are shown last, without a header. Hot locations get their category populated automatically (see below) — user-added favorites have no category unless imported from a source that sets one.

## Add Flows

Two ways to add a favorite:

1. **From coordinates**: inline dialog with name, lat, lon fields.
2. **From map**: navigate to `MapPickerScreen` where user taps map or uses Nominatim search, enters name, then confirms. `MapPickerScreen` calls back with `(name, lat, lon)`.

## Storage

`FavoriteEntity` flat table (no relations). Sort by `createdAt` desc by default; optional alpha sort.

## Teleport

Set position directly, push one update, camera jumps to new position.

## Shared ViewModel

`FavoritesViewModel` is shared across the favorites graph via `hiltViewModel(navController.getBackStackEntry("favorites_graph"))`.

## Hot Locations

Settings → Favorites → "Show hot locations" toggle (default off). When enabled, upserts 26 curated locations into the favorites DB. When disabled, removes only the entries this feature inserted.

Key files: `:core:data/FavoriteRepository.kt` (list + upsert/remove logic), `:core:datastore/AppPreferencesDataSource.kt` (`hot_locations_enabled` key)

**Upsert rule**: match by name + city (via `idForLocation`). If a favorite with the same derived ID already exists, its coordinates and `category` are updated and its original ID is preserved. New entries get IDs prefixed with `hot_`.

**Remove rule**: delete all favorites whose ID starts with `hot_`. User favorites that happened to share a name with a hot location (and thus had their coords updated) are kept — their ID was never changed to `hot_`.

**Export/import**: `hotLocationsEnabled` field in `ExportData`. Importing a backup with it `true` re-applies the upsert. The per-favorite `category` field also round-trips as part of `favoriteLocations` — old exports without it import cleanly (missing field defaults to `null`).

**Categories**: each `HotLocation` entry carries `country` and `city` fields, used both for the grouped picker UI in Settings and — since this feature — for the `FavoriteLocation.category` field once added as a favorite (set to `country`, e.g. "Pago Pago" gets category "American Samoa").

The 62 locations live in `FavoriteRepository.HOT_LOCATIONS` as a `List<HotLocation>` (`name`, `lat`, `lon`, `country`, `city`).
