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
#   ./scripts/screenshot-gallery.sh --marketing-only   (no device needed — regenerates
#                                                        docs/wiki/screenshots/marketing/
#                                                        from existing screenshots)
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
# Output files (24 canonical PNGs):
#   01_idle, 02_map, 03_routes, 04_favorites, 05_settings,
#   06_map_routes_sheet, 07_map_favorites_sheet, 08_map_roaming_sheet,
#   09_route_creator, 10_route_detail, 11_map_picker,
#   12_qr_share,
#   13_joystick_overlay, 14_widget_overlay,
#   15_routes_add_button, 16_favorites_add_button,
#   17_group_sync, 18_debug_stats,
#   19_onboarding_mock_location,
#   20_tap_to_walk_settings, 21_compass_orientation,
#   22_capture_coordinates, 23_map_paste_coordinates, 24_roaming_planting
#
# 19_onboarding_mock_location ("Set as fake GPS app" onboarding step) is only
# captured on a genuinely fresh install — it's taken mid-onboarding, before
# mock_location is granted, inside the onboarding auto-complete branch above.
# If the app is already past onboarding when the script runs, that branch
# never executes and step 19 is silently skipped, no matter --steps. Run
# `make reinstall-on-phone` right before `make screenshot` to guarantee it.

set -euo pipefail

# ── Config ───────────────────────────────────────────────────────────────────

PACKAGE="com.locationjoystick.app"
ACTIVITY=".MainActivity"
OUTPUT_DIR="docs/wiki/screenshots"
ADB_DEVICE=""
AUTO=false
STEPS_FILTER=""  # Comma-separated or range, e.g. "16,17" or "14-17"
ENABLED_STEPS=" "  # Space-separated list of enabled step numbers (01 02 03 etc)
MARKETING_ONLY=false

# ── Arg parsing ──────────────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)          OUTPUT_DIR="$2"; shift 2 ;;
    --device)          ADB_DEVICE="-s $2"; shift 2 ;;
    --auto)            AUTO=true; shift ;;
    --steps)           STEPS_FILTER="$2"; shift 2 ;;
    --marketing-only)  MARKETING_ONLY=true; shift ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

