## Title

locationjoystick — free, open-source GPS spoofer for Android (no root, no ads)

## Body

Disclaimer: not AI slop or marketing. I'm a software engineer who builds tools I actually use — feel free to check my [other work](https://github.com/shortcuts).

---

Hey everyone.

After 4+ years of using GPS Joystick and a few months with YAMLA, I got fed up. Both apps work great but the ad-gating on every screen and the lack of UX improvements finally pushed me to build my own. I work 7 hours a day with these tools, so I had a very clear picture of what I wanted.

So I built **[locationjoystick](https://github.com/shortcuts/locationjoystick)** — a free, open-source, no-root GPS spoofer. Here's what it does:

**Movement**
- Floating joystick overlay — drag in any direction at configurable speed, stays on top of any app
- Click-to-Move — long-press anywhere on map, choose "Walk here" or "Teleport there"
- Auto-roaming — set a center, radius, and total distance; walks randomly within the area, road-following optional, returns to start when done

**Routes**
- Saved routes — create waypoints on a map, replay, loop, or reverse at will
- Road-following routes — OSRM-powered routing that follows real streets
- Route recording — record your own path in real time, save and replay it later
- GPX import — bring in any `.gpx` file from hiking apps, trackers, or online sources

**Favorites & locations**
- Favorite locations — save named spots and teleport or walk there instantly
- Address search — powered by Nominatim/OSM
- Last position restore — resumes from where you left off automatically

**UX**
- Configurable floating widget — quick-access controls over any app
- Speed profiles — three user-editable presets (walk / run / bike), one-tap switching from the widget
- Background spoofing — continues while other apps run, survives screen off
- GPS realism — bearing hold, altitude drift, warm-up accuracy envelope, satellite count in fix, natural signal dropout cycles
- Offline map tiles — download areas for use without connectivity
- Elevation Controls *(experimental, root only)* — inject tilt sensor events so apps see the phone as physically tilted

**Data & portability**
- One-tap import from GPS Joystick or YAMLA — your saved routes migrate in seconds
- Full data export / import — backup routes, favorites, and settings to JSON
- QR transfer — export and import all data between devices via scannable QR codes

---

If you're coming from GPS Joystick or YAMLA you can import your data directly and be up and running in seconds.

Download: [GitHub Releases](https://github.com/shortcuts/locationjoystick/releases) | Play Store (coming soon)

Docs / screenshots: [shortcuts.github.io/locationjoystick](https://shortcuts.github.io/locationjoystick/)

Source: [github.com/shortcuts/locationjoystick](https://github.com/shortcuts/locationjoystick)

Issues, feature requests, and PRs are all welcome. Happy to answer any questions.
