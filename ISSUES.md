# Known Issues & Backlog

## Documentation Outdated Items

No outstanding documentation issues.

---

## Backlog

### Onboarding screen

We have one section with "check again" but not the others, if we believe we need it we should add it everywhere, otherwise let's just remove it. i personally never had to click on it, it was refreshed right away

### Settings screen

- We should be a bit more exhaustive on the description of the GPS Realism options for people that might not be fully understanding what it applies.
- If we recommend 0.8m idle radius, this should be the default, and if it's the default, we can remove the recommendation notice.
- How do we justify not having every GPS Realism option checked? If it is not worth to be defaulted, the description should explain why and what it implies.
- We could add brief description to the Map and Default Roaming options
- Default Roaming should be renamed "Roaming"
- Import data modal doesn't have the 3 options on the same row

### Info screen

- Everything should be renamed "About", that's the name of the screen in the app, but in the code it's "info"

### Map screen

- When having an active spoof location, clicking on a favorite opens a bottom drawer sheet that has a different style of the standard bottom drawer sheet after clicking on the map (with the Do nothing), we should reuse the one with the "Do nothing" and remove the one with the "Back" from the codebase.
- When clicking on the map to add a new point to walk to, the tracing disappears, but the route seems to properly execute still
