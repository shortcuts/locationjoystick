---
name: screenshots
description: Refresh all wiki/Play Store gallery screenshots from a connected Android device. Use this skill whenever the user asks to update, regenerate, recapture, or refresh screenshots for the wiki, docs, or Play Store. Also triggers on "run screenshot script", "capture gallery", "update docs screenshots", "screenshot-gallery", or any mention of refreshing the 01_idle through 17_favorites_add_button PNGs. Always use this skill for screenshot-related tasks — don't attempt to run screenshot-gallery.sh manually without it.
---

# Screenshots Skill

Captures all 17 canonical gallery screenshots from a connected Android device using
`scripts/screenshot-gallery.sh --auto`, saving them to `docs/wiki/screenshots/`.

In `--auto` mode the script runs fully non-interactively: the agent executes it
directly via the Bash tool. No manual terminal steps needed.

**New in v2:** Screenshots 16 and 17 show the "add button" (FAB) in the top-right
corner of Routes and Favorites screens. Screenshots 14 and 15 (overlays) now include
service startup verification to improve reliability.

**New in v3:** Use `--steps` to run only specific steps instead of the full suite:
- `--steps 16,17` — run steps 16 and 17 only
- `--steps 14-17` — run steps 14 through 17
- `--steps 01,03,05` — run steps 1, 3, 5
Seeding (routes, favorites) still runs automatically before the first selected step.

---

## Step 1 — Pre-flight checks (agent runs these)

```bash
# Device connected?
adb devices | grep -v "List of" | grep "device$"

# App installed?
adb shell pm list packages | grep com.locationjoystick
```

If device not found: tell the user to connect their Android device with USB debugging
enabled, wait for it to appear under `adb devices`, then proceed.

If app not installed: run `make install-on-phone` (not `make install`) to build and
install the debug APK, then follow the onboarding section below before running the
script.

---

## Step 1b — Complete onboarding autonomously (if needed)

The script requires onboarding to be complete. If the app lands on the onboarding screen
after install, do **not** ask the user to complete it manually — do it via adb:

```bash
# Grant location permissions
adb shell pm grant com.locationjoystick.app android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.locationjoystick.app android.permission.ACCESS_COARSE_LOCATION

# Grant overlay (draw over other apps) permission
adb shell appops set com.locationjoystick.app SYSTEM_ALERT_WINDOW allow

# Grant mock location permission (replaces "Select mock location app" in Developer Options)
# The app checks OPSTR_MOCK_LOCATION via AppOpsManager — this satisfies it:
adb shell appops set com.locationjoystick.app android:mock_location allow

# Verify mock location is allowed
adb shell appops get com.locationjoystick.app android:mock_location
# Expected output: MOCK_LOCATION: allow
```

Then restart the app and dismiss the notification permission dialog (tap Allow):

```bash
adb shell am force-stop com.locationjoystick.app
sleep 1
adb shell am start -n com.locationjoystick.app/.MainActivity
sleep 3
# Tap "Allow" on the notification permission dialog (coordinates ~540,1400 on Pixel 7)
adb shell input tap 540 1400
sleep 1
```

Verify onboarding is past by dumping the UI — you should see app content (e.g. "Favorites",
"Map", "Routes") rather than "Set up locationjoystick":

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
grep -o 'text="[^"]*"' /tmp/ui.xml | sort -u
```

Once past onboarding, proceed to Step 1c.

---

## Step 1c — Seed realistic data (agent runs this, after onboarding)

Do this **before** running the script. The script skips seeding steps if data already exists.

### Enable Hot Locations

Navigate to Settings and enable the "Show hot locations" toggle:

```bash
# Open the app (should already be on Idle screen)
adb shell am start -n com.locationjoystick.app/.MainActivity
sleep 2

