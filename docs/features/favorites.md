# Favorite Locations

Save named locations. Tap from list to instantly teleport spoofed position. Rename and delete supported.

Key files: `:feature:favorites:impl/FavoritesScreen.kt`, `:feature:favorites:impl/FavoritesViewModel.kt`, `:core:database/FavoriteDao.kt`

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

**Upsert rule**: match by name. If a favorite with the same name already exists, its coordinates are updated and its original ID is preserved. New entries get IDs prefixed with `hot_`.

**Remove rule**: delete all favorites whose ID starts with `hot_`. User favorites that happened to share a name with a hot location (and thus had their coords updated) are kept — their ID was never changed to `hot_`.

**Export/import**: `hotLocationsEnabled` field in `ExportData`. Importing a backup with it `true` re-applies the upsert.

The 26 locations live in `FavoriteRepository.HOT_LOCATIONS` as `List<Triple<String, Double, Double>>` (name, lat, lon).
