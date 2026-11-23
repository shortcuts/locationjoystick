# Draw Line

The user drags a finger on the main map or the floating map. The path becomes a line that the app walks as is or saves as a route. There is no road following.

Key files: `DrawLineLayer` in `:core:map`, `DrawnLine.kt` in `:core:common`, the map's `MapAction.ToggleDrawLine` and the floating map's local state.

## Behaviour

- A map button, off by default (`AppFeature.DRAW_LINE`), toggles draw mode. It appears on both maps through the shared map feature set.
- On release, the points go to the paste sheet as text. The sheet already offers Start route (walk the line) and Save route, so no new walk or save path exists.
- Draw mode ends when the line finishes, when the button is tapped again, or when the map closes.

## Why an overlay and a toggle

- A drag on the map already pans it, and a long press already starts a walk. A gesture on the map itself would clash with both.
- A transparent layer above the map view takes the drag, so the map never sees the touches. Removing the layer ends draw mode in one state change. Switching MapLibre's scroll and zoom gestures off instead is global and easy to leave off after an error.
- The layer converts each screen point through the map projection and the tile source projection. It stores WGS-84 only ([map.md](map.md), "GCJ-02 boundary").
- The layer draws its own preview in screen space. The floating map resets its map sources on every update, so a map source would flicker.

## Point density

A raw touch stream gives hundreds of points per swipe. A point is kept only when it is far enough from the last kept point, then the line is simplified and capped at the GPX route limit. `DrawConstants` holds the values. A line under two points is dropped.

## State

`DrawLineState` (`Off`, `Armed`, `Drawing`) is the line lifecycle. The layer owns it for the drag. The main map keeps only the on/off flag in its UI state, and the floating map keeps it in a local `remember`, because draw mode must end when the window closes.