# Dump UI and find the Settings card
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
grep -o 'text="[^"]*"' /tmp/ui.xml | sort -u
```

If on the Idle screen, tap Settings card (find its bounds from the dump, typically around `540,900`):

```bash
# Tap Settings card on Idle screen — find exact coords from dump
adb shell input tap 540 900
sleep 2
```

In Settings, scroll down to find "Show hot locations" toggle and enable it:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
grep -i "hot" /tmp/ui.xml
```

Locate the toggle bounds from the XML (`bounds="[x1,y1][x2,y2]"`), compute center, and tap:

```bash
# Example — use actual bounds from dump:
adb shell input tap <CENTER_X> <CENTER_Y>
sleep 1
```

Verify the toggle is now checked:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
grep -A2 -i "hot" /tmp/ui.xml
```

### Create 3 Routes

Navigate back to Idle, then to Routes, and create three routes with realistic names.
Use the "Add route → from map" flow. Two waypoints per route is sufficient.

**Route 1 — "Morning Walk"** (e.g. Central Park area, NYC):
- Waypoint A: tap map near `40.7829° N, 73.9654° W`
- Waypoint B: tap map ~500 m north

**Route 2 — "Riverside Jog"** (e.g. along the Seine, Paris):
- Waypoint A: near `48.8566° N, 2.3522° E`
- Waypoint B: tap map ~600 m east

**Route 3 — "Harbor Stroll"** (e.g. Sydney Harbour):
- Waypoint A: near `-33.8688° S, 151.2093° E`
- Waypoint B: tap map ~400 m southeast

For each route, use the RouteCreator UI via adb taps derived from `uiautomator dump`.
The "Add route" FAB is at the bottom right of the Routes screen. After adding both
waypoints, tap the save button (checkmark in the top bar).

Once all 3 routes exist, the script's seed phase will detect non-empty Routes and skip
auto-creation. Proceed to Step 2.

---

## Step 2 — Run the script (agent runs this)

```bash
cd /path/to/project && ./scripts/screenshot-gallery.sh --auto --output docs/wiki/screenshots
```

The `--auto` flag replaces all manual pause points with adb automation:

| Phase | What `--auto` does |
|-------|--------------------|
| Seed (pre-screenshots) | Navigates to Routes — if empty, creates "Morning Walk" and "City Loop" routes (2 waypoints each). Then navigates to Favorites — if empty, adds Tokyo, Paris, London via coordinates dialog. Ensures steps 03, 04, 06, 07, 10 all show populated lists. |
| A (step 02: map) | Navigates to Map, taps the "start simulation" FAB to start spoofing so the map screenshot shows the running state (stop button visible, location marker active). `go_idle` between steps force-stops the app, ending spoofing cleanly before step 03. |
| B (step 10: route detail) | Routes already seeded; opens overflow menu on first route and taps Edit |
| C (step 14: joystick overlay) | Navigates to Map, taps "Start location simulation" FAB (`content-desc` substring `"location simulation"`), starts `FloatingWidgetService`, expands the widget panel (tap master FAB), taps `JOYSTICK_TOGGLE` (2nd feature icon after `MAP_FLOATING`), then collapses the panel so the joystick is the screenshot focus. Widget window position is read from `dumpsys window` to handle user drags. **Improved:** Service startup now verified via `dumpsys window` with retries; 3 failed attempts fall back gracefully. |
| D (step 15: widget overlay) | Widget service already running from step 14; expands the panel (tap master FAB again) for the screenshot. **Improved:** Service startup now verified via `dumpsys window` with retries. |
| E (step 16: routes add button) | Navigates to Routes list and captures the FAB (plus button) in the top-right corner. Shows the entry point for creating new routes. |
| F (step 17: favorites add button) | Navigates to Favorites list and captures the FAB (plus button) in the top-right corner. Shows the entry point for adding new favorites. |

The script will print progress for every step. Total runtime ~2–3 minutes.

### Running specific steps only

To update only certain screenshots, add `--steps`:

```bash
# Capture steps 16 and 17 (Routes and Favorites add buttons)
./scripts/screenshot-gallery.sh --auto --steps 16,17

