# Known Issues & Backlog

---

## Bug

- Last location should be persisted even when spoof isn't enabled, as the user could potentially: enable spoof -> set japan -> disable spoof -> set paris -- but the cooldown still applies. the last location should never be null as long as the user have set it once.
- "Show hot location" should also be added to the export settings. any settings should be saved and exported.
- The Share feature should support opening links from https://dex100c0der.github.io/android-ios-support/coorward.html if possible
- From the floating widget, entering the map menu, then clicking on the map, a "walk to via roads" should be in the bottom drawer sheet, it is missing. It is however visible from the MapScreen

---

## Documentation Outdated Items

---

## Frontend UI/UX

- Selecting a favorite (e.g. being in Paris and selecting a favorite in Japan) properly focuses the new target location but the zoom is way too far.
- Favorite list should display the cooldown warning when applicable.
- When a route is traced on the floatingmapview, we can't see it from the mapscreen, the logic of the two (similar with favoriteview/favoritescreen) should be the exact same and fully shared, it's only how we surface it to the user that changes.

---

## Wiki

