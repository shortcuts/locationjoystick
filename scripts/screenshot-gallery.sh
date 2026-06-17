#!/usr/bin/env bash
# screenshot-gallery.sh
#
# Captures wiki / Play Store gallery screenshots from a connected Android device.
# Re-run any time the app updates. Saves PNGs to ./screenshots/ (or --output DIR).
#
# Usage:
#   ./scripts/screenshot-gallery.sh
#   ./scripts/screenshot-gallery.sh --output /tmp/gallery
#   ./scripts/screenshot-gallery.sh --device emulator-5554
#   ./scripts/screenshot-gallery.sh --steps 16,17
#   ./scripts/screenshot-gallery.sh --steps 14-17
#   ./scripts/screenshot-gallery.sh --auto --steps 16-17
#
# Prerequisites:
#   - adb in PATH, device connected with USB debugging on
#   - App installed and past onboarding (permissions granted)
#   - At least one saved route must exist in the app (required for step 10)
#
# Overlay screens (joystick + widget) require manual activation — the script
# will pause and prompt you at those steps.
#
# Android Demo Mode is enabled for the duration of the run so screenshots show
# a clean status bar (neutral clock, full battery/signal, no notifications).
# Demo mode exits automatically on completion or error.
#
# --steps allows running only specific step numbers:
#   --steps 16,17        (run steps 16 and 17 only)
#   --steps 14-17        (run steps 14, 15, 16, 17)
#   --steps 01,03,05     (run steps 1, 3, 5)
# Seeding (routes, favorites) always runs before the first selected step.
#
# Output files (17 canonical PNGs):
#   01_idle, 02_map, 03_routes, 04_favorites, 05_settings,
#   06_map_routes_sheet, 07_map_favorites_sheet, 08_map_roaming_sheet,
#   09_route_creator, 10_route_detail, 11_map_picker,
#   12_qr_share,
#   13_joystick_overlay, 14_widget_overlay,
#   15_routes_add_button, 16_favorites_add_button,
#   17_group_sync

set -euo pipefail

# ── Config ───────────────────────────────────────────────────────────────────

PACKAGE="com.locationjoystick.app"
ACTIVITY=".MainActivity"
OUTPUT_DIR="docs/wiki/screenshots"
ADB_DEVICE=""
AUTO=false
STEPS_FILTER=""  # Comma-separated or range, e.g. "16,17" or "14-17"
ENABLED_STEPS=" "  # Space-separated list of enabled step numbers (01 02 03 etc)

# ── Arg parsing ──────────────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)  OUTPUT_DIR="$2"; shift 2 ;;
    --device)  ADB_DEVICE="-s $2"; shift 2 ;;
    --auto)    AUTO=true; shift ;;
    --steps)   STEPS_FILTER="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

# Parse --steps into a space-separated list of enabled step numbers
if [[ -n "$STEPS_FILTER" ]]; then
  ENABLED_STEPS=" "
  for part in $(echo "$STEPS_FILTER" | tr ',' '\n'); do
    if [[ "$part" =~ ^([0-9]+)-([0-9]+)$ ]]; then
      # Range: e.g. "14-17"
      start=${BASH_REMATCH[1]}
      end=${BASH_REMATCH[2]}
      for (( i=start; i<=end; i++ )); do
        step_num=$(printf "%02d" $i)
        ENABLED_STEPS="${ENABLED_STEPS}${step_num} "
      done
    elif [[ "$part" =~ ^[0-9]+$ ]]; then
      # Single number: e.g. "16"
      step_num=$(printf "%02d" $part)
      ENABLED_STEPS="${ENABLED_STEPS}${step_num} "
    else
      echo "Invalid --steps format: $part (expected '16' or '14-17')"
      exit 1
    fi
  done
else
  # No filter: enable all steps
  for i in {01..17}; do ENABLED_STEPS="${ENABLED_STEPS}$(printf '%02d' $i) "; done
fi

# Helper to check if a step should run (e.g. should_run_step "16")
should_run_step() {
  local step="$1"
  [[ "$ENABLED_STEPS" =~ " ${step} " ]]
}

ADB="adb $ADB_DEVICE"

# ── Helpers ──────────────────────────────────────────────────────────────────

