#!/usr/bin/env python3
"""Reliability-proof harness for the Bluetooth reconnect tap (docs/features/bluetooth-reconnect-tap.md).

Subcommands (stdlib plus adb on PATH; `auto` also needs numpy and Pillow; raw results go to
scripts/reconnect-matrix/, gitignored):
    run [--serial S] [--mac-suffix D3:24] [--device-slug pixel7] --block a|b|c|d|e --cycles 20
        Operator disconnects the companion and presses Enter; the harness waits for the app's
        check (one per loop, 10 s debug / 60 s release), then confirms the link with dumpsys. One row per cycle.
    already-on [--serial S] [--mac-suffix D3:24] [--device-slug pixel7] --checks 12
        Companion connected; every check must read ON, tap nothing and return NOT_OFF.
    auto [--serial S] [--mac-suffix D3:24] [--device-slug pixel7] --block a|b|c|d|e --cycles 100
        No operator per cycle: taps the game's connect control while it reads ON to drop the companion
        link (a software disconnect; it does not cover the accessory powering off or going out of range),
        falling back to cycling the phone's Bluetooth adapter,
        waits for the app's check, and saves a before and after screenshot per cycle named with
        its host-side class (ON/OFF/NONE) for manual review in scripts/reconnect-matrix/shots/.
        Block c (screen off, then foreground the game) is driven with adb keyevents: SLEEP, 60 s,
        WAKEUP, dismiss the swipe lock screen, launch the game.
        At 10 s per loop (debug build) a 100-cycle block takes about 30 minutes.
    corpus [--serial S] [--device-slug pixel7] --state on|off --theme day|night --brightness min|max
        Captures one labelled screencap into scripts/reconnect-matrix/corpus/.
    summary
        Prints per-device/per-block pass counts as a markdown table, then lists each failed cycle.

--serial omitted: the only connected device is used, or you pick from a list.
    --device-slug omitted: taken from the phone's model name.
    --mac-suffix omitted: the bonded 'Pokemon GO Plus' or 'Pokemon GO Plus +' (accents, case, spaces ignored), else the one with a live link, else pick by name from a list.
    --block omitted: a.

Self-check:
    python3 scripts/test_reconnect_matrix.py
"""

import argparse
import json
import queue
import re
import subprocess
import sys
import threading
import time
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "scripts" / "reconnect-matrix"
ROWS = OUT / "cycles.jsonl"
TAG = "ScreenAccessibilitySvc"
LOOP_INTERVAL_S = 60  # AppConstants.ReconnectTapConstants.CHECK_INTERVAL_MS (release); debug is 10 s
CYCLE_TIMEOUT_S = 150  # one loop interval + up to 3 taps with settles

ACL_RE = re.compile(r"aclStateChangeCallback: State:(Connected|Disconnected)")
READ_RE = re.compile(r"Reconnect read: (ON|OFF|NONE)")
TAP_RE = re.compile(r"Reconnect tap: ")
CHECK_RE = re.compile(r"Reconnect check: (NOT_OFF|RECONNECTED|FAILED)")
TERMINAL_RE = re.compile(r"Reconnect check: (RECONNECTED|FAILED)")
TOGGLE_SETTLE_S = 5  # the app needs at least 3 s to load after each connect or disconnect
SHOTS = OUT / "shots"


def parse_acl_state(dumpsys_text, mac_suffix):
    """Companion link state from the masked address's visible tail (e.g. 'D3:24').

    Pixel 7 prints `[XX:..:D3:24] aclStateChangeCallback: State:X` lines, last one wins. Pixel 5
    (Android 14) prints only a connection-history block, `CONNECTED    xx:..:d3:24` and
    `DISCONNECTED xx:..:d3:24 reason=0`, newest first, so the first such line wins.
    """
    state = None
    for line in dumpsys_text.splitlines():
        if f"{mac_suffix}]" in line:
            m = ACL_RE.search(line)
            if m:
                state = m.group(1)
    if state is None:
        m = re.search(
            rf"\b(CONNECTED|DISCONNECTED)\s+\S*(?i:{re.escape(mac_suffix)})(?![0-9A-Fa-f])", dumpsys_text
        )
        if m:
            state = "Connected" if m.group(1) == "CONNECTED" else "Disconnected"
    return state


