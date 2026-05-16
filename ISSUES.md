# ISSUES.md

This document references known issues for agents to pick them and iterate on

## List

- Feature: when a route is ongoing, clicking on the map (either the main map screen or floating map view) opens the confirmation drawer to decide whether we should teleport to the point or walk here or do nothing. As we have an ongoing route, the choices should change, it should be: Stop route and teleport (self explanatory), Stop route and walk here (Stops the route, change target point to the user chosen point), Finish route and walk here (continues the route and add append the user selected point to the end of the GPX route (only in memory, not saved in the route)), Do nothing.

- Fix: when clicking on the map to walk to a point, the UI should treat it as an ongoing route. Meaning that: the map should display a line from starting point to target point, we should see the current location point moving on this line, and the floating widget route icon should be "green" as if a route was ongoing, we should be able to "pause" and "stop" that route.