# Capture step 14 only (joystick overlay)
./scripts/screenshot-gallery.sh --auto --steps 14

# Capture steps 14–17 (overlays and add buttons)
./scripts/screenshot-gallery.sh --auto --steps 14-17

# Capture steps 01, 03, 05 (specific screens)
./scripts/screenshot-gallery.sh --auto --steps 01,03,05
```

**Format:**
- Single step: `--steps 16`
- Multiple steps: `--steps 16,17,18` (comma-separated, no spaces)
- Range: `--steps 14-17` (inclusive)
- Mixed: `--steps 01,03,06-09`

**Seeding:** Routes and Favorites are still seeded automatically before the first
selected step if they don't exist (for steps that need populated lists). This ensures
steps 03–10 always show data if selected.

---

## Step 3 — Script output (automatic)

The script automatically:
1. Captures all 17 canonical screenshots to `docs/wiki/screenshots/`
2. Generates 1024×500 Play Store variants (named `*_playstore.png`)
3. Displays a summary table

Expected output files (34 total: 17 source + 17 playstore variants):
```
01_idle.png + 01_idle_playstore.png
02_map.png + 02_map_playstore.png
03_routes.png + 03_routes_playstore.png
04_favorites.png + 04_favorites_playstore.png
05_settings.png + 05_settings_playstore.png
06_map_routes_sheet.png + 06_map_routes_sheet_playstore.png
07_map_favorites_sheet.png + 07_map_favorites_sheet_playstore.png
08_map_roaming_sheet.png + 08_map_roaming_sheet_playstore.png
09_route_creator.png + 09_route_creator_playstore.png
10_route_detail.png + 10_route_detail_playstore.png
11_map_picker.png + 11_map_picker_playstore.png
12_settings_scrolled.png + 12_settings_scrolled_playstore.png
13_qr_share.png + 13_qr_share_playstore.png
14_joystick_overlay.png + 14_joystick_overlay_playstore.png
15_widget_overlay.png + 15_widget_overlay_playstore.png
16_routes_add_button.png + 16_routes_add_button_playstore.png
17_favorites_add_button.png + 17_favorites_add_button_playstore.png
```

If any file is missing:
- **14_joystick_overlay.png**: joystick overlay service failed to start after 3 retries.
  Troubleshoot: verify the app is running, mock location is active, and `FloatingWidgetService` started.
  Fallback: manually start joystick via widget panel, position on screen, then capture with
  `adb exec-out screencap -p > docs/wiki/screenshots/14_joystick_overlay.png`
- **15_widget_overlay.png**: widget overlay service failed to start after 3 retries.
  Troubleshoot: verify the app is running and `FloatingWidgetService` bound correctly.
  Fallback: manually expand the widget panel, then capture with
  `adb exec-out screencap -p > docs/wiki/screenshots/15_widget_overlay.png`
- **16_routes_add_button.png / 17_favorites_add_button.png**: navigation failed.
  Verify routes or favorites screen loaded, re-run step only with
  `adb shell am force-stop com.locationjoystick.app && sleep 1 && adb shell am start -n com.locationjoystick.app/.MainActivity`
- **Any other file**: report which step failed and suggest re-running.

---

## Step 4 — Stage for commit (agent runs this)

```bash
git add docs/wiki/screenshots/
git diff --cached --name-only | grep screenshots/
```

This will include both the raw `*.png` captures and the `*_playstore.png` variants.

Report how many files changed. Offer to commit alongside the user's next code change,
or commit immediately if requested.

---

## Manual mode (fallback)

If `--auto` fails or device state requires human intervention, drop the flag:

```bash
./scripts/screenshot-gallery.sh --output docs/wiki/screenshots
```

The script will pause at steps 10, 14, and 15 and print instructions. Tell the user
what to do at each pause before they start — the pause descriptions are in the script
output itself.