def classify_logs(lines):
    """Summarise one run's app log lines: final check result, tap count, reads in order."""
    outcome = None
    taps = 0
    reads = []
    for line in lines:
        if (m := CHECK_RE.search(line)):
            outcome = m.group(1)
        if TAP_RE.search(line):
            taps += 1
        if (m := READ_RE.search(line)):
            reads.append(m.group(1))
    return {"outcome": outcome, "taps": taps, "reads": reads}


def cycle_passes(outcome, connected):
    """Plan rule 7: the app reports RECONNECTED and dumpsys confirms the companion is Connected."""
    return outcome == "RECONNECTED" and connected is True


# Port of ReconnectControlDetector.kt (same rules: flat regions, ball by shape, state by relative
# channel dominance, ON checked before OFF). Keep in sync with the Kotlin detector; its fixtures
# are the self-check's fixtures.
SEARCH_X_MIN_PCT = 0.55  # AppConstants.AccessibilityConstants
SEARCH_Y_MAX_PCT = 0.35
FLAT_STEP = 4
MIN_PIXELS = 12


def _flat_regions(region):
    """4-connected regions of flat pixels as (left, top, right, bottom, (r, g, b) mean)."""
    import numpy as np

    h, w = region.shape[:2]
    flat = np.ones((h, w), dtype=bool)
    dx = np.abs(np.diff(region, axis=1)).max(-1) > FLAT_STEP
    flat[:, :-1] &= ~dx
    flat[:, 1:] &= ~dx
    dy = np.abs(np.diff(region, axis=0)).max(-1) > FLAT_STEP
    flat[:-1] &= ~dy
    flat[1:] &= ~dy
    out = []
    for y, x in zip(*np.nonzero(flat)):
        if not flat[y, x]:
            continue
        flat[y, x] = False
        stack = [(int(y), int(x))]
        pts = []
        while stack:
            cy, cx = stack.pop()
            pts.append((cy, cx))
            for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                if 0 <= ny < h and 0 <= nx < w and flat[ny, nx]:
                    flat[ny, nx] = False
                    stack.append((ny, nx))
        if len(pts) >= MIN_PIXELS:
            a = np.array(pts)
            mean = region[a[:, 0], a[:, 1]].mean(axis=0)
            out.append((int(a[:, 1].min()), int(a[:, 0].min()), int(a[:, 1].max()), int(a[:, 0].max()),
                        (int(mean[0]), int(mean[1]), int(mean[2]))))
    return out


def _state(u, low, d):
    """ON, OFF or None from the mean colors of the top half, bottom half and dot."""
    dot_green = d[1] - max(d[0], d[2])
    if dot_green >= 0.4 * d[1] and u[0] - max(u[1], u[2]) >= 0.4 * u[0]:
        return "ON"
    rose_top = u[0] - u[1] >= 0.15 * u[0] and u[0] > u[2]
    blue_bottom = low[2] - low[0] >= 0.3 * low[2] and low[2] >= low[1]
    dark_dot = dot_green < 0.1 * d[1] + 5 and sum(d) <= 0.7 * sum(low)
    return "OFF" if rose_top and blue_bottom and dark_dot else None


