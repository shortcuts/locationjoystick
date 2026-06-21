#!/usr/bin/env bash
# playstore-foreground-location-video.sh
#
# Records the Play Console "FOREGROUND_SERVICE_LOCATION" demo video.
# Demonstrates the "background location notifications" use case: starting
# spoofing shows a persistent foreground-service notification, the
# notification survives backgrounding the app, and stopping spoofing clears it.
#
# Usage:
#   ./scripts/playstore-foreground-location-video.sh
#   ./scripts/playstore-foreground-location-video.sh --output demo.mp4
#   ./scripts/playstore-foreground-location-video.sh --device emulator-5554
#
# Prerequisites:
#   - adb in PATH, device connected with USB debugging on
#   - App installed and past onboarding (permissions granted)
#
# Recording is driven by `adb shell screenrecord` in the background, stopped
# with SIGINT (so the mp4 finalizes correctly) once the scripted flow ends.

set -euo pipefail

PACKAGE="com.locationjoystick.app"
ACTIVITY=".MainActivity"
OUTPUT="playstore_foreground_location_demo.mp4"
ADB_DEVICE=""
DEVICE_PATH="/sdcard/playstore_demo.mp4"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) OUTPUT="$2"; shift 2 ;;
    --device) ADB_DEVICE="-s $2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

ADB="adb $ADB_DEVICE"

log()  { echo "▶ $*"; }
warn() { echo "⚠ $*"; }

ui_dump() {
  local tmp
  tmp=$(mktemp /tmp/uidump.XXXXXX.xml)
  $ADB shell uiautomator dump /sdcard/uidump.xml >/dev/null 2>&1
  $ADB pull /sdcard/uidump.xml "$tmp" >/dev/null 2>&1
  echo "$tmp"
}

bounds_of() {
  local dump="$1" term="$2"
  perl -lne '
    if (/(?:text|content-desc)="[^"]*'"${term}"'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/i) {
      printf "%d %d\n", int(($1+$3)/2), int(($2+$4)/2);
      last;
    }
  ' "$dump" 2>/dev/null
}

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

wait_s() {
  local n="$1" msg="${2:-Waiting}"
  for (( i=n; i>0; i-- )); do
    printf "\r  %s… %ds " "$msg" "$i"
    sleep 1
  done
  printf "\r%*s\r" 40 ""
}

go_idle() {
  log "Returning to IdleScreen..."
  $ADB shell am force-stop "$PACKAGE"
  sleep 1
  $ADB shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null
  wait_s 3 "App starting"
}

demo_mode_enter() {
  log "Entering demo mode (clean status bar)..."
  $ADB shell settings put global sysui_demo_allowed 1 2>/dev/null || true
  $ADB shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null 2>&1 || true
  $ADB shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null 2>&1 || true
  $ADB shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null 2>&1 || true
  $ADB shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4 -e datatype lte -e wifi show -e level 4 >/dev/null 2>&1 || true
  $ADB shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null 2>&1 || true
}

demo_mode_exit() {
  $ADB shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  log "Demo mode exited"
}

start_recording() {
  log "Starting screen recording → $DEVICE_PATH"
  $ADB shell rm -f "$DEVICE_PATH" 2>/dev/null || true
  $ADB shell screenrecord --bit-rate 8000000 "$DEVICE_PATH" &
  RECORD_BG_PID=$!
  wait_s 2 "Recorder warming up"
}

stop_recording() {
  log "Stopping screen recording..."
  $ADB shell pkill -INT screenrecord 2>/dev/null || true
  wait_s 2 "Finalizing recording"
  wait "$RECORD_BG_PID" 2>/dev/null || true
  log "Pulling $DEVICE_PATH → $OUTPUT"
  $ADB pull "$DEVICE_PATH" "$OUTPUT" >/dev/null
  $ADB shell rm -f "$DEVICE_PATH" 2>/dev/null || true
}

# ── Setup ────────────────────────────────────────────────────────────────────

log "Checking device..."
if ! $ADB devices | grep -q "device$"; then
  echo "Error: no device found. Connect a device or pass --device <serial>."
  exit 1
fi

log "Checking app installation..."
if ! $ADB shell pm list packages 2>/dev/null | grep -q "$PACKAGE"; then
  echo "Error: $PACKAGE not installed. Run 'make install-on-phone' first."
  exit 1
fi

log "Waking and unlocking screen..."
$ADB shell input keyevent KEYCODE_WAKEUP
sleep 1
$ADB shell input keyevent KEYCODE_MENU 2>/dev/null || true
$ADB shell input swipe 540 1800 540 600 2>/dev/null || true
sleep 1

demo_mode_enter
trap 'demo_mode_exit' EXIT

go_idle
dump=$(ui_dump)
if grep -qi "onboarding\|Welcome\|grant\|permission" "$dump" 2>/dev/null; then
  rm -f "$dump"
  echo "Error: app is on the onboarding screen. Complete onboarding then re-run."
  exit 1
fi
rm -f "$dump"

# ── Recorded flow ────────────────────────────────────────────────────────────

start_recording

log "=== Open Map screen ==="
tap_text_below "Map" 1
wait_s 2 "Map loading"

log "=== Start spoofing (foreground service starts, notification appears) ==="
tap_text "location simulation"
wait_s 2 "Simulation starting"

log "=== Pull down notification shade to reveal the persistent notification ==="
$ADB shell cmd statusbar expand-notifications
wait_s 3 "Showing notification"
$ADB shell cmd statusbar collapse
wait_s 1 "Collapsing shade"

log "=== Send app to background (notification keeps running the service) ==="
$ADB shell input keyevent KEYCODE_HOME
wait_s 2 "On home screen"
$ADB shell cmd statusbar expand-notifications
wait_s 3 "Notification persists in background"
$ADB shell cmd statusbar collapse
wait_s 1 "Collapsing shade"

log "=== Return to app ==="
$ADB shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null
wait_s 3 "App resuming"

log "=== Stop spoofing (foreground service stops, notification clears) ==="
tap_text "location simulation" || tap_text "stop"
wait_s 2 "Simulation stopping"

stop_recording

demo_mode_exit
trap - EXIT

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Video saved: $OUTPUT"
ls -lh "$OUTPUT" 2>/dev/null | awk '{print "  " $5 "  " $9}'
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
