# Favorite Locations

Save named locations and teleport or walk to them.

Tapping a favorite does the following. While spoofing runs, a sheet offers teleport, walk, and walk via roads. The teleport choice is hidden when hide-teleport is on. While spoofing is off, the tap teleports at once. Any teleport first stops a running walk, roam or route, so two sessions never write the position together. Rename and delete are supported.

Key files: the favorites feature module, its view model, and the favorites table in `:core:database`.

## Search

Search matches the name or the group name, case-insensitively, and updates as the user types. It is hidden when there are no favorites at all. The query is not saved, so leaving the screen starts from the full list.

Every favorites picker uses the same filter: the map sheet, the route creator's jump list, and the widget panel. The widget panel is a separate window, so it needs a focusable window for the keyboard to work, and it clears the query each time it closes.

## Sort

The sort menu is shared by the Favorites screen, the widget panel and the floating map's panel. The chosen order is kept and shared. Dragging a favorite or a group sets a manual order, and choosing a sort mode replaces it.

## Groups

A favorite's group is a plain name. No group is stored as a separate table: a group exists only while it has members.

- Create or move a favorite into a group from its menu. A field there creates a new group.
- Renaming a group renames it on every member. Deleting a group keeps its members and makes them ungrouped.
- Collapsing is not saved. An active search expands every group, so matches stay visible.
- Order: user groups first, then ungrouped favorites, then hot-list groups. Local places stay above the curated list. A user group may share a name with a hot-list country, and the two stay as separate sections.
- Drag a row or a group header to set your own order. Picking a sort mode replaces it.

Hot-list favorites are read-only and cannot be moved. Their group is their country.

## Add

There are four ways to add a favorite:

1. **From the map:** pick a point on the map or from a search result, then name it.
2. **From coordinates:** type a name and a latitude and longitude. Save is disabled until the pair is valid.
3. **Paste coordinates:** a name plus a paste box. The same lenient parser as route paste is used, and the first valid pair is saved.
4. **Current location:** fills the coordinates from the current spoofed position.

The map's paste sheet can also save its first point as a favorite.

## Share

Copy and send use the same decimal `lat, lon` text as paste. They do not make a link. A saved place has a name in the app, so a link would add nothing for the user.

The Favorites screen and the widget picker both have a share-current-location action, which shares the current position, not the list. With no current position, the user is told and the picker stays open. In the widget, the overlay closes before the system share sheet opens, so the sheet is not hidden behind it.

## Home

One favorite can be marked as Home. The app then starts there, instead of at the last remembered location.

- Only one Home. Marking another moves the mark. Tapping the active Home clears it.
- Home is stored as a single setting, not a column on the favorite. If the Home favorite is deleted, the setting resolves to nothing, and the app falls back to the last location.
- It is a local startup preference. It is not part of the Control API, but it does round-trip through export and import.

## Storage

Favorites are a flat table with no relations. Sorting by time is the default, and the other sorts are applied above the repository.

## Hot locations

Settings has a toggle to show a curated list of popular places. When on, the list is inserted into the favorites table. When off, only the inserted entries are removed.

- Inserted entries get an id prefix, so they can be told apart from the user's own favorites. A user's favorite with the same name keeps its own id and is never removed by turning the list off.
- Identity is the name plus city. Keep both stable upstream, or users lose their selections.
- The upsert also removes hot entries that were dropped from the list upstream, so the list can change without an app update.
- Export and import carry the toggle. A backup with it on re-applies the upsert.
- The category field round-trips, and an older export without it imports cleanly.

The list is the wiki's `hot/locations.json`. It is fetched and cached like the hot routes list ([routes.md](routes.md)). It is refreshed once a day when the app opens, and a failed check keeps the last good copy. A body is rejected whole if its schema version is wrong or any entry is malformed. A new incompatible format must get a new schema number, so old apps keep their last good copy instead of breaking.

New entries from a refresh are not switched on. The user chooses them in settings.

## Control API

Favorites can be listed, created, updated and deleted over the leader Control API. See [group-sync.md](group-sync.md), "Content API".
