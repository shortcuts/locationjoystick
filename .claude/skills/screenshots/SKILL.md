---
name: screenshots
description: Refresh all wiki/Play Store gallery screenshots from a connected Android device. Use this skill whenever the user asks to update, regenerate, recapture, or refresh screenshots for the wiki, docs, or Play Store. Also triggers on "run screenshot script", "capture gallery", "update docs screenshots", "screenshot-gallery", or any mention of refreshing the 01_idle through 15_widget_overlay PNGs. Always use this skill for screenshot-related tasks — don't attempt to run screenshot-gallery.sh manually without it.
---

# Screenshots Skill

Captures all 15 canonical gallery screenshots from a connected Android device using
`scripts/screenshot-gallery.sh --auto`, saving them to `docs/wiki/screenshots/`.

In `--auto` mode the script runs fully non-interactively: the agent executes it
directly via the Bash tool. No manual terminal steps needed.

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

Once past onboarding, proceed to Step 2.

---

## Step 2 — Run the script (agent runs this)

```bash
cd /path/to/project && ./scripts/screenshot-gallery.sh --auto --output docs/wiki/screenshots
```

The `--auto` flag replaces the 3 former manual pause points with adb automation:

| Pause | What `--auto` does |
|-------|--------------------|
| A (step 10: route detail) | Checks UI dump for existing route; if none, navigates to route creator, drops a waypoint, saves "Auto" route |
| B (step 14: joystick overlay) | Navigates to Map, starts `MockLocationService` via `am start-foreground-service`, starts `JoystickOverlayService` with `extra_show_overlay=true` |
| C (step 15: widget overlay) | Stops joystick service, starts `FloatingWidgetService` (auto-shows on start) |

The script will print progress for every step. Total runtime ~2–3 minutes.

---

## Step 3 — Verify output (agent runs this)

```bash
ls docs/wiki/screenshots/*.png | sort
```

Expected files:
```
01_idle.png
02_map.png
03_routes.png
04_favorites.png
05_settings.png
06_map_routes_sheet.png
07_map_favorites_sheet.png
08_map_roaming_sheet.png
09_route_creator.png
10_route_detail.png
11_map_picker.png
12_settings_scrolled.png
13_qr_share.png
14_joystick_overlay.png
15_widget_overlay.png
```

If any file is missing:
- **14_joystick_overlay.png / 15_widget_overlay.png**: overlay service failed to start.
  Capture manually: put the overlay on screen, then run
  `adb exec-out screencap -p > docs/wiki/screenshots/14_joystick_overlay.png`
- **Any other file**: report which step failed and suggest re-running.

---

## Step 4 — Stage for commit (agent runs this)

```bash
git add docs/wiki/screenshots/
git diff --cached --name-only | grep screenshots/
```

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
