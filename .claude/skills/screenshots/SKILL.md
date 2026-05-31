---
name: screenshots
description: Refresh all wiki/Play Store gallery screenshots from a connected Android device. Use this skill whenever the user asks to update, regenerate, recapture, or refresh screenshots for the wiki, docs, or Play Store. Also triggers on "run screenshot script", "capture gallery", "update docs screenshots", "screenshot-gallery", or any mention of refreshing the 01_idle through 15_widget_overlay PNGs. Always use this skill for screenshot-related tasks — don't attempt to run screenshot-gallery.sh manually without it.
---

# Screenshots Skill

Captures all 15 canonical gallery screenshots from a connected Android device using
`scripts/screenshot-gallery.sh`, saving them to `docs/wiki/screenshots/`.

The script has interactive prompts that require a real terminal — the agent handles
pre/post steps via adb and guides the user through every manual pause point.

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

If app not installed: tell the user to install the debug APK (`make install`) and
complete onboarding before continuing.

---

## Step 2 — Tell the user to run the script

Because `scripts/screenshot-gallery.sh` contains interactive `read` prompts, it must
run in a real interactive terminal — not via the agent's Bash tool. Tell the user:

> **Open a terminal in the project root and run:**
> ```bash
> make screenshot
> ```
> The script will navigate the app automatically but will pause 3 times for manual
> steps. Instructions for each pause are below — read them before pressing ENTER.

---

## Step 3 — Guide through the 3 manual pause points

Tell the user what to do at each pause **before** they start the script, so they're
prepared when the prompts appear.

### Pause A — Step 10: Route Detail

The terminal will print:
```
MANUAL STEP:
  Ensure at least one route exists in the Routes list, then press ENTER.
```

Instructions for the user:
1. Look at the device — the app should be on the Routes screen.
2. **If a route already exists** in the list → press ENTER now.
3. **If no routes exist:**
   - Tap **Add route** → **from map**
   - Tap anywhere on the map to drop a waypoint, then tap **Save**
   - Give it any name and confirm
   - Press ENTER once the route appears in the list

### Pause B — Step 14: Joystick Overlay

The terminal will print:
```
MANUAL STEP:
  Start mock location then enable the Floating Joystick.
  The joystick overlay should be visible on screen before you press ENTER.
```

Instructions for the user:
1. The app is still open on the device.
2. Navigate to the **Map** screen.
3. Tap the **Start Simulation** FAB (▶ play button) to begin mock location.
4. Enable the Floating Joystick using either method:
   - Open the nav drawer → toggle **Floating Joystick**
   - Or tap the joystick icon in the floating widget if it's already visible
5. Confirm the **circular joystick overlay** is visible over the current screen.
6. Press ENTER in the terminal.

### Pause C — Step 15: Widget Overlay

The terminal will print:
```
MANUAL STEP:
  Dismiss the joystick (if open) and enable the Floating Widget instead.
  The widget bubble should be visible on screen before you press ENTER.
```

Instructions for the user:
1. Dismiss the joystick overlay: open the nav drawer → toggle **Floating Joystick** off.
2. Enable the Floating Widget using either method:
   - Open the nav drawer → toggle **Floating Widget**
   - Or go to **Settings** → Widget section → enable **Floating Widget**
3. Confirm the small **widget bubble** is visible on screen.
4. Press ENTER in the terminal.

---

## Step 4 — Verify output (agent runs this)

After the user reports the script finished, verify all 15 files were captured:

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
- **14_joystick_overlay.png / 15_widget_overlay.png**: overlay services couldn't
  be reached automatically. Tell the user they can capture these manually with
  `adb exec-out screencap -p > docs/wiki/screenshots/14_joystick_overlay.png`
  while the overlay is visible, then repeat for 15.
- **Any other file**: report which step failed and suggest re-running the script.

---

## Step 5 — Stage for commit (agent runs this)

```bash
git add docs/wiki/screenshots/
git diff --cached --name-only | grep screenshots/
```

Report how many files changed. Offer to commit them alongside the user's next code
change, or commit immediately if requested.