log()  { echo "▶ $*"; }
warn() { echo "⚠ $*"; }

# Dump UI hierarchy to a temp file and return its path.
ui_dump() {
  local tmp
  tmp=$(mktemp /tmp/uidump.XXXXXX.xml)
  $ADB shell uiautomator dump /sdcard/uidump.xml >/dev/null 2>&1
  $ADB pull /sdcard/uidump.xml "$tmp" >/dev/null 2>&1
  echo "$tmp"
}

# Given UI dump file and a search term (matched against text= or content-desc=),
# echo the centre point as "X Y" of the first matching node.
# Uses [^>]* between the text match and bounds= to stay within one XML node
# (the uiautomator dump is a single line; [^>]* prevents crossing node boundaries).
bounds_of() {
  local dump="$1" term="$2"
  perl -lne '
    if (/(?:text|content-desc)="[^"]*'"${term}"'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/i) {
      printf "%d %d\n", int(($1+$3)/2), int(($2+$4)/2);
      last;
    }
  ' "$dump" 2>/dev/null
}

# Tap a UI element by its visible text or content-desc (case-insensitive substring).
tap_text() {
  local text="$1"
  local dump centre x y
  dump=$(ui_dump)
  centre=$(bounds_of "$dump" "$text")
  rm -f "$dump"
  if [[ -z "$centre" ]]; then
    warn "Could not find UI element containing \"$text\" — skipping tap."
    return 1
  fi
  read -r x y <<< "$centre"
  log "Tapping \"$text\" at ($x, $y)"
  $ADB shell input tap "$x" "$y"
}

