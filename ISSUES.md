# Known Issues & Backlog

## Documentation Outdated Items

No outstanding documentation issues.

---

## Bugs

- "Walk here via roads" doesn't work, it works in a straight line from point A to B. It should invoke OSRM roads.
- "Add next point" from the MapScreen or FloatingMapView should be "infinite", meaning that we could keep appending points to the ongoing "walk to" route if we want. right now we can only do it once.

---

## Frontend UI/UX

Invoke the /frontend-design skill for each of the below task:
- "Walk" "Run" and "Bike" profile speed button of the "Roaming" drawer bottom sheet doesn't respect the design system of the other buttons.
- When choosing a "Walk here" from the MapScreen or FloatingMapView, it should have a similar "expandable" layout as the "roaming" or "routes" icon to "pause" and "stop" the route. 
- FavoritesList should show the cooldown duration in the same manner as the MapBottomSheets. Anything that provides "teleport" should show it.
- The "play" icon from the routes list should be next to the three dot to be consistent with how we display a play button in e.g. the bottom drawer sheet. 
