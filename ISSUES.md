# ISSUES.md

This document references known issues for agents to pick them and iterate on

## List

- Bug: the main map screen search button should be a floating button just like the "favorite" and "start spoof" button. the floating map screen button should have the exact same layout as the main map screen, the buttons should be floating, bottom right, with the 3 features available. Top right should just be a "close" button.

- Feature: when a route is ongoing, clicking on the map (either the main map screen or floating map view) opens the confirmation drawer to decide whether we should teleport to the point or walk here or do nothing. As we have an ongoing route, the choices should change, it should be: Stop route and teleport (self explanatory), Stop route and walk here (Stops the route, change target point to the user chosen point), Finish route and walk here (continues the route and add append the user selected point to the end of the GPX route (only in memory, not saved in the route)), Do nothing.

- Fix: when clicking on the map to walk to a point, the UI should treat it as an ongoing route. Meaning that: the map should display a line from starting point to target point, we should see the current location point moving on this line, and the floating widget route icon should be "green" as if a route was ongoing, we should be able to "pause" and "stop" that route.