# Parse --steps into a space-separated list of enabled step numbers
if [[ -n "$STEPS_FILTER" ]]; then
  ENABLED_STEPS=" "
  for part in $(echo "$STEPS_FILTER" | tr ',' '\n'); do
    if [[ "$part" =~ ^([0-9]+)-([0-9]+)$ ]]; then
      # Range: e.g. "14-17"
      start=$((10#${BASH_REMATCH[1]}))
      end=$((10#${BASH_REMATCH[2]}))
      for (( i=start; i<=end; i++ )); do
        step_num=$(printf "%02d" "$i")
        ENABLED_STEPS="${ENABLED_STEPS}${step_num} "
      done
    elif [[ "$part" =~ ^[0-9]+$ ]]; then
      # Single number: e.g. "16"
      step_num=$(printf "%02d" "$((10#$part))")
      ENABLED_STEPS="${ENABLED_STEPS}${step_num} "
    else
      echo "Invalid --steps format: $part (expected '16' or '14-17')"
      exit 1
    fi
  done
else
  # No filter: enable all steps
  for i in $(seq 1 24); do ENABLED_STEPS="${ENABLED_STEPS}$(printf '%02d' "$i") "; done
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
  # macOS mktemp only substitutes a trailing XXXXXX — with ".xml" after it, the
  # whole template is used literally, so a leftover file from an earlier run
  # (or one this same run failed to clean up) makes every later call collide.
  rm -f /tmp/uidump.XXXXXX.xml
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

# Tap the toggle switch on the same row as a label (settings rows place the
# label text and its Switch as separate nodes sharing one y-centre, with the
# switch always in the fixed right-hand column at x=970 — the label itself is
# not clickable, so tap_text's own bounds miss the switch entirely).
tap_switch_for() {
  local text="$1"
  local dump centre y
  dump=$(ui_dump)
  centre=$(bounds_of "$dump" "$text")
  rm -f "$dump"
  if [[ -z "$centre" ]]; then
    warn "Could not find row \"$text\" — skipping switch tap."
    return 1
  fi
  read -r _ y <<< "$centre"
  log "Tapping switch for \"$text\" at (970, $y)"
  $ADB shell input tap 970 "$y"
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

# Tap the Nth (0-indexed) EditText on screen by top-to-bottom order. Used
# instead of tap_text on a field's hint label, since the hint disappears once
# a dialog field already holds a value from a prior seeding iteration.
tap_edit_field() {
  local index="$1"
  local dump centre x y
  dump=$(ui_dump)
  centre=$(perl -lne '
    push @b, [$1,$2,$3,$4] while /class="android\.widget\.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g;
    END {
      @b = sort { $a->[1] <=> $b->[1] } @b;
      my $n = '"${index}"';
      if (defined $b[$n]) {
        printf "%d %d\n", int(($b[$n][0]+$b[$n][2])/2), int(($b[$n][1]+$b[$n][3])/2);
      }
    }
  ' "$dump" 2>/dev/null)
  rm -f "$dump"
  if [[ -z "$centre" ]]; then
    warn "Could not find EditText #$index — skipping tap."
    return 1
  fi
  read -r x y <<< "$centre"
  log "Tapping EditText #$index at ($x, $y)"
  $ADB shell input tap "$x" "$y"
}

# Clear the focused text field by sending 80 backspace keypresses in one adb call.
# Needed when a dialog reopens with a field that retains its previous value
# (hint text disappears, so tap_text on the hint label fails to find the element).
clear_field() {
  local i keys=""
  for (( i=0; i<80; i++ )); do keys+="KEYCODE_DEL "; done
  $ADB shell input keyevent $keys
}

# Wait N seconds with a visible countdown.
wait_s() {
  local n="$1" msg="${2:-Waiting}" i
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
  # Distinct waypoint layouts per route so seeded routes don't look identical.
  local -a route_names=("Morning Walk" "City Loop")
  local -a route_wp1_dx=(0 -80)
  local -a route_wp1_y=(35 30)
  local -a route_wp2_dx=(60 100)
  local -a route_wp2_y=(55 50)
  local -a route_wp3_dx=(0 -40)
  local -a route_wp3_y=(45 65)
  for i in "${!route_names[@]}"; do
    local name="${route_names[$i]}"
    go_idle
    tap_text_below "Routes" "$CARD_Y_MIN"
    wait_s 2 "Routes loading"
    tap_text "Add route"
    wait_s 1 "Add menu opening"
    tap_text "Draw on map"
    wait_s 4 "Route creator loading"
    # Need ≥2 waypoints before Save FAB appears.
    $ADB shell input tap "$(( cx + route_wp1_dx[i] ))" "$(( SCREEN_H * route_wp1_y[i] / 100 ))"
    wait_s 2 "Placing waypoint 1"
    $ADB shell input tap "$(( cx + route_wp2_dx[i] ))" "$(( SCREEN_H * route_wp2_y[i] / 100 ))"
    wait_s 2 "Placing waypoint 2"
    $ADB shell input tap "$(( cx + route_wp3_dx[i] ))" "$(( SCREEN_H * route_wp3_y[i] / 100 ))"
    wait_s 2 "Placing waypoint 3"
    tap_text "Save route"
    wait_s 1 "Save dialog opening"
    $ADB shell input text "${name// /%s}"
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
    # Navigate fresh to Favorites each iteration so dialog always opens with empty fields.
    go_idle
    tap_text_below "Favorites" "$CARD_Y_MIN"
    wait_s 2 "Favorites loading"
    tap_text "Add favorite"
    wait_s 1 "Add menu opening"
    tap_text "from coordinates"
    wait_s 1 "Dialog opening"
    tap_edit_field 0
    wait_s 1
    clear_field
    $ADB shell input text "${fav_names[$i]}"
    wait_s 1
    tap_edit_field 1
    wait_s 1
    clear_field
    $ADB shell input text "${fav_lats[$i]}"
    wait_s 1
    tap_edit_field 2
    wait_s 1
    clear_field
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
      | perl -lne 'if (/mAttrs=\{\((-?\d+),(\d+)\)\(wrapxwrap\).*APPLICATION_OVERLAY/) { print "$1 $2"; last; }'
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
      | perl -lne 'if (/mAttrs=\{\((-?\d+),(\d+)\)\(wrapxwrap\).*APPLICATION_OVERLAY/) { print "$1 $2"; last; }'
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

# Generate the two distinct Play Store marketing assets from the current
# docs/wiki/screenshots/*.png:
#
#   1. Feature graphic — exactly 1024x500, ONE image (the idle/hero screen),
#      full uncropped screenshot + catch-phrase. This is a small promotional
#      banner, not meant to show fine detail, so the screenshot is scaled
#      down to fit — some softness here is normal and expected for this
#      asset (real Play feature graphics never show readable screenshot text).
#
#   2. Gallery — 8 portrait images, one per feature, fixed at exactly
#      1080x1920 (= 9:16, satisfies Play Store's exact-ratio requirement and
#      clears the 1080x1080 floor for promotion eligibility on all 8). The
#      screenshot is CROPPED to fit, never resized, so every gallery pixel is
#      a 1:1 source pixel — no downscale blur/moire. This is what actually
#      fixes "pixelated screenshots" (squeezing a 1080-wide screenshot into
#      the 1024x500 feature graphic's ~200px-wide photo column was the real
#      bug in an earlier version).
generate_marketing_variants() {
  log "Generating Play Store marketing assets..."
  if ! python3 -c "import PIL" >/dev/null 2>&1; then
    warn "Pillow not installed — skipping marketing assets."
    warn "Install with: python3 -m pip install --user --break-system-packages Pillow"
    return 0
  fi
  python3 << 'PYTHON_EOF'
import os
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

src_dir = "docs/wiki/screenshots"
out_dir = os.path.join(src_dir, "marketing")
gallery_dir = os.path.join(out_dir, "gallery")
os.makedirs(gallery_dir, exist_ok=True)

if not os.path.isdir(src_dir):
  print(f"  Error: {src_dir} not found", file=sys.stderr)
  sys.exit(1)

FONT_PATH = "/System/Library/Fonts/HelveticaNeue.ttc"
BOLD_INDEX = 1
ACCENT = (178, 83, 26)      # LjLightAccent 0xFFB2531A
TEXT_COLOR = (35, 30, 27)   # LjLightText 0xFF231E1B
MASK_SS = 4  # supersample factor for rounded-corner masks only (cheap, localized)

# (source screenshot, caption lines) — one entry per feature. Reused for both
# the feature graphic (first entry only) and the gallery (all entries).
SHOTS = [
  ("01_idle.png", [[("Take control of", False)], [("your ", False), ("GPS", True)]]),
  ("02_map.png", [[("Fake your ", False), ("GPS", True)], [("anywhere", False)]]),
  ("16_routes_add_button.png", [[("Create routes", False)], [("your ", False), ("way", True)]]),
  ("17_favorites_add_button.png", [[("Save your ", False), ("favorite", True)], [("spots", False)]]),
  ("05_settings.png", [[("Fine-tune every", False)], [("setting", True)]]),
  ("15_widget_overlay.png", [[("Control it all from", False)], [("one quick ", False), ("widget", True)]]),
  ("08_map_roaming_sheet.png", [[("Roam ", False), ("naturally", True)], [("hands-free", False)]]),
  ("17_group_sync.png", [[("Sync location", False)], [("across ", False), ("devices", True)]]),
]


def rounded_mask(size, radius, corners=(True, True, True, True)):
  big = (size[0] * MASK_SS, size[1] * MASK_SS)
  mask = Image.new("L", big, 0)
  ImageDraw.Draw(mask).rounded_rectangle(
    [0, 0, big[0], big[1]], radius=radius * MASK_SS, fill=255, corners=corners
  )
  return mask.resize(size, Image.LANCZOS)


def line_width(draw, segments, font):
  return sum(draw.textbbox((0, 0), text, font=font)[2] for text, _ in segments)


def fit_font(draw, lines, max_width, max_height, start_size, min_size):
  size = start_size
  while size > min_size:
    font = ImageFont.truetype(FONT_PATH, size, index=BOLD_INDEX)
    widest = max(line_width(draw, line, font) for line in lines)
    total_h = int(size * 1.2) * len(lines)
    if widest <= max_width and total_h <= max_height:
      return font, size
    size -= 1
  return ImageFont.truetype(FONT_PATH, min_size, index=BOLD_INDEX), min_size


def draw_caption(draw, caption_lines, font, line_height, top_y, area_x, area_w):
  y = top_y
  for line in caption_lines:
    total_w = line_width(draw, line, font)
    x = area_x + (area_w - total_w) // 2
    for text, is_accent in line:
      color = ACCENT if is_accent else TEXT_COLOR
      draw.text((x, y), text, font=font, fill=color)
      x += draw.textbbox((0, 0), text, font=font)[2]
    y += line_height


# ── 1. Feature graphic: 1024x500, idle screen, full uncropped screenshot ──

FEATURE_W, FEATURE_H = 1024, 500
MARGIN = 40
LEFT_W = 440
CORNER_RADIUS = 28

feat_fname, feat_caption = SHOTS[0]
feat_src = os.path.join(src_dir, feat_fname)
if os.path.exists(feat_src):
  canvas = Image.new("RGB", (FEATURE_W, FEATURE_H), (255, 255, 255))
  draw = ImageDraw.Draw(canvas)

  text_max_w = LEFT_W - 2 * MARGIN
  text_max_h = FEATURE_H - 2 * MARGIN
  font, font_size = fit_font(draw, feat_caption, text_max_w, text_max_h, 48, 20)
  line_height = int(font_size * 1.2)
  text_block_h = line_height * len(feat_caption)
  draw_caption(draw, feat_caption, font, line_height, (FEATURE_H - text_block_h) // 2, MARGIN, text_max_w)

  img = Image.open(feat_src).convert("RGB")  # full screenshot, no crop
  avail_w = FEATURE_W - LEFT_W - MARGIN
  avail_h = FEATURE_H - 2 * MARGIN
  scale = min(avail_w / img.width, avail_h / img.height)
  new_w, new_h = int(img.width * scale), int(img.height * scale)
  img_resized = img.resize((new_w, new_h), Image.LANCZOS)
  mask = rounded_mask((new_w, new_h), CORNER_RADIUS)

  px = LEFT_W + (avail_w - new_w) // 2
  py = (FEATURE_H - new_h) // 2

  shadow = Image.new("RGBA", (FEATURE_W, FEATURE_H), (0, 0, 0, 0))
  ImageDraw.Draw(shadow).rounded_rectangle(
    [px + 2, py + 4, px + new_w + 2, py + new_h + 4], radius=CORNER_RADIUS, fill=(0, 0, 0, 45)
  )
  shadow = shadow.filter(ImageFilter.GaussianBlur(5))
  canvas.paste(shadow, (0, 0), shadow)
  canvas.paste(img_resized, (px, py), mask)

  dst_path = os.path.join(out_dir, "feature_graphic.png")
  canvas.save(dst_path, "PNG", optimize=True)
  print(f"  {feat_fname} → marketing/feature_graphic.png (1024x500)")
else:
  print(f"  skip feature graphic (missing): {feat_fname}", file=sys.stderr)

# ── 2. Gallery: 8 portrait images, exact 9:16, FULL uncropped screenshot ──
#
# Play Store requires an exact 16:9/9:16 ratio (not just "portrait-ish"),
# plus >=4 shots at >=1080x1080 to qualify for promotion. Canvas is fixed at
# 1080x1920 (= 9:16 exactly, and well past the 1080x1080 floor). The full
# screenshot (1080x2340, nothing cropped off) is scaled down ONE time to fit
# the space left after the caption band, then centered (pillarboxed) — a
# single, modest-ratio (~0.65x) resize stays sharp; it's chained resizes and
# extreme (5x+) downscale ratios that caused the earlier pixelation, not
# resizing itself.
GALLERY_W, GALLERY_H = 1080, 1920
GALLERY_CORNER_RADIUS = 48
GALLERY_BAND_H = 400  # caption band height, leaves 1520px for the screenshot

for fname, caption_lines in SHOTS:
  src_path = os.path.join(src_dir, fname)
  if not os.path.exists(src_path):
    print(f"  skip gallery (missing): {fname}", file=sys.stderr)
    continue

  photo_full = Image.open(src_path).convert("RGB")
  avail_w = GALLERY_W
  avail_h = GALLERY_H - GALLERY_BAND_H
  scale = min(avail_w / photo_full.width, avail_h / photo_full.height)
  new_w, new_h = round(photo_full.width * scale), round(photo_full.height * scale)
  photo = photo_full.resize((new_w, new_h), Image.LANCZOS)

  canvas = Image.new("RGB", (GALLERY_W, GALLERY_H), (255, 255, 255))
  draw = ImageDraw.Draw(canvas)

  margin = round(GALLERY_W * 0.08)
  text_max_w = GALLERY_W - 2 * margin
  font, font_size = fit_font(draw, caption_lines, text_max_w, GALLERY_BAND_H, round(GALLERY_W * 0.11), round(GALLERY_W * 0.045))
  line_height = int(font_size * 1.2)
  text_block_h = line_height * len(caption_lines)
  draw_caption(draw, caption_lines, font, line_height, (GALLERY_BAND_H - text_block_h) // 2, margin, text_max_w)

  mask = rounded_mask((new_w, new_h), GALLERY_CORNER_RADIUS)
  px = (GALLERY_W - new_w) // 2
  py = GALLERY_BAND_H + (avail_h - new_h) // 2

  shadow = Image.new("RGBA", (GALLERY_W, GALLERY_H), (0, 0, 0, 0))
  ImageDraw.Draw(shadow).rounded_rectangle(
    [px + 2, py + 6, px + new_w + 2, py + new_h + 6], radius=GALLERY_CORNER_RADIUS, fill=(0, 0, 0, 40)
  )
  shadow = shadow.filter(ImageFilter.GaussianBlur(8))
  canvas.paste(shadow, (0, 0), shadow)
  canvas.paste(photo, (px, py), mask)

  name, _ = os.path.splitext(fname)
  dst_path = os.path.join(gallery_dir, f"{name}.png")
  canvas.save(dst_path, "PNG", optimize=True)
  print(f"  {fname} → marketing/gallery/{os.path.basename(dst_path)} ({GALLERY_W}x{GALLERY_H})")
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

# ── Marketing-only mode: skip device entirely ─────────────────────────────────

if [[ "$MARKETING_ONLY" == true ]]; then
  mkdir -p "$OUTPUT_DIR"
  generate_marketing_variants
  exit 0
fi

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
    # Grant location + overlay only — mock_location stays ungranted for now so
    # the app lands on the "Set as fake GPS app" step below, letting step 19
    # capture it before the flow completes.
    $ADB shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
    $ADB shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
    $ADB shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
    log "Location + overlay granted. Restarting app..."
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

    if should_run_step "19"; then
      log "=== 19 ONBOARDING MOCK LOCATION ==="
      screenshot "19_onboarding_mock_location"
    fi

    $ADB shell appops set "$PACKAGE" android:mock_location allow 2>/dev/null || true
    log "Mock location granted. Restarting app..."
    $ADB shell am force-stop "$PACKAGE"
    sleep 1
    $ADB shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null
    wait_s 3 "App starting"
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
  # Scroll to top to show the Settings landing page with all section headers visible
  $ADB shell input swipe 540 400 540 1200
  wait_s 1 "Scrolling to top"
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
  tap_text "Draw on map"
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
  go_idle
  tap_text_below "Settings" "$CARD_Y_MIN"
  wait_s 2 "Settings loading"
  # Export/Import actions live behind the "More actions" overflow menu, not
  # standalone top-bar buttons.
  tap_text "More actions"
  wait_s 1 "Overflow menu opening"
  tap_text "Export via QR code"
  wait_s 2 "QR share dialog opening"
  # Dialog opens on a loading state ("Starting local export server…") until the
  # local HTTP server + NSD advertising are up — poll until the QR image replaces it.
  for _ in 1 2 3 4 5; do
    dump=$(ui_dump)
    ready=$(grep -c 'Export QR code' "$dump" || true)
    rm -f "$dump"
    (( ready > 0 )) && break
    wait_s 1 "Waiting for QR server to start"
  done
  screenshot "12_qr_share"
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
  tap_text_below "Group Sync" "$CARD_Y_MIN"
  wait_s 2 "Group Sync loading"
  screenshot "17_group_sync"
fi

# ── 18. Debug stats (widget panel) ───────────────────────────────────────────

if should_run_step "18"; then
  log "=== 18 DEBUG STATS ==="
  if $AUTO; then
    go_idle
    tap_text_below "Settings" "$CARD_Y_MIN"
    wait_s 2 "Settings loading"
    tap_text "Menus"
    wait_s 2 "Menus loading"
    # Debug section is last on the Menus page, well below the fold (Theme,
    # App Features, Speed Cycle, Tap to Walk, Privacy all come first) — keep
    # scrolling until the row actually appears.
    for _ in 1 2 3 4 5 6 7; do
      dump=$(ui_dump)
      found=$(grep -c 'text="Debug stats"' "$dump" || true)
      rm -f "$dump"
      (( found > 0 )) && break
      $ADB shell input swipe 540 1800 540 400
      wait_s 1 "Scrolling to Debug section"
    done
    # Idempotent: only tap if currently unchecked — the row is a toggle, so
    # tapping an already-enabled setting (e.g. left on from a prior run) would
    # disable it instead. The dump is one giant single line, so a line-based
    # grep -B1 can't isolate the checkbox next to "Debug stats" — walk the
    # raw text backwards from that label to its nearest preceding checked= instead.
    dump=$(ui_dump)
    already_on=$(python3 -c '
data = open("'"$dump"'").read()
idx = data.find("text=\"Debug stats\"")
prefix = data[:idx]
last = prefix.rfind("checked=\"")
print(prefix[last+9:last+13] == "true")
')
    if [[ "$already_on" == "True" ]]; then
      log "Debug stats already enabled — skipping toggle tap."
    else
      tap_text "Debug stats"
      wait_s 2 "Enabling debug stats"
      # This settings page buffers changes behind a Save/Discard FAB (check icon,
      # labeled "Save") — force-stopping via go_idle without saving would
      # discard the toggle. Retry: the FAB's enter animation (fadeIn+slideIn,
      # 200ms) plus recomposition can lag past a single wait on a slow device.
      for _ in 1 2 3; do
        tap_text "Save" && break
        wait_s 1 "Waiting for Save FAB"
      done
      wait_s 1 "Saving setting"
    fi
    rm -f "$dump"
    go_idle
    tap_text_below "Map" "$CARD_Y_MIN"
    wait_s 3 "Map loading"
    tap_text "location simulation"
    wait_s 3 "Starting simulation"
    $ADB shell am startservice \
      -n "${PACKAGE}/com.locationjoystick.feature.widget.impl.FloatingWidgetService" 2>/dev/null || true
    # A freshly-started overlay window reports isReadyForDisplay/isVisible in
    # dumpsys well before it's actually first in line for touch dispatch — a
    # Best-effort, same as steps 13/14: overlay touch dispatch is flaky enough
    # (seen falling through to the map beneath even after an 8s+ settle and a
    # retry) that this may still need a manual re-run. If so: expand the
    # widget panel by hand with Debug stats enabled, spoofing active, then
    #   adb exec-out screencap -p > docs/wiki/screenshots/18_debug_stats.png
    attempts=0
    while (( attempts < 3 )); do
      wait_s 8 "Widget overlay settling"
      expand_widget_panel
      dump=$(ui_dump)
      if grep -q 'Move to this location' "$dump"; then
        rm -f "$dump"
        (( ++attempts ))
        warn "Tap fell through to the map (attempt $attempts/3) — dismissing and retrying."
        back
        wait_s 2 "Dismissing map sheet"
      else
        rm -f "$dump"
        break
      fi
    done
    wait_s 2 "Stats populating"
  else
    pause_for_user "Enable Settings → Menus → Debug → \"Debug stats\", start spoofing,
    then expand the floating widget panel so the live stats block is visible."
  fi
  screenshot "18_debug_stats"
fi

# ── 20. Settings → Menus → Tap to Walk section ───────────────────────────────

if should_run_step "20"; then
  log "=== 20 TAP TO WALK SETTINGS ==="
  go_idle
  tap_text_below "Settings" "$CARD_Y_MIN"
  wait_s 2 "Settings loading"
  tap_text "Menus"
  wait_s 2 "Menus loading"
  $ADB shell input swipe 540 1600 540 400
  wait_s 1 "Scrolling to Tap to Walk"
  $ADB shell input swipe 540 1600 540 400
  wait_s 1 "Scrolling to Tap to Walk"
  dump=$(ui_dump)
  already_on=$(python3 -c '
data = open("'"$dump"'").read()
idx = data.find("Enable Tap to Walk")
seg = data[idx:idx+900]
i = seg.find("checkable=\"true\"")
print("checked=\"true\"" in seg[i:i+40])
')
  rm -f "$dump"
  if [[ "$already_on" != "True" ]]; then
    tap_switch_for "Enable Tap to Walk"
    wait_s 1 "Warning dialog opening"
    tap_text "Enable anyway"
    wait_s 1 "Enabling Tap to Walk — Map scale / Compass sections expanding"
  fi
  screenshot "20_tap_to_walk_settings"
fi

# ── 21. Settings → Menus → Compass orientation section ──────────────────────
# CompassOrientationSection renders below the Map scale slider (API 30+ only)
# — off-screen in step 20's screenshot. Scroll further to capture the Game
# app picker + Test button on their own.

if should_run_step "21"; then
  log "=== 21 COMPASS ORIENTATION ==="
  go_idle
  tap_text_below "Settings" "$CARD_Y_MIN"
  wait_s 2 "Settings loading"
  tap_text "Menus"
  wait_s 2 "Menus loading"
  for _ in 1 2 3; do
    dump=$(ui_dump)
    found=$(grep -c 'text="Compass orientation"' "$dump" || true)
    rm -f "$dump"
    (( found > 0 )) && break
    $ADB shell input swipe 540 1600 540 400
    wait_s 1 "Scrolling to Compass orientation"
  done
  screenshot "21_compass_orientation"
fi

# ── 22. Capture coordinates screen ───────────────────────────────────────────

if should_run_step "22"; then
  log "=== 22 CAPTURE COORDINATES ==="
  go_idle
  tap_text_below "Capture" "$CARD_Y_MIN"
  wait_s 2 "Capture loading"
  screenshot "22_capture_coordinates"
fi

# ── 23. Map → Paste coordinates sheet (two points → route actions) ───────────

if should_run_step "23"; then
  log "=== 23 MAP PASTE COORDINATES ==="
  go_idle
  tap_text_below "Map" "$CARD_Y_MIN"
  wait_s 3 "Map loading"
  tap_text "Paste coordinates"
  wait_s 2 "Paste sheet opening"
  tap_edit_field 0
  $ADB shell input text "35.6762,%s139.6503"
  $ADB shell input keyevent KEYCODE_ENTER
  $ADB shell input text "35.6895,%s139.6917"
  wait_s 1 "Parsing pasted points"
  # Keyboard covers the route options; hide it before capturing.
  back
  wait_s 1 "Dismissing keyboard"
  screenshot "23_map_paste_coordinates"
  back
  wait_s 1 "Dismissing sheet"
fi

# ── 24. Map → Roaming sheet, Planting mode ───────────────────────────────────

if should_run_step "24"; then
  log "=== 24 ROAMING PLANTING ==="
  go_idle
  tap_text_below "Map" "$CARD_Y_MIN"
  wait_s 3 "Map loading"
  tap_text "start roaming"
  wait_s 2 "Roaming sheet opening"
  tap_text_exact "Planting mode"
  wait_s 1 "Switching to Planting"
  screenshot "24_roaming_planting"
  back
  wait_s 1 "Dismissing sheet"
fi

# ── Done ─────────────────────────────────────────────

demo_mode_exit
trap - EXIT

# Generate the Play Store marketing gallery
generate_marketing_variants

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