def locate_control(png_bytes):
    """Return ("ON"|"OFF", (x, y)) or ("NONE", None): the control's state and its dot's centre in full-screen pixels."""
    import io

    import numpy as np
    from PIL import Image

    rgb = np.asarray(Image.open(io.BytesIO(png_bytes)).convert("RGB"), dtype=np.int32)
    full_h, width = rgb.shape[:2]
    x0 = int(width * SEARCH_X_MIN_PCT)
    region = rgb[: int(full_h * SEARCH_Y_MAX_PCT), x0:]
    flats = _flat_regions(region)
    halves = [
        f for f in flats
        if 0.03 * width <= f[2] - f[0] + 1 <= 0.10 * width
        and 0.2 <= (f[3] - f[1] + 1) / (f[2] - f[0] + 1) <= 0.6
    ]
    off_centre = None
    for u in halves:
        for low in halves:
            if u is low:
                continue
            w = low[2] - low[0] + 1
            uw = u[2] - u[0] + 1
            gap = low[1] - u[3]
            union_w = max(u[2], low[2]) - min(u[0], low[0]) + 1
            union_h = low[3] - u[1] + 1
            if not (
                abs(uw - w) <= 0.2 * w
                and abs((u[0] + u[2]) // 2 - (low[0] + low[2]) // 2) <= 0.1 * w
                and 0 <= gap <= 0.15 * w
                and 0.85 * union_w <= union_h <= 1.15 * union_w
            ):
                continue
            split = (u[3] + low[1]) // 2
            for f in flats:
                fw, fh = f[2] - f[0] + 1, f[3] - f[1] + 1
                if not (0.12 * w <= fw <= 0.5 * w and 0.12 * w <= fh <= 0.5 * w):
                    continue
                if abs((f[0] + f[2]) // 2 - (low[0] + low[2]) // 2) > 0.1 * w:
                    continue
                if abs((f[1] + f[3]) // 2 - split) > 0.2 * w:
                    continue
                state = _state(u[4], low[4], f[4])
                centre = (x0 + (f[0] + f[2]) // 2, (f[1] + f[3]) // 2)
                if state == "ON":
                    return "ON", centre
                if state == "OFF":
                    off_centre = centre
                break
    return ("OFF", off_centre) if off_centre else ("NONE", None)


def classify_screenshot(png_bytes):
    """Return "ON", "OFF" or "NONE" for the companion control in a screenshot (never a tap decision)."""
    return locate_control(png_bytes)[0]


def adb(serial, *args, text=True):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=text, check=False).stdout


def link_state(serial, mac_suffix):
    return parse_acl_state(adb(serial, "shell", "dumpsys", "bluetooth_manager"), mac_suffix)


def wait_for_link(serial, mac_suffix, want, timeout_s):
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        state = link_state(serial, mac_suffix)
        if state == want:
            return True
        time.sleep(2)
    return False


def open_log_stream(serial):
    """Clear the app's log and start streaming it. Lines queue up until collect_until reads them."""
    subprocess.run(["adb", "-s", serial, "logcat", "-c"], check=False)
    proc = subprocess.Popen(
        ["adb", "-s", serial, "logcat", "-v", "brief", "-s", f"{TAG}:D"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    q = queue.Queue()
    threading.Thread(target=lambda: [q.put(x) for x in proc.stdout], daemon=True).start()
    return proc, q


def say(msg):
    print(f"  [{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def collect_until(stream, done, timeout_s, echo=False):
    """Read tagged lines from an open stream until done(line) is true or the timeout passes."""
    _, q = stream
    lines = []
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        try:
            line = q.get(timeout=1)
        except queue.Empty:
            continue
        if TAG in line:
            lines.append(line.rstrip())
            if echo and re.search(r"Reconnect (read|tap|check)", line):
                say(line.split(": ", 1)[-1].strip())
            if done(line):
                break
    else:
        if echo:
            say(f"no check result after {timeout_s} s")
    return lines


def watch_app_log(serial, checks_wanted, timeout_s, pattern=CHECK_RE):
    """Stream the app's tagged log from now on until N matching lines arrive or the timeout passes."""
    stream = open_log_stream(serial)
    checks = 0

    def count_check(line):
        nonlocal checks
        if pattern.search(line):
            checks += 1
        return checks >= checks_wanted

    try:
        return collect_until(stream, count_check, timeout_s)
    finally:
        stream[0].terminate()


def append_row(row):
    OUT.mkdir(parents=True, exist_ok=True)
    with ROWS.open("a") as f:
        f.write(json.dumps(row) + "\n")


def toggle_adapter(serial):
    """Cycle the phone's Bluetooth adapter off and on. False when either wait times out."""
    def bt(*args):
        cmd = ["adb", "-s", serial, "shell", "cmd", "bluetooth_manager", *args]
        return subprocess.run(cmd, capture_output=True, text=True, timeout=60, check=False)

    try:
        bt("disable")
        if bt("wait-for-state:STATE_OFF").returncode != 0:
            return False
        bt("enable")
        return bt("wait-for-state:STATE_ON").returncode == 0
    except subprocess.TimeoutExpired:
        return False


def disconnect_companion(serial, mac_suffix):
    """Drop the companion link as a user would: tap the connect control while a fresh read says ON.

    Falls back to cycling the phone's Bluetooth adapter when the control does not read ON or the
    tap does not drop the link. Returns (link dropped, method).
    """
    state, centre = locate_control(take_screenshot(serial))
    if state == "ON":
        adb(serial, "shell", "input", "tap", str(centre[0]), str(centre[1]))
        if wait_for_link(serial, mac_suffix, "Disconnected", 60):
            return True, "tap"
    toggle_ok = toggle_adapter(serial)
    return toggle_ok and wait_for_link(serial, mac_suffix, "Disconnected", 60), "adapter"


GAME_PACKAGE = "com.nianticlabs.pokemongo"
SCREEN_OFF_S = 60  # matches the operator-driven block c in `run`


def screen_off_then_foreground(serial):
    """Block c, unattended: screen off for SCREEN_OFF_S, wake, dismiss a swipe lock, bring the game forward."""
    adb(serial, "shell", "input", "keyevent", "KEYCODE_SLEEP")
    time.sleep(SCREEN_OFF_S)
    adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(2)
    adb(serial, "shell", "input", "keyevent", "KEYCODE_MENU")  # dismisses a non-secure keyguard
    adb(serial, "shell", "monkey", "-p", GAME_PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(TOGGLE_SETTLE_S)


def take_screenshot(serial):
    return subprocess.run(["adb", "-s", serial, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout


def save_shot(png, device, block, cycle, phase, cls):
    SHOTS.mkdir(parents=True, exist_ok=True)
    path = SHOTS / f"{device}_{block}_{cycle:03d}_{phase}_{cls}.png"
    path.write_bytes(png)
    return str(path.relative_to(ROOT))


def cmd_auto(args):
    print(f"auto: {args.device_slug} block {args.block}, {args.cycles} cycles, companion ...{args.mac_suffix}", flush=True)
    for cycle in range(1, args.cycles + 1):
        print(f"[{args.block} {cycle}/{args.cycles}]", flush=True)
        acl_dropped, method = disconnect_companion(args.serial, args.mac_suffix)
        say(f"disconnect via {method}: companion link dropped={acl_dropped}")
        if args.block == "c":
            screen_off_then_foreground(args.serial)
        time.sleep(TOGGLE_SETTLE_S)
        before = take_screenshot(args.serial)
        before_class = classify_screenshot(before)
        before_shot = save_shot(before, args.device_slug, args.block, cycle, "before", before_class)
        toggle_ok = acl_dropped and before_class == "OFF"

        # Open the log only now, so lines from before the toggle cannot count as this cycle's result.
        stream = open_log_stream(args.serial)
        try:
            say(f"before screenshot reads {before_class}; waiting for the app's next check")
            started = time.monotonic()
            lines = collect_until(stream, lambda line: bool(TERMINAL_RE.search(line)), CYCLE_TIMEOUT_S + 60, echo=True)
            seconds = round(time.monotonic() - started, 1)
        finally:
            stream[0].terminate()
        info = classify_logs(lines)
        say("waiting for the companion link to come back")
        connected = wait_for_link(args.serial, args.mac_suffix, "Connected", 30)
        time.sleep(TOGGLE_SETTLE_S)
        after = take_screenshot(args.serial)
        after_class = classify_screenshot(after)
        after_shot = save_shot(after, args.device_slug, args.block, cycle, "after", after_class)
        row = {
            "device": args.device_slug, "block": args.block, "cycle": cycle,
            "outcome": info["outcome"], "taps": info["taps"], "reads": info["reads"],
            "seconds": seconds, "connected": connected, "acl_dropped": acl_dropped,
            "toggle_ok": toggle_ok, "disconnect": method, "before_class": before_class, "before_shot": before_shot,
            "after_class": after_class, "after_shot": after_shot,
            "pass": toggle_ok and cycle_passes(info["outcome"], connected) and after_class == "ON",
            "at": datetime.now(timezone.utc).isoformat(),
        }
        append_row(row)
        print(f"  -> outcome={row['outcome']} disconnect={method} before={before_class} "
              f"after={after_class} connected={connected} pass={row['pass']}")


def cmd_run(args):
    for cycle in range(1, args.cycles + 1):
        input(f"[{args.block} {cycle}/{args.cycles}] Disconnect the companion, confirm the control reads off, then press Enter: ")
        if not wait_for_link(args.serial, args.mac_suffix, "Disconnected", 60):
            print("  companion ACL never reached Disconnected; row not recorded", file=sys.stderr)
            continue
        if args.block == "c":
            input("  Turn the screen off, then press Enter (harness waits 60 s before waking): ")
            time.sleep(60)
            adb(args.serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
            input("  Bring the game back to the foreground, then press Enter: ")
        started = time.monotonic()
        lines = watch_app_log(args.serial, checks_wanted=1, timeout_s=CYCLE_TIMEOUT_S, pattern=TERMINAL_RE)
        seconds = round(time.monotonic() - started, 1)
        info = classify_logs(lines)
        connected = wait_for_link(args.serial, args.mac_suffix, "Connected", 30)
        row = {
            "device": args.device_slug, "block": args.block, "cycle": cycle,
            "outcome": info["outcome"], "taps": info["taps"], "reads": info["reads"],
            "seconds": seconds, "connected": connected,
            "pass": cycle_passes(info["outcome"], connected),
            "at": datetime.now(timezone.utc).isoformat(),
        }
        append_row(row)
        print(f"  -> outcome={row['outcome']} taps={row['taps']} connected={connected} pass={row['pass']}")


def cmd_already_on(args):
    state = link_state(args.serial, args.mac_suffix)
    if state != "Connected":
        sys.exit(f"companion is not Connected (ACL state: {state}); connect it first")
    lines = watch_app_log(args.serial, checks_wanted=args.checks, timeout_s=args.checks * LOOP_INTERVAL_S + 120)
    # Split the log at each check result so each check is judged on its own reads and taps.
    segment, checks = [], []
    for line in lines:
        segment.append(line)
        if CHECK_RE.search(line):
            checks.append(classify_logs(segment))
            segment = []
    for i, info in enumerate(checks, 1):
        ok = info["outcome"] == "NOT_OFF" and info["taps"] == 0 and bool(info["reads"]) and all(r == "ON" for r in info["reads"])
        append_row({"device": args.device_slug, "block": "already-on", "cycle": i,
                    "outcome": info["outcome"], "taps": info["taps"], "reads": info["reads"],
                    "pass": ok, "at": datetime.now(timezone.utc).isoformat()})
    print(f"{sum(1 for i in checks if i['taps'] == 0)}/{len(checks)} checks with zero taps; wanted {args.checks}")


def cmd_corpus(args):
    corpus = OUT / "corpus"
    corpus.mkdir(parents=True, exist_ok=True)
    name = f"{args.state}_{args.theme}_{args.brightness}_{args.device_slug}.png"
    with (corpus / name).open("wb") as f:
        f.write(subprocess.run(["adb", "-s", args.serial, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout)
    print(f"wrote {corpus / name}")


def cmd_summary(_args):
    if not ROWS.exists():
        sys.exit("no results yet")
    counts, failed = {}, []
    for line in ROWS.read_text().splitlines():
        row = json.loads(line)
        if not row["pass"]:
            failed.append(f"{row['device']} {row['block']} cycle {row['cycle']}: outcome={row.get('outcome')}")
        key = (row["device"], row["block"])
        passed, total = counts.get(key, (0, 0))
        counts[key] = (passed + bool(row["pass"]), total + 1)
    print("| device | block | pass / total |")
    print("|---|---|---|")
    for (device, block), (passed, total) in sorted(counts.items()):
        print(f"| {device} | {block} | {passed} / {total} |")
    if failed:
        print("\nFailed cycles:")
        print("\n".join(f"- {f}" for f in failed))


def list_devices():
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    return [l.split()[0] for l in out.splitlines()[1:] if l.strip().endswith("\tdevice")]


def device_model(serial):
    return adb(serial, "shell", "getprop", "ro.product.model").strip().lower().replace(" ", "") or serial


# matches both 'Pokemon GO Plus' and 'Pokemon GO Plus +' after normalize_name
COMPANION_NAME = "pokemongoplus"
COMPANION_CACHE = OUT / "companion.txt"
BONDED_RE = re.compile(r"XX:XX:XX:XX:(\w\w:\w\w)\(.*\[ACL BR/EDR:(\w) LE:(\w)\].*\]\s*(.*)$")


def normalize_name(name):
    folded = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    return "".join(folded.lower().split())


def pick_companion(serial):
    """--mac-suffix omitted: the one bonded device with a live link, else ask by name."""
    dump = adb(serial, "shell", "dumpsys", "bluetooth_manager")
    bonded = [(m.group(1), "Y" in (m.group(2), m.group(3)), m.group(4).strip()) for l in dump.splitlines() if (m := BONDED_RE.search(l)) and m.group(4).strip()]
    named = [b for b in bonded if normalize_name(b[2]).startswith(COMPANION_NAME)]
    if not named and COMPANION_CACHE.exists():
        # the phone only lists devices it has seen since its Bluetooth stack started
        mac = COMPANION_CACHE.read_text().strip()
        print(f"companion not listed by the phone; using last seen ...{mac}")
        return mac
    bonded = named or bonded
    if len(named) == 1:
        COMPANION_CACHE.parent.mkdir(parents=True, exist_ok=True)
        COMPANION_CACHE.write_text(named[0][0])
    live = [b for b in bonded if b[1]]
    if len(named) == 1:
        print(f"companion: {named[0][2]} ({named[0][0]})")
        return named[0][0]
    if len(live) == 1:
        print(f"companion: {live[0][2]} ({live[0][0]})")
        return live[0][0]
    if not bonded:
        sys.exit("phone lists no Bluetooth device: press the Pokemon GO Plus button so it advertises, then rerun (or pass --mac-suffix)")
    choices = live or bonded
    for i, (mac, _, name) in enumerate(choices, 1):
        print(f"{i}. {name}  ...{mac}")
    while True:
        c = input("Pick companion number: ").strip()
        if c.isdigit() and 1 <= int(c) <= len(choices):
            return choices[int(c) - 1][0]


def pick_device():
    """--serial omitted: use the only device, else ask."""
    devices = list_devices()
    if not devices:
        sys.exit("no adb device in state 'device'")
    if len(devices) == 1:
        return devices[0]
    for i, d in enumerate(devices, 1):
        print(f"{i}. {d}  {device_model(d)}")
    while True:
        choice = input("Pick device number: ").strip()
        if choice.isdigit() and 1 <= int(choice) <= len(devices):
            return devices[int(choice) - 1]


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    r = sub.add_parser("run")
    r.add_argument("--serial")
    r.add_argument("--mac-suffix")
    r.add_argument("--device-slug")
    r.add_argument("--block", default="a", choices=list("abcde"))
    r.add_argument("--cycles", type=int, default=20)
    r.set_defaults(fn=cmd_run)

    au = sub.add_parser("auto")
    au.add_argument("--serial")
    au.add_argument("--mac-suffix")
    au.add_argument("--device-slug")
    au.add_argument("--block", default="a", choices=list("abcde"))
    au.add_argument("--cycles", type=int, default=100)
    au.set_defaults(fn=cmd_auto)

    a = sub.add_parser("already-on")
    a.add_argument("--serial")
    a.add_argument("--mac-suffix")
    a.add_argument("--device-slug")
    a.add_argument("--checks", type=int, default=12)
    a.set_defaults(fn=cmd_already_on)

    c = sub.add_parser("corpus")
    c.add_argument("--serial")
    c.add_argument("--device-slug")
    c.add_argument("--state", required=True, choices=["on", "off"])
    c.add_argument("--theme", required=True, choices=["day", "night"])
    c.add_argument("--brightness", required=True, choices=["min", "max"])
    c.set_defaults(fn=cmd_corpus)

    s = sub.add_parser("summary")
    s.set_defaults(fn=cmd_summary)

    args = p.parse_args()
    if hasattr(args, "serial"):
        args.serial = args.serial or pick_device()
        if not getattr(args, "device_slug", True):
            args.device_slug = device_model(args.serial)
        if not getattr(args, "mac_suffix", True):
            args.mac_suffix = pick_companion(args.serial)
    args.fn(args)


if __name__ == "__main__":
    main()
