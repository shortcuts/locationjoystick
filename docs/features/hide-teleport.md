# Hide Teleport Features

A single toggle that removes every teleport entry point in the app, leaving only walking and route replay as ways to move.

Key files: `:core:model/AppSettings.kt`, `:core:data/SettingsRepository.kt`, `:core:datastore/AppPreferencesDataSource.kt`, `:feature:settings:impl/SettingsMenusSubScreen.kt`, `:feature:map:impl/MapViewModel.kt`, `:feature:map:impl/MapBottomSheets.kt`, `:feature:map:impl/MapScreen.kt`, `:feature:map:impl/MapFabColumn.kt`, `:feature:favorites:impl/FavoritesViewModel.kt`, `:feature:routes:impl/RoutesViewModel.kt`, `:feature:routes:impl/RoutesScreen.kt`, `:feature:group:impl/GroupSyncViewModel.kt`, `:feature:group:impl/GroupSyncScreen.kt`, `:feature:widget:impl/FloatingWidgetService.kt`, `:feature:widget:impl/WidgetPanelPresenter.kt`, `:feature:widget:impl/WidgetPanelContent.kt`, `:feature:widget:impl/MapFloatingView.kt`

## Behaviour

- Setting: `AppSettings.hideTeleportFeatures` (`Boolean`, default `false`). Persisted via DataStore key `hide_teleport_features`, round-trips through `ExportData`.
- Toggle location: Settings → Menus → Privacy → "Hide teleport features".
- When enabled, every explicit teleport button, checkbox, or gesture is removed from its composable tree (not just disabled):
  - Map long-press bottom sheet: "Teleport here" and "Stop route and teleport" (both the main map sheet and the floating map's tap panel).
  - Favorites map-picker "Set location" button.
  - Favorites list row tap — the whole row's tap is the teleport action (see @docs/features/favorites.md); with the toggle on, tapping a row becomes a no-op instead of a visible-but-inert button.
  - The standalone **Teleport** button on the start-route sheet (map long-press
    sheet, Routes screen, and widget panel — three surfaces sharing
    `LjRouteStartOptions`) and the **Teleport between waypoints** checkbox on
    that same sheet and on paste-coordinates Start (`PasteCoordinatesForm` /
    `LjRouteStartCheckboxes`), including the compact seconds field on that row.
    **Start** also walks to the first waypoint instead
    of teleporting there, and teleport-between is forced off even if an intent
    extra is true.
  - Group Sync "Teleport to leader now" button (Group Sync screen), and the "Follow leader teleports" toggle (the follower always walks).
  - Widget favorites-panel "Teleport" button.
  - Paste-coordinates **Teleport** on the map FAB, floating map, and widget paste box (`PasteCoordinatesForm`). **Start** on that same form still walks to the first stop when hide-teleport is on (`StartRouteReplayUseCase`).
  - Route replay "Previous waypoint" / "Next waypoint" buttons (main map
    FAB column, widget panel, and floating map — see
    @docs/features/routes.md, "Next / Previous Waypoint (Teleport)").

## Idle-State Exception

`MapViewModel.handleTapToTeleport()` and `handleSelectFavorite()` each have an `else` branch reachable only when `mockLocationState == IDLE` (nothing is spoofing yet). These branches are **not** gated by the toggle — they are the only way to establish a starting position at all, so hiding them would strand a user with no way to begin. Every other teleport path is reachable only while already spoofing, or is unconditional (Favorites screen, Group Sync), and is gated.

## What Is Not Hidden

- The map screen's hint text changes from "Tap to teleport · Long-press to walk" to "Long-press to walk" when the toggle is on, since the tap gesture it describes is disabled. This is a copy change, not a removed feature.
- `IdleScreen.kt`'s Favorites card description ("Teleport or walk to saved locations.") and the Settings → Menus → App Features description for Favorites are left unchanged — walking still works, so the copy stays accurate enough, and making it reactive to this setting was out of scope.
