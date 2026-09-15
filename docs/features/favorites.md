# Favorite Locations

Save named locations. Tap from list to instantly teleport spoofed position. A teleport stops any walk, roam, or route session first (`TeleportUseCase.execute`). Rename and delete supported.

Key files: `:feature:favorites:impl/FavoritesScreen.kt`, `:feature:favorites:impl/FavoritesViewModel.kt`, `:core:database/FavoriteDao.kt`

## Search

A search field at the top of the Favorites screen filters the list by name or category
(case-insensitive substring match via `matchesNameSearch` / `FavoriteLocation.matchesSearch`),
updating live as the user types. Hidden when there are no favorites at all. If the query matches
nothing, a "No favorites match your search" placeholder is shown instead of the list. Query state
is `remember` (not `rememberSaveable`) so leaving the screen starts from the full list.

The same filter is on every other favorites picker:

| Surface | UI |
|---|---|
| Map favorites sheet | Search field under the sheet title (`FavoritesList`, `enableSearch = true`) |
| Route creator "Jump to Favorite" | Same embedded field |
| Widget favorites panel | Share current location left of Search, then Close. Search icon reveals the field. `FloatingPickerShell` owns the query and passes it as `filterQuery` (`enableSearch = false` so the list does not draw a second field). Closing the panel (or hiding search) clears the query — `WidgetPanelPresenter.showPanel()` builds a fresh `ComposeView` on every open. |

The widget favorites panel uses `mapPanelLayoutParams()` so the overlay is focusable and the
search field can take IME input (same reason as the map/paste panels).

The Sort menu is available on the main Favorites screen, widget favorites panel, and the floating
map's favorites panel. It offers **A–Z**, **Z–A**, **Newest saved**, and **Oldest saved**. The
selection persists and is shared across those surfaces.

## Categories

`FavoriteLocation.category` is an optional label (`String?`, `null` for most user-added favorites). When set, the Favorites list groups entries under a header for their category; uncategorized favorites are shown last, without a header. Hot locations get their category populated automatically (see below) — user-added favorites have no category unless imported from a source that sets one.

## Add Flows

Four ways to add a favorite, from the add FAB action sheet:

1. **From map**: navigate to `MapPickerScreen` where user taps map or uses Nominatim search, enters name, then confirms. `MapPickerScreen` calls back with `(name, lat, lon)`.
2. **From coordinates**: inline dialog with name, lat, lon fields. Save is disabled until the name is non-empty and lat/lon parse as a valid pair (`isValidLatLng`).
3. **Paste coordinates**: same dialog layout with a name field plus a single paste box for
   decimal-degree `lat, lon` (example `11.0127769, 79.48065`) or DMS
   (`37°34'11.4"N 127°00'17.9"E`). Uses `parsePastedCoordinates`
   (`:core:common`) — the same messy-text parser as route paste — and saves the first valid pair.
   Invalid text shows "No valid coordinates" and does not save.
4. **Use current location**: pre-fills lat/lon from the current spoofed position.
4. **From the map paste sheet**: the map FAB's paste-coordinates sheet can save the parsed point as a favorite after prompting for a name (same parser, first valid pair).

## Share

The overflow menu on each favorite is **Edit**, **Copy coordinates**, **Send as message**, **Delete**.
Copy and send use the same decimal-degree `lat, lon` text as paste (`formatCapturedPoint`). They do
not generate a `locationjoystick.shrtcts.fr` shortcut URL.

The Favorites screen toolbar is Sort, **Share current location**, Add (`+`). Share sits immediately
left of Add. The widget favorites picker has the same share action in the title row, immediately
left of Search (hidden on a favorite's detail). Both open the system share sheet
(`Intent.ACTION_SEND` / `text/plain`) with `formatCapturedPoint` of the current spoofed (or last)
position — not the favorite list. If there is no current position, a toast says "No current
location" and the picker stays open. After a successful share from the widget (or floating-map)
picker, the overlay closes first so the system share sheet is not covered (`onShareOpened`,
default `onDismiss` / `hidePanelView()`). The widget overlay is not an Activity, so the chooser
is started with `FLAG_ACTIVITY_NEW_TASK` (`shareCurrentLocationCoordinates` in `:core:common`).
Favorite detail in both the widget picker and its floating-map picker also supports Rename and
Delete. Their dialogs stay inside the focusable overlay and return cleanly to the list after delete.

## Storage

`FavoriteEntity` flat table (no relations). Sort by `createdAt` descending by default; name and
saved-time sort modes are applied above the repository.

## Teleport

Set position directly, push one update, camera jumps to new position. Goes through
`TeleportUseCase.execute`, which stops any walk, roam, or route session first.

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
