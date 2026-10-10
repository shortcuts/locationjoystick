# Favorite Locations

Save named locations. Tap from list: while spoofing runs, a bottom sheet offers Teleport / Walk to location / Walk via roads (Teleport hidden when Hide Teleport is on); while spoofing is off, the tap teleports instantly. A teleport stops any walk, roam, or route session first (`TeleportUseCase.execute`). Rename and delete supported.

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

## Home

One favorite can be marked Home; the app then starts there instead of the last remembered location.

- A tappable house icon (`HomeToggleButton`, shared by `FavoritesList` and the Favorites screen) toggles Home on the Favorites screen, the map favorites sheet and the widget favorites panel. The Favorites screen overflow menu has the same "Set as home" / "Remove home" item. The route-creator "Jump to Favorite" list and the widget's floating-map picker show no icon.
- Only one Home. Marking another favorite moves the flag silently; tapping the active Home clears it.
- Stored as one DataStore value (`HOME_FAVORITE_ID`, see docs/features/last-location.md), not a column on `FavoriteEntity`. A Home id whose favorite no longer exists resolves to nothing, so deleting the Home favorite falls back to the last location. Turning hot locations off (or an import that drops the favorite) clears the id.
- Local startup preference: no Content API or Control API field. It round-trips through export/import (`ExportData.homeFavoriteId`).

## Storage

`FavoriteEntity` flat table (no relations). Sort by `createdAt` descending by default; name and
saved-time sort modes are applied above the repository.

## Teleport

Set position directly, push one update, camera jumps to new position. Goes through
`TeleportUseCase.execute`, which stops any walk, roam, or route session first.

On the Favorites screen, a row tap opens `FavoriteTargetDetail` in a bottom sheet while spoofing
runs (same choices as the map favorites sheet and widget panel). Walk goes through
`MapController.walkTo` / `walkViaRoads`. While spoofing is off the tap teleports directly.

## Shared ViewModel

`FavoritesViewModel` is shared across the favorites graph via `hiltViewModel(navController.getBackStackEntry("favorites_graph"))`.

## Hot Locations

Settings → Favorites → "Show hot locations" toggle (default off). When enabled, upserts the curated locations (see "Source of the list") into the favorites DB. When disabled, removes only the entries this feature inserted.

Key files: `:core:data/HotLocationsRepository.kt` (list source, cache, refresh), `:core:data/FavoriteRepository.kt` (upsert/remove logic), `docs/wiki/hot/locations.json` (the list), `:core:datastore/AppPreferencesDataSource.kt` (`hot_locations_enabled` key)

**Upsert rule**: match by name + city (via `idForLocation`). If a favorite with the same derived ID already exists, its coordinates and `category` are updated and its original ID is preserved. New entries get IDs prefixed with `hot_`. The upsert also deletes every `hot_*` favorite whose derived ID is no longer in the list, so a location removed upstream disappears without an app release.

**Remove rule**: delete all favorites whose ID starts with `hot_`. User favorites that happened to share a name with a hot location (and thus had their coords updated) are kept — their ID was never changed to `hot_`.

**Export/import**: `hotLocationsEnabled` field in `ExportData`. Importing a backup with it `true` re-applies the upsert. The per-favorite `category` field also round-trips as part of `favoriteLocations` — old exports without it import cleanly (missing field defaults to `null`).

**Categories**: each hot location entry carries `country` and `city` fields, used both for the grouped picker UI in Settings and — since this feature — for the `FavoriteLocation.category` field once added as a favorite (set to `country`, e.g. "Pago Pago" gets category "American Samoa").

### Source of the list

The list is `docs/wiki/hot/locations.json`, published with the wiki at `https://locationjoystick.shrtcts.fr/hot/locations.json`:

```json
{ "schema": 1, "locations": [ { "name": "", "lat": 0.0, "lon": 0.0, "country": "", "city": "" } ] }
```

- A body is accepted only if `schema` is `1`, `locations` is non-empty and every entry has all five fields; otherwise the whole body is rejected. Unknown extra fields are ignored. Publish an incompatible format under a new `schema` number: old apps keep their last good copy.
- Fetch/cache code is the shared `WikiJsonCache` (also used by hot routes, docs/features/routes.md).
- The same file is packed into the APK as the seed (`assets.srcDir` in `:core:data`). `HotLocationsRepository.locations` serves the cached copy (`filesDir/hot_locations.json`), else the seed.
- On app open (`LjApplication`), `refreshIfStale()` fetches silently when the cache is older than 24 h (file modified time as the clock; no toggle, no DataStore key). A failed check keeps the last good copy and also waits 24 h; with no cache it retries next launch.
- After a successful refresh, if "Show hot locations" is on, `upsertHotLocations` reconciles `hot_*` favorites to the new list. New entries are not auto-selected: they show unchecked in the Settings tree.
- Identity is `name` + `city` (`idForLocation`). Renaming either is a remove plus a new unchecked entry, so publishers should keep both stable to preserve users' selections.

**API**: favorites can be listed, created, updated and deleted over the leader Control API; see docs/features/group-sync.md, "Content API".