# Tap a UI element whose text or content-desc is exactly the given string.
tap_text_exact() {
  local text="$1"
  local dump centre x y
  dump=$(ui_dump)
  centre=$(perl -lne '
    if (/(?:text|content-desc)="'"${text}"'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/i) {
      printf "%d %d\n", int(($1+$3)/2), int(($2+$4)/2);
      last;
    }
  ' "$dump" 2>/dev/null)
  rm -f "$dump"
  if [[ -z "$centre" ]]; then
    warn "Could not find UI element with exact text \"$text\" — skipping tap."
    return 1
  fi
  read -r x y <<< "$centre"
  log "Tapping \"$text\" (exact) at ($x, $y)"
  $ADB shell input tap "$x" "$y"
}

# Tap a UI element by text/content-desc, but only match nodes whose vertical
# centre is at or below min_y. Filters out closed-drawer items that remain in
# the semantics tree and would otherwise ambiguate IdleScreen card taps.
tap_text_below() {
  local text="$1" min_y="$2"
  local dump centre x y
  dump=$(ui_dump)
  centre=$(perl -lne '
    while (/(?:text|content-desc)="[^"]*'"${text}"'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/gi) {
      my $cy = int(($2+$4)/2);
      next if $cy < '"${min_y}"';
      printf "%d %d\n", int(($1+$3)/2), $cy;
      last;
    }
  ' "$dump" 2>/dev/null)
  rm -f "$dump"
  if [[ -z "$centre" ]]; then
    warn "Could not find \"$text\" below y=$min_y — skipping tap."
    return 1
  fi
  read -r x y <<< "$centre"
  log "Tapping \"$text\" at ($x, $y) [y≥$min_y filter]"
  $ADB shell input tap "$x" "$y"
}

# Press the hardware back button.
back() { $ADB shell input keyevent KEYCODE_BACK; }

# Wait N seconds with a visible countdown.
wait_s() {
  local n="$1" msg="${2:-Waiting}"
  for (( i=n; i>0; i-- )); do
    printf "\r  %s… %ds " "$msg" "$i"
    sleep 1
  done
  printf "\r%*s\r" 40 ""
}

# Enter Android Demo Mode: clean status bar (neutral clock, full battery/signal,
# no notifications) so screenshots don't leak personal phone information.
demo_mode_enter() {
  log "Entering demo mode (clean status bar)..."
  $ADB shell settings put global sysui_demo_allowed 1 2>/dev/null || true
  $ADB shell am broadcast -a com.android.systemui.demo \
    -e command enter >/dev/null 2>&1 || true
  $ADB shell am broadcast -a com.android.systemui.demo \
    -e command clock -e hhmm 1200 >/dev/null 2>&1 || true
  $ADB shell am broadcast -a com.android.systemui.demo \
    -e command battery -e level 100 -e plugged false >/dev/null 2>&1 || true
  $ADB shell am broadcast -a com.android.systemui.demo \
    -e command network -e mobile show -e level 4 -e datatype lte \
    -e wifi show -e level 4 >/dev/null 2>&1 || true
  $ADB shell am broadcast -a com.android.systemui.demo \
    -e command notifications -e visible false >/dev/null 2>&1 || true
}

# Exit Android Demo Mode and restore the real status bar.
demo_mode_exit() {
  $ADB shell am broadcast -a com.android.systemui.demo \
    -e command exit >/dev/null 2>&1 || true
  log "Demo mode exited"
}

# Capture screen and pull to OUTPUT_DIR/<name>.png (idempotent overwrite).
screenshot() {
  local name="$1"
  local dest="$OUTPUT_DIR/${name}.png"
  log "Capturing → $dest"
  $ADB exec-out screencap -p > "$dest"
  echo "  Saved: $dest"
}

# Pause and wait for the user to perform a manual step.
pause_for_user() {
  local msg="$1"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  MANUAL STEP:"
  echo "  $msg"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  read -rp "  Press ENTER when ready..."
  echo ""
}

# Ensure routes exist in the list. If empty, navigate to the creator and create
# two seed routes so list screenshots and route detail are non-empty.
seed_route_if_needed() {
  log "Checking if routes exist..."
  wait_s 2 "Routes list settling"
  local dump
  dump=$(ui_dump)
  if grep -q 'Menu' "$dump" 2>/dev/null; then
    rm -f "$dump"
    log "Routes found — no seeding needed."
    return 0
  fi
  rm -f "$dump"
  log "No routes — creating seed routes..."
  local cx=$(( SCREEN_W / 2 ))
  local y1=$(( SCREEN_H * 35 / 100 ))
  local y2=$(( SCREEN_H * 55 / 100 ))
  local y3=$(( SCREEN_H * 45 / 100 ))
  local -a route_names=("Morning Walk" "City Loop")
  for name in "${route_names[@]}"; do
    go_idle
    tap_text_below "Routes" "$CARD_Y_MIN"
    wait_s 2 "Routes loading"
    tap_text "Add route"
    wait_s 1 "Add menu opening"
    tap_text "from map"
    wait_s 4 "Route creator loading"
    # Need ≥2 waypoints before Save FAB appears.
    $ADB shell input tap "$cx" "$y1"
    wait_s 2 "Placing waypoint 1"
    $ADB shell input tap "$(( cx + 60 ))" "$y2"
    wait_s 2 "Placing waypoint 2"
    $ADB shell input tap "$cx" "$y3"
    wait_s 2 "Placing waypoint 3"
    tap_text "Save route"
    wait_s 1 "Save dialog opening"
    $ADB shell input text "$name"
    wait_s 1
    tap_text_exact "Save"
    wait_s 2 "Saving route"
  done
  go_idle
  tap_text_below "Routes" "$CARD_Y_MIN"
  wait_s 2 "Routes loading"
}

# Ensure favorites exist in the list. If empty, add three named locations via
# the coordinates dialog so list screenshots are non-empty.
seed_favorites_if_needed() {
  log "Checking if favorites exist..."
  wait_s 2 "Favorites list settling"
  local dump
  dump=$(ui_dump)
  # Favorite list items expose "More options" overflow buttons in the UI tree.
  if grep -q 'More options\|Walk to\|Teleport to' "$dump" 2>/dev/null; then
    rm -f "$dump"
    log "Favorites found — no seeding needed."
    return 0
  fi
  rm -f "$dump"
  log "No favorites — creating seed favorites..."
  local -a fav_names=("Tokyo" "Paris" "London")
  local -a fav_lats=("35.6762" "48.8566" "51.5074")
  local -a fav_lons=("139.6503" "2.3522" "-0.1278")
  for i in "${!fav_names[@]}"; do
    tap_text "Add favorite"
    wait_s 1 "Add menu opening"
    tap_text "from coordinates"
    wait_s 1 "Dialog opening"
    tap_text "Name"
    wait_s 1
    $ADB shell input text "${fav_names[$i]}"
    wait_s 1
    tap_text "Latitude"
    wait_s 1
    $ADB shell input text "${fav_lats[$i]}"
    wait_s 1
    tap_text "Longitude"
    wait_s 1
    $ADB shell input text "${fav_lons[$i]}"
    wait_s 1
    tap_text_exact "Save"
    wait_s 2 "Saving favorite"
  done
}

# Start MockLocationService via direct intent (ACTION_START with default coords).
start_mock_location() {
  log "Starting location simulation..."
  $ADB shell am start-foreground-service \
    -n "${PACKAGE}/com.locationjoystick.core.location.MockLocationService" \
    -a "com.locationjoystick.core.location.ACTION_START" 2>/dev/null || true
  wait_s 2 "Starting simulation"
}

# Start JoystickOverlayService and show overlay immediately via EXTRA_SHOW_OVERLAY.
start_joystick_overlay() {
  log "Starting joystick overlay..."
  $ADB shell am startservice \
    -n "${PACKAGE}/com.locationjoystick.feature.joystick.impl.JoystickOverlayService" \
    --ez extra_show_overlay true 2>/dev/null || true
  wait_s 3 "Joystick overlay appearing"
  # Verify service is running via dumpsys
  local attempts=0
  while ! $ADB shell dumpsys window windows 2>/dev/null | grep -q "JoystickOverlayService"; do
    if (( ++attempts > 3 )); then
      warn "Joystick overlay service failed to start after 3 retries"
      return 1
    fi
    wait_s 1 "Retrying joystick startup"
  done
}

# Show joystick via widget panel toggle.
# EXTRA_SHOW_OVERLAY via am startservice is unreliable when the service is already
# running (the extra is not re-delivered). The correct flow is:
#   1. Start FloatingWidgetService (collapsed FAB appears)
#   2. Tap the master FAB to expand the panel
#   3. Tap JOYSTICK_TOGGLE (2nd feature icon, after MAP_FLOATING)
# Widget FAB position is read from the live overlay window bounds via dumpsys.
show_joystick_via_widget() {
  log "Starting widget service and toggling joystick..."
  $ADB shell am startservice \
    -n "${PACKAGE}/com.locationjoystick.feature.widget.impl.FloatingWidgetService" 2>/dev/null || true
  wait_s 2 "Widget overlay appearing"

  # Read overlay window position from WindowManager
  local wx wy
  read -r wx wy < <(
    $ADB shell dumpsys window windows 2>/dev/null \
      | perl -lne 'if (/mAttrs=\{(-?\d+),(\d+)\}\(wrapxwrap\).*APPLICATION_OVERLAY/) { print "$1 $2"; last; }'
  ) || true
  if [[ -z "$wx" || -z "$wy" ]]; then
    warn "Widget window not found via dumpsys — using calculated fallback"
    local screen_h
    screen_h=$($ADB shell wm size | awk '{print $NF}' | cut -dx -f2)
    wx=0; wy=$(( (screen_h - 136 - 66) / 2 ))  # appHeight/2 ≈ layout y
  fi

  # 440 dpi: 1dp = 2.75px. FAB = 36dp + 4dp padding each side = 44dp = 121px.
  # Overlay y in LayoutParams is relative to the content area (below status bar).
  local STATUS_BAR=136
  local FAB_PX=121
  local cx=$(( wx + FAB_PX / 2 ))
  local fab_cy=$(( STATUS_BAR + wy + FAB_PX / 2 ))
  # MAP_FLOATING is icon 0, JOYSTICK_TOGGLE is icon 1 → offset = (1+1)*FAB_PX + FAB_PX/2
  local toggle_y=$(( STATUS_BAR + wy + FAB_PX * 2 + FAB_PX / 2 ))

  log "Expanding widget at ($cx, $fab_cy)"
  $ADB shell input tap "$cx" "$fab_cy"
  wait_s 1 "Panel expanding"

  log "Tapping JOYSTICK_TOGGLE at ($cx, $toggle_y)"
  $ADB shell input tap "$cx" "$toggle_y"
  wait_s 2 "Joystick appearing"
}

# Collapse widget panel (tap master FAB to toggle).
collapse_widget_panel() {
  local wx wy
  read -r wx wy < <(
    $ADB shell dumpsys window windows 2>/dev/null \
      | perl -lne 'if (/mAttrs=\{(-?\d+),(\d+)\}\(wrapxwrap\).*APPLICATION_OVERLAY/) { print "$1 $2"; last; }'
  ) || true
  [[ -z "$wx" ]] && wx=0
  [[ -z "$wy" ]] && wy=1069
  local STATUS_BAR=136 FAB_PX=121
  local cx=$(( wx + FAB_PX / 2 ))
  local cy=$(( STATUS_BAR + wy + FAB_PX / 2 ))
  log "Collapsing widget panel at ($cx, $cy)"
  $ADB shell input tap "$cx" "$cy"
  wait_s 1 "Panel collapsing"
}

# Generate 1024×500 Play Store variants for all captured screenshots.
# Each source screenshot is scaled to fit within 500 px height and centered
# on a 1024×500 canvas with Material dark surface background (#1C1B1F).
generate_playstore_variants() {
  log "Generating Play Store variants..."
  python3 << 'PYTHON_EOF'
from PIL import Image
import os
import sys

src_dir = "docs/wiki/screenshots"
canvas_w, canvas_h = 1024, 500
bg_color = (28, 27, 31)

if not os.path.isdir(src_dir):
  print(f"  Error: {src_dir} not found", file=sys.stderr)
  sys.exit(1)

files = sorted(f for f in os.listdir(src_dir) if f.endswith(".png") and "_playstore" not in f)
if not files:
  print(f"  No source PNG files found in {src_dir}", file=sys.stderr)
  sys.exit(1)

for fname in files:
  src_path = os.path.join(src_dir, fname)
  name, ext = os.path.splitext(fname)
  dst_path = os.path.join(src_dir, f"{name}_playstore{ext}")
  img = Image.open(src_path)
  img.thumbnail((canvas_w, canvas_h), Image.LANCZOS)
  canvas = Image.new("RGB", (canvas_w, canvas_h), bg_color)
  x = (canvas_w - img.width) // 2
  y = (canvas_h - img.height) // 2
  canvas.paste(img, (x, y), img if img.mode == "RGBA" else None)
  canvas.save(dst_path, "PNG", optimize=True)
  print(f"  {fname} → {os.path.basename(dst_path)}")
PYTHON_EOF
}

# Expand widget panel (same tap — toggles).
expand_widget_panel() { collapse_widget_panel; }

# Stop JoystickOverlayService (removes the overlay).
stop_joystick_overlay() {
  log "Stopping joystick overlay..."
  $ADB shell am stopservice \
    -n "${PACKAGE}/com.locationjoystick.feature.joystick.impl.JoystickOverlayService" 2>/dev/null || true
  wait_s 1 "Stopping joystick"
}

# Start FloatingWidgetService (showOverlayOnStart=true, shows immediately).
start_widget_overlay() {
  log "Starting widget overlay..."
  $ADB shell am startservice \
    -n "${PACKAGE}/com.locationjoystick.feature.widget.impl.FloatingWidgetService" 2>/dev/null || true
  wait_s 3 "Widget overlay appearing"
  # Verify service is running via dumpsys
  local attempts=0
  while ! $ADB shell dumpsys window windows 2>/dev/null | grep -q "FloatingWidgetService"; do
    if (( ++attempts > 3 )); then
      warn "Widget overlay service failed to start after 3 retries"
      return 1
    fi
    wait_s 1 "Retrying widget startup"
  done
}

# Force-stop and restart the app to guarantee a clean IdleScreen landing.
# --activity-single-top only redelivers the intent; the Compose nav stack stays
# wherever it was. Force-stop is the only reliable way to reset it.
go_idle() {
  log "Returning to IdleScreen..."
  $ADB shell am force-stop "$PACKAGE"
  sleep 1
  $ADB shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null
  wait_s 4 "App starting"
}

# ── Setup ────────────────────────────────────────────────────────────────────

mkdir -p "$OUTPUT_DIR"

log "Checking device..."
if ! $ADB devices | grep -q "device$"; then
  echo "Error: no device found. Connect a device or pass --device <serial>."
  exit 1
fi

DEVICE_MODEL=$($ADB shell getprop ro.product.model | tr -d '\r')
SCREEN_SIZE=$($ADB shell wm size | awk '{print $NF}')
log "Device: $DEVICE_MODEL ($SCREEN_SIZE)"

demo_mode_enter
trap demo_mode_exit EXIT

# Ensure app is installed
log "Checking app installation..."
if ! $ADB shell pm list packages 2>/dev/null | grep -q "$PACKAGE"; then
  echo ""
  echo "Error: $PACKAGE not installed."
  echo "Run 'make install-on-phone' to build and install the debug APK,"
  echo "then re-run this script."
  exit 1
fi

# Screen height for Y-threshold disambiguation of IdleScreen card taps.
SCREEN_W=$(echo "$SCREEN_SIZE" | awk -F'x' '{print $1}')
SCREEN_H=$(echo "$SCREEN_SIZE" | awk -F'x' '{print $2}')
# IdleScreen cards live roughly in the bottom 70% of the display.
CARD_Y_MIN=$(( SCREEN_H * 30 / 100 ))

# ── 1. Launch app ────────────────────────────────────────────────────────────

log "Launching app (force-stop to clear any saved nav state)..."
$ADB shell am force-stop "$PACKAGE"
sleep 1
$ADB shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null
wait_s 4 "App launching"

dump=$(ui_dump)
if grep -qi "onboarding\|Welcome\|grant\|permission" "$dump" 2>/dev/null; then
  rm -f "$dump"
  if $AUTO; then
    log "App appears to be on onboarding screen — completing via adb..."
    $ADB shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
    $ADB shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
    $ADB shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
    $ADB shell appops set "$PACKAGE" android:mock_location allow 2>/dev/null || true
    log "Permissions granted. Restarting app..."
    $ADB shell am force-stop "$PACKAGE"
    sleep 1
    $ADB shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null
    wait_s 3 "App starting"
    # Dismiss notification permission dialog — "Allow" is on the right side.
    if ! tap_text_exact "Allow" 2>/dev/null; then
      allow_x=$(( SCREEN_W * 65 / 100 ))
      allow_y=$(( SCREEN_H * 60 / 100 ))
      log "Allow button not found in UI tree — tapping at ($allow_x, $allow_y)"
      $ADB shell input tap "$allow_x" "$allow_y"
    fi
    wait_s 2 "Dialog dismissing"
    # Verify onboarding is past
    dump=$(ui_dump)
    if grep -qi "onboarding\|Welcome\|grant\|permission" "$dump" 2>/dev/null; then
      rm -f "$dump"
      echo ""
      echo "Error: Auto-onboarding failed — still on onboarding screen."
      echo "Complete onboarding manually then re-run."
      exit 1
    fi
    rm -f "$dump"
    log "Onboarding auto-completed successfully."
  else
    echo ""
    echo "Error: App appears to be on the onboarding screen."
    echo "Complete onboarding (grant permissions, enable mock location) then re-run."
    exit 1
  fi
fi
rm -f "$dump"

# ── Seed data (--auto only) ───────────────────────────────────────────────────
# Routes and favorites must be non-empty before capturing list screenshots (03,
# 04, 06, 07) and the route detail (10). Seed them once up front so every
# subsequent step sees populated lists.

if $AUTO; then
  log "=== SEEDING ROUTES ==="
  go_idle
  tap_text_below "Routes" "$CARD_Y_MIN"
  wait_s 2 "Routes loading"
  seed_route_if_needed

  log "=== SEEDING FAVORITES ==="
  go_idle
  tap_text_below "Favorites" "$CARD_Y_MIN"
  wait_s 2 "Favorites loading"
  seed_favorites_if_needed
fi

# ── 01. IdleScreen ───────────────────────────────────────────────────────────

if should_run_step "01"; then
  log "=== 01 IDLE ==="
  go_idle
  screenshot "01_idle"
fi

# ── 02. Map screen ───────────────────────────────────────────────────────────

if should_run_step "02"; then
  log "=== 02 MAP ==="
  # Y-min filter prevents matching the closed drawer "Map" item in the semantics tree.
  tap_text_below "Map" "$CARD_Y_MIN"
  wait_s 3 "Map loading"
  # Start spoofing so the map screenshot shows the running state (stop button visible).
  # content-desc is "Start location simulation" — "location simulation" is a reliable substring.
  tap_text "location simulation"
  wait_s 3 "Starting simulation"
  screenshot "02_map"
fi

# ── 03. Routes screen ────────────────────────────────────────────────────────

if should_run_step "03"; then
  log "=== 03 ROUTES ==="
  go_idle
  tap_text_below "Routes" "$CARD_Y_MIN"
  wait_s 2 "Routes loading"
  screenshot "03_routes"
fi

# ── 04. Favorites screen ─────────────────────────────────────────────────────

if should_run_step "04"; then
  log "=== 04 FAVORITES ==="
  go_idle
  tap_text_below "Favorites" "$CARD_Y_MIN"
  wait_s 2 "Favorites loading"
  screenshot "04_favorites"
fi

# ── 05. Settings screen ──────────────────────────────────────────────────────

if should_run_step "05"; then
  log "=== 05 SETTINGS ==="
  go_idle
  tap_text_below "Settings" "$CARD_Y_MIN"
  wait_s 2 "Settings loading"
  screenshot "05_settings"
fi

# ── 06. Map → Routes bottom sheet ────────────────────────────────────────────

if should_run_step "06"; then
  log "=== 06 MAP ROUTES SHEET ==="
  go_idle
  tap_text_below "Map" "$CARD_Y_MIN"
  wait_s 3 "Map loading"
  # Use content-desc of the FAB, not the bare label "Routes" which would match the drawer.
  tap_text "open routes"
  wait_s 2 "Routes sheet opening"
  screenshot "06_map_routes_sheet"
  back
  wait_s 1 "Dismissing sheet"
fi

# ── 07. Map → Favorites bottom sheet ─────────────────────────────────────────

if should_run_step "07"; then
  log "=== 07 MAP FAVORITES SHEET ==="
  tap_text "open favorites"
  wait_s 2 "Favorites sheet opening"
  screenshot "07_map_favorites_sheet"
  back
  wait_s 1 "Dismissing sheet"
fi

# ── 08. Map → Roaming bottom sheet ───────────────────────────────────────────

if should_run_step "08"; then
  log "=== 08 MAP ROAMING SHEET ==="
  tap_text "start roaming"
  wait_s 2 "Roaming sheet opening"
  screenshot "08_map_roaming_sheet"
  back
  wait_s 1 "Dismissing sheet"
fi

# ── 09. Route creator ────────────────────────────────────────────────────────

if should_run_step "09"; then
  log "=== 09 ROUTE CREATOR ==="
  go_idle
  tap_text_below "Routes" "$CARD_Y_MIN"
  wait_s 2 "Routes loading"
  tap_text "Add route"
  wait_s 1 "Add menu opening"
  tap_text "from map"
  wait_s 3 "Route creator loading"
  screenshot "09_route_creator"
  back
  wait_s 1 "Returning to Routes"
fi

# ── 10. Route detail ─────────────────────────────────────────────────────────

if should_run_step "10"; then
  log "=== 10 ROUTE DETAIL ==="
  if $AUTO; then
    seed_route_if_needed
  else
    pause_for_user "Ensure at least one route exists in the Routes list, then press ENTER."
  fi
  # Open the overflow menu on the first visible route and tap Edit.
  # tap_text_below filters out the TopAppBar hamburger which shares content-desc "Menu".
  tap_text_below "Menu" 230
  wait_s 1 "Menu opening"
  tap_text "Edit"
  wait_s 2 "Route detail loading"
  screenshot "10_route_detail"
  back
  wait_s 1 "Returning to Routes"
fi

# ── 11. Map picker (from Favorites add flow) ──────────────────────────────────

if should_run_step "11"; then
  log "=== 11 MAP PICKER ==="
  go_idle
  tap_text_below "Favorites" "$CARD_Y_MIN"
  wait_s 2 "Favorites loading"
  tap_text "Add favorite"
  wait_s 1 "Add menu opening"
  tap_text "from map"
  wait_s 3 "Map picker loading"
  screenshot "11_map_picker"
  back
  wait_s 1 "Returning to Favorites"
fi

# ── 12. QR share dialog ──────────────────────────────────────────────────────

if should_run_step "12"; then
  log "=== 12 QR SHARE ==="
  tap_text "Export"
  wait_s 1 "Export menu opening"
  tap_text "QR"
  wait_s 2 "QR share dialog opening"
  screenshot "13_qr_share"
  back
  wait_s 1 "Dismissing QR dialog"
fi

# ── 13. Joystick overlay ─────────────────────────────────────────────────────

if should_run_step "13"; then
  log "=== 13 JOYSTICK OVERLAY ==="
  if $AUTO; then
    go_idle
    tap_text_below "Map" "$CARD_Y_MIN"
    wait_s 3 "Map loading"
    # Start spoofing first — required for JoystickOverlayService to bind correctly.
    # content-desc is "Start location simulation"; "location simulation" is a safe substring.
    tap_text "location simulation"
    wait_s 3 "Starting simulation"
    # Show joystick via widget panel toggle (EXTRA_SHOW_OVERLAY via am startservice is
    # unreliable when the service is already running — the extra is not re-delivered).
    show_joystick_via_widget
    # Collapse the widget panel so the joystick is the focus of the screenshot.
    collapse_widget_panel
  else
    pause_for_user "Start mock location then enable the Floating Joystick.
    The joystick overlay should be visible on screen before you press ENTER.
    Tip: Map screen → start spoofing → enable joystick from widget or drawer."
  fi
  screenshot "14_joystick_overlay"
fi

# ── 14. Floating widget ──────────────────────────────────────────────────────

if should_run_step "14"; then
  log "=== 14 FLOATING WIDGET ==="
  if $AUTO; then
    # Widget service already running; expand the panel for the screenshot.
    expand_widget_panel
  else
    pause_for_user "Dismiss the joystick (if open) and enable the Floating Widget instead.
    The widget bubble should be visible on screen before you press ENTER."
  fi
  screenshot "15_widget_overlay"
fi

# ── 15. Routes add button (FAB) ───────────────────────────────────────────────

if should_run_step "15"; then
  log "=== 15 ROUTES ADD BUTTON ==="
  go_idle
  tap_text_below "Routes" "$CARD_Y_MIN"
  wait_s 2 "Routes loading"
  tap_text "Add route"
  wait_s 1 "Add menu opening"
  screenshot "16_routes_add_button"
fi

# ── 16. Favorites add button (FAB) ────────────────────────────────────────────

if should_run_step "16"; then
  log "=== 16 FAVORITES ADD BUTTON ==="
  go_idle
  tap_text_below "Favorites" "$CARD_Y_MIN"
  wait_s 2 "Favorites loading"
  tap_text "Add favorite"
  wait_s 1 "Add menu opening"
  screenshot "17_favorites_add_button"
fi

# ── 17. Group Sync screen ────────────────────────────────────────────────────

if should_run_step "17"; then
  log "=== 17 GROUP SYNC ==="
  go_idle
  # Open the navigation drawer and tap "Group Sync".
  tap_text "Open navigation drawer"
  wait_s 1 "Drawer opening"
  tap_text "Group Sync"
  wait_s 2 "Group Sync loading"
  screenshot "17_group_sync"
fi

# ── Done ─────────────────────────────────────────────

demo_mode_exit
trap - EXIT

# Generate Play Store variants for all captured screenshots
generate_playstore_variants

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Screenshots complete: $OUTPUT_DIR"
echo ""
ls -1 "$OUTPUT_DIR"/*.png 2>/dev/null | while read -r f; do
  SIZE=$(du -h "$f" | cut -f1)
  DIMS=$( sips -g pixelWidth -g pixelHeight "$f" 2>/dev/null \
    | awk '/pixelWidth/{w=$2} /pixelHeight/{h=$2} END{print w"x"h}' \
    || echo "?" )
  printf "  %-35s  %s  %s\n" "$(basename "$f")" "$DIMS" "$SIZE"
done
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
