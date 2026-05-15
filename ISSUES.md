# ISSUES.md

This document references known issues for agents to pick them and iterate on

## List

- App UX: Switching from screen is not ideal, it's not smooth, let's revise the plan at .opencode/plans/navigation_transitions.md

- DX: Codebase naming and consistency is bad. Iterate over plan .opencode/plans/naming.md, search for other missing renames.

- Map UX: Playing a route, or going from the current location to an other (e.g. after clicking the map and chosing "Walk"), the route should be traced in the map, in order to give better feedback to the user.

- Bug: Jitter seems to move when idled, even when at 0. The Jitter exists to make sure the mock location feels natural for app that we will spoof GPS coordinates, so it should only be when moving (walking from a point to an other), unless defined by the user in the settings that they want idled jitter. Also the jitter should feel as minimal, we don't want to move in a radius of 30 meters frenetically. it should be every N seconds move from what the user defined in the settings. we can also make that second a setting, and default it to 3. GPS satellites are not accurate to the meter, so we should mimic this.
