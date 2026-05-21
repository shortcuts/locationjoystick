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
