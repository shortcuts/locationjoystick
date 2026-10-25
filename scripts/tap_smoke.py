#!/usr/bin/env python3
"""Global tap-to-walk smoke gate (docs/testing.md, "Tap-to-walk global smoke gate").

For each location in scripts/tap_smoke_locations.json: teleport through the Control API, measure the reference
ruler with registration_bench.py's walkcap and persp steps, walk one 40 m leg so the app stores its own map, then
tap ground points through the tap-to-walk overlay and score where the app walked. Writes report.md and report.json
(numbers only, no frames) under scripts/registration-bench/smoke/<UTC stamp>/ (gitignored). Exit 0 iff the gate passes.

Run with the bench venv python (make tap-smoke). Needs LJ_CONTROL_API_KEY and LJ_CONTROL_API_HOST.
Only on a throwaway game account: global teleports are anti-cheat bait.
Self-check: python3 scripts/test_tap_smoke.py
"""

import argparse
import json
import math
import os
import random
import re
import statistics
import subprocess
import sys
import tempfile
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).parent
BENCH = HERE / "registration_bench.py"
INF = float("inf")
N_TAPS, TOL_M, MAX_REJECT, NCC_MIN, MAX_M = 12, 15.0, 0.2, 0.4, 40.0
NEED_LOCATIONS = 19
TAP_WAIT_S, WALK_TIMEOUT_S = 3.0, 300.0
ZOOM, TWIST_DEG, LEG_M = 1.6, 45.0, 40.0
EXTRA_LOCATIONS = (1, 7)  # 1-based: New York (dense) and Reykjavik (high latitude)
EXTRA_SCENARIOS = ("fresh", "twist", "zoom")
# Per-step wall-clock bound: a hang fails the row, not the run.
STEP_TIMEOUT_S = {"walkcap": 1800, "persp": 600}
# Eight legs on purpose: fewer legs hide the sprite-erasure bug. The app's learning walk uses two (CalibrationWalkMath.LEARNING_BEARINGS_DEG).
LEG_BEARINGS = ["0", "180", "90", "270", "45", "225", "135", "315"]
# UI strings the device path finds by content description (feature/widget/impl/src/main/res/values/strings.xml).
TAP_ICON_DESC, EXPAND_DESC, CANCEL_DESC = "Tap to walk", "Expand widget", "Cancel tap-to-walk"
# DataStore key of the stored flow map (core/datastore AppPreferencesDataSource).
TAP_CALIBRATION_KEY = "tap_to_walk_ground_calibration"


# ---- pure logic (pinned by test_tap_smoke.py) ----

def load_locations(path):
    return json.loads(Path(path).read_text())


def percentile(errs, q):
    """Same index rule as registration_bench.cmd_taptrials; NaN when empty."""
    errs = sorted(errs)
    return errs[min(len(errs) - 1, int(q * len(errs)))] if errs else float("nan")


def tap_error_m(ncc, err_m):
    """A tap the matcher cannot verify is a miss, so a weak matcher cannot raise the pass rate."""
    return INF if ncc < NCC_MIN else err_m


def row_verdict(errs, rejected, ambiguous):
    """(PASS|FAIL, reason). errs holds one error per scored tap (inf for a miss)."""
    if len(errs) < N_TAPS:
        return "FAIL", "insufficient_scored"
    if percentile(errs, 0.9) > TOL_M:
        return "FAIL", "p90_over_15m"
    if rejected * 5 > (rejected + len(errs) + ambiguous):  # integer form of rejected / total > 20%
        return "FAIL", "rejected_over_20pct"
    return "PASS", ""


def suite_verdict(rows, need=NEED_LOCATIONS):
    """(passing, total, ok). The 20 normal rows need the bar; every extra scenario row must pass."""
    normal = [r for r in rows if r.get("scenario", "normal") == "normal"]
    extra = [r for r in rows if r.get("scenario", "normal") != "normal"]
    p = sum(r["verdict"] == "PASS" for r in normal)
    return p, len(normal), p >= need and all(r["verdict"] == "PASS" for r in extra)


def prior_fallbacks(persp_log):
    """Feet-anchor prior fallbacks the bench's persp step printed (a frequent one means failed registration)."""
    return sum("prior fallback" in line for line in persp_log.splitlines())


def summary_line(p, n, ok):
    return f"TAP SMOKE: {p}/{n} locations pass (bar {NEED_LOCATIONS}/20) -> {'PASS' if ok else 'FAIL'}"


def render_report(rows):
    head = ("| # | name | lat | lon | kind | device | resolution | density | scenario | scored | rejected | ambiguous "
            "| unverified | prior fallback | p50 (m) | p90 (m) | re-aim (m) | verdict | reason |\n|" + "---|" * 19)
    body = [f"| {r['index']} | {r['name']} | {r['lat']} | {r['lon']} | {r['kind']} | {r['device']} "
            f"| {r['resolution']} | {r['density']} | {r['scenario']} | {r['scored']} | {r['rejected']} "
            f"| {r['ambiguous']} | {r['unverified']} | {r['prior_fallback']} | {r['p50']:.1f} "
            f"| {r['p90']:.1f} | {r['reaim_m']:.1f} | {r['verdict']} | {r['reason']} |" for r in rows]
    return "\n".join([head, *body, "", summary_line(*suite_verdict(rows))]) + "\n"


# ---- device glue ----

def _api(host, key, method, path, body=None):
    # ponytail: duplicated from registration_bench._api to keep cv2/numpy out of this module; share when a third script needs it.
    req = urllib.request.Request(
        f"http://{host}/api/v1{path}", method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read() or b"{}")


def _adb(serial, *args):
    return subprocess.run(["adb", "-s", serial, "shell", *args], check=True, capture_output=True, text=True).stdout


def device_info(serial):
    model = _adb(serial, "getprop", "ro.product.model").strip()
    sdk = int(_adb(serial, "getprop", "ro.build.version.sdk").strip())
    size = _adb(serial, "wm", "size").split(":")[-1].strip()
    dens = _adb(serial, "wm", "density").split(":")[-1].strip()
    return model, sdk, size, dens


def _metres(a, b):
    dn = (a[0] - b[0]) * 111320
    de = (a[1] - b[1]) * 111320 * math.cos(math.radians(b[0]))
    return math.hypot(dn, de)


def teleport_and_settle(host, key, loc, settle, respect_cooldown):
    _api(host, key, "POST", "/walk/stop")  # a stale walk must not poison this location
    r = _api(host, key, "POST", "/teleport", {"lat": loc["lat"], "lon": loc["lon"]})
    if not r.get("ok"):
        raise RuntimeError(f"teleport refused ({r}); is Hide Teleport on?")
    wait = r.get("warning", {}).get("totalSeconds", 0)
    deadline = time.time() + 60
    while time.time() < deadline:
        st = _api(host, key, "GET", "/state")
        p = st.get("position")
        if (st["spoofState"] == "RUNNING" and not st["walk"]["target"] and p
                and _metres((p["lat"], p["lon"]), (loc["lat"], loc["lon"])) < 1.0):
            break
        time.sleep(1)
    else:
        raise RuntimeError("position did not reach the target within 60 s")
    time.sleep(settle)
    return wait if respect_cooldown else 0


def _bench(py, serial, *args):
    cmd = [py, str(BENCH), *args]
    budget = STEP_TIMEOUT_S.get(args[0], 900)
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=budget)
    except subprocess.TimeoutExpired:
        return False, f"timed out after {budget:.0f} s"
    return r.returncode == 0, r.stdout + r.stderr


def _tap(serial, xy):
    subprocess.run(["adb", "-s", serial, "shell", "input", "tap", str(int(xy[0])), str(int(xy[1]))], check=True)


def _node_centre(serial, desc, attr="content-desc"):
    """Centre of the first UI node whose attr equals desc, or None. --windows sees the overlay widget."""
    _adb(serial, "uiautomator", "dump", "--windows", "/sdcard/ljui.xml")
    xml = _adb(serial, "cat", "/sdcard/ljui.xml")
    for node in re.findall(r"<node [^>]*>", xml):
        attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', node))
        if attrs.get(attr) == desc:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", attrs["bounds"]))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def open_overlay(serial):
    """Opens the tap-to-walk overlay from the widget. The overlay closes itself after every tap."""
    icon = _node_centre(serial, TAP_ICON_DESC)
    if icon is None:
        expand = _node_centre(serial, EXPAND_DESC)
        if expand is None:
            raise RuntimeError("widget not on screen")
        _tap(serial, expand)
        time.sleep(1)
        icon = _node_centre(serial, TAP_ICON_DESC)
        if icon is None:
            raise RuntimeError("Tap to walk icon not on screen")
    _tap(serial, icon)
    time.sleep(1)
    if _node_centre(serial, CANCEL_DESC) is None:
        raise RuntimeError("tap-to-walk overlay did not open")


def _varint(b, i):
    value = shift = 0
    while True:
        c = b[i]
        i += 1
        value |= (c & 0x7F) << shift
        shift += 7
        if not c & 0x80:
            return value, i


def _map_entry_key(entry):
    """The key string of a MapEntry, the only message in the file whose fields are all length-delimited."""
    i = 0
    while i < len(entry):
        tag, i = _varint(entry, i)
        n, i = _varint(entry, i)
        if tag >> 3 == 1:
            return entry[i:i + n].decode()
        i += n
    return None


def drop_pref(data, key):
    """Removes one map entry from a DataStore PreferencesMap (field 1 = repeated MapEntry)."""
    out, i, removed = bytearray(), 0, False
    while i < len(data):
        start = i
        tag, i = _varint(data, i)
        if tag & 7 == 0:
            _, i = _varint(data, i)
            out += data[start:i]
        elif tag & 7 == 2:
            n, i = _varint(data, i)
            entry = data[i:i + n]
            i += n
            if tag >> 3 == 1 and _map_entry_key(entry) == key:
                removed = True
            else:
                out += data[start:i]
        else:
            raise ValueError(f"unexpected wire type {tag & 7} in DataStore file")
    if not removed:
        raise RuntimeError(f"no {key!r} entry in the DataStore file")
    return bytes(out)


def forget_stored_map(serial, host, key, loc, settle):
    """Forces the app to forget its stored map, then starts spoofing again at the same place."""
    pkg, path = "com.locationjoystick.app", "files/datastore/app_preferences.preferences_pb"
    _adb(serial, "am", "force-stop", pkg)
    raw = subprocess.run(["adb", "-s", serial, "exec-out", "run-as", pkg, "cat", path], check=True,
                         capture_output=True).stdout
    local = Path(tempfile.mkdtemp()) / "prefs.pb"
    local.write_bytes(drop_pref(raw, TAP_CALIBRATION_KEY))
    subprocess.run(["adb", "-s", serial, "push", str(local), "/data/local/tmp/ljprefs.pb"], check=True,
                   capture_output=True)
    _adb(serial, "run-as", pkg, "cp", "/data/local/tmp/ljprefs.pb", path)
    _adb(serial, "am", "start", "-n", f"{pkg}/.MainActivity")
    time.sleep(3)
    if _api(host, key, "GET", "/state")["spoofState"] != "RUNNING":
        start = _node_centre(serial, "Start", attr="text")
        if start is None:
            raise RuntimeError("Start button not on screen")
        _tap(serial, start)
    teleport_and_settle(host, key, loc, settle, respect_cooldown=False)


def _frame(serial, path):
    import cv2
    import registration_bench as rb

    rb._adb_screencap(serial, path)
    return cv2.imread(str(path))


def _target(host, key):
    return _api(host, key, "GET", "/state")["walk"]["target"]


def score_tap(host, key, serial, cal, p, frames, n):
    """One overlay tap at pixel p: {"kind": scored|rejected|ambiguous, ...}. Errors are in metres."""
    import numpy as np
    import registration_bench as rb

    anchor = (cal["px"], cal["py"])
    before = _frame(serial, frames / f"t{n:02d}_before.png")
    open_overlay(serial)
    _tap(serial, p)
    first, deadline = None, time.time() + TAP_WAIT_S
    while first is None and time.time() < deadline:
        first = _target(host, key)
        time.sleep(0.5)
    if first is None:
        return {"kind": "rejected"}
    last, deadline = first, time.time() + WALK_TIMEOUT_S
    while True:
        target = _target(host, key)
        if target is None:
            break
        last = target
        if time.time() > deadline:
            _api(host, key, "POST", "/walk/stop")
            return {"kind": "ambiguous", "reason": "walk_timeout"}
        time.sleep(1)
    after = _frame(serial, frames / f"t{n:02d}_after.png")
    off, ncc = rb.patch_offset(before, after, p, anchor)
    if off is None:
        return {"kind": "ambiguous", "reason": "no_patch"}
    hi = np.linalg.inv(rb.persp_h(cal))
    err = float(np.linalg.norm(rb._h_apply(hi, (anchor[0] + off[0], anchor[1] + off[1])) - rb._h_apply(hi, anchor)))
    reaim = _metres((first["lat"], first["lon"]), (last["lat"], last["lon"]))
    return {"kind": "scored", "error_m": tap_error_m(ncc, err), "ncc": float(ncc), "reaim_m": reaim}


def candidate_taps(serial, cal):
    """Ground-band pixels whose predicted walk is 5 to 40 m, in a fixed shuffled order."""
    import numpy as np
    import registration_bench as rb

    w, h = rb._device_size(serial)
    # Dense on purpose: a 50 px grid found no 5 to 40 m point for the 2026-10-25 New York ruler (about 3 px per m).
    pts = [(rb._sx(x, w), rb._sy(y, h)) for x in np.arange(150, 931, 20) for y in np.arange(1000, 1901, 10)]
    random.Random(0).shuffle(pts)
    return [p for p in pts if rb.TRIAL_MIN_M <= math.hypot(*rb.tap_to_ground_persp(cal, p)) <= MAX_M]


def measure_taps(host, key, serial, cal, d):
    frames = d / "frames"
    frames.mkdir(parents=True, exist_ok=True)
    trials, scored = [], 0
    for n, p in enumerate(candidate_taps(serial, cal)):
        if scored == N_TAPS:
            break
        t = {**score_tap(host, key, serial, cal, p, frames, n), "tap": [float(p[0]), float(p[1])]}
        trials.append(t)
        scored += t["kind"] == "scored"
        (d / "trials.json").write_text(json.dumps(trials, indent=1))  # an aborted run keeps its numbers
        print(f"  tap {n:02d} {t['kind']} {t.get('error_m', '')}", flush=True)
    return trials


def reference(py, serial, d):
    """Walks the eight calibration legs and fits the ruler. The app's flow fit also reads these legs."""
    d.mkdir(parents=True, exist_ok=True)
    walk = ["walkcap", str(d), "--serial", serial, "--legs", *LEG_BEARINGS, "--dist", "40", "--every", "3"]
    for step in (walk, ["persp", str(d)]):
        ok, log = _bench(py, serial, *step)
        (d / f"{step[0]}.log").write_text(log)  # kept whole: the report counts fallbacks from it
        if not ok:
            raise RuntimeError(f"{step[0]} failed: {log[-400:]}")
    return json.loads((d / "calibration_persp.json").read_text())


def app_leg(host, key, loc):
    """One 40 m leg north. The last walk before the taps, so the stored map is this one-leg map."""
    import registration_bench as rb

    arrived = rb._walk_and_wait(host, key, loc["lat"] + LEG_M / 111320, loc["lon"])
    if not arrived:
        raise RuntimeError("app leg did not arrive")


def run_row(py, serial, host, key, loc, i, scenario, out, settle, respect_cooldown):
    import registration_bench as rb

    slug = "".join(c if c.isalnum() else "_" for c in loc["name"].lower())
    d = out / f"{i:02d}_{slug}" / scenario
    row = {"index": i, **{k: loc[k] for k in ("name", "lat", "lon", "kind")}, "scenario": scenario, "scored": 0,
           "rejected": 0, "ambiguous": 0, "unverified": 0, "prior_fallback": 0, "p50": INF, "p90": INF,
           "reaim_m": float("nan")}
    cooldown, undo = 0, []
    cx, cy = rb.GESTURE_CENTER
    try:
        cooldown = teleport_and_settle(host, key, loc, settle, respect_cooldown)
        cal = reference(py, serial, d / "reference")
        if scenario == "zoom":
            rb._twist(serial, rb.pinch_events(cx, cy, rb.PINCH_BASE_PX, rb.PINCH_BASE_PX * ZOOM))
            undo.append(rb.pinch_events(cx, cy, rb.PINCH_BASE_PX * ZOOM, rb.PINCH_BASE_PX))
            cal = reference(py, serial, d / "reference_zoomed")
        if scenario == "fresh":
            forget_stored_map(serial, host, key, loc, settle)
        else:
            app_leg(host, key, loc)
        if scenario == "twist":
            rb._twist(serial, rb.twist_events(cx, cy, 250, 0.0, TWIST_DEG))
            undo.append(rb.twist_events(cx, cy, 250, TWIST_DEG, -TWIST_DEG))
        if cal.get("rejected"):
            row.update(verdict="FAIL", reason="no_calibration")
            return row, cooldown
        trials = measure_taps(host, key, serial, cal, d)
    except (RuntimeError, OSError, KeyError, ValueError, subprocess.SubprocessError) as e:
        print(f"[{i}] {loc['name']} ({scenario}): {e}", file=sys.stderr)
        row.update(verdict="FAIL", reason="bench_error")
        return row, cooldown
    finally:
        for events in reversed(undo):
            rb._twist(serial, events)
        _api(host, key, "POST", "/walk/stop")
    errs = [t["error_m"] for t in trials if t["kind"] == "scored"]
    reaims = [t["reaim_m"] for t in trials if t["kind"] == "scored"]
    row.update(scored=len(errs), rejected=sum(t["kind"] == "rejected" for t in trials),
               ambiguous=sum(t["kind"] == "ambiguous" for t in trials),
               unverified=sum(t["error_m"] == INF for t in trials if t["kind"] == "scored"),
               prior_fallback=prior_fallbacks((d / "reference" / "persp.log").read_text()),
               p50=percentile(errs, 0.5), p90=percentile(errs, 0.9),
               reaim_m=statistics.median(reaims) if reaims else float("nan"))
    row["verdict"], row["reason"] = row_verdict(errs, row["rejected"], row["ambiguous"])
    return row, cooldown


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--locations", default=str(HERE / "tap_smoke_locations.json"))
    p.add_argument("--out", default=None)
    p.add_argument("--serial", default=None)
    p.add_argument("--only", type=int, nargs="+", help="1-based location numbers to run")
    p.add_argument("--scenarios", choices=("normal", "all"), default="normal",
                   help="all adds fresh, twist and zoom at locations 1 and 7")
    p.add_argument("--settle", type=float, default=45.0, help="seconds to let the game load tiles")
    p.add_argument("--respect-cooldown", action="store_true", help="sleep the app's advised cooldown between teleports")
    p.add_argument("--ack-throwaway-account", action="store_true", help="required: global teleports risk a ban")
    a = p.parse_args(argv)
    if not a.ack_throwaway_account:
        raise SystemExit("refusing: pass --ack-throwaway-account (only run this on a throwaway game account)")
    key, host = os.environ.get("LJ_CONTROL_API_KEY"), os.environ.get("LJ_CONTROL_API_HOST")
    if not key or not host:
        raise SystemExit("set LJ_CONTROL_API_KEY and LJ_CONTROL_API_HOST (host:port)")
    serial = a.serial
    if not serial:
        found = [l.split()[0] for l in subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True)
                 .stdout.splitlines()[1:] if l.endswith("\tdevice")]
        if len(found) != 1:
            raise SystemExit(f"pass --serial: {len(found)} devices attached")
        serial = found[0]
    model, sdk, size, dens = device_info(serial)
    if sdk < 30:
        raise SystemExit(f"device_too_old: overlay needs Android 11 (API 30), device is API {sdk}")
    locs = load_locations(a.locations)
    out = Path(a.out) if a.out else HERE / "registration-bench" / "smoke" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out.mkdir(parents=True, exist_ok=True)
    jobs = [(i, "normal") for i in range(1, len(locs) + 1)]
    if a.scenarios == "all":
        jobs += [(i, s) for i in EXTRA_LOCATIONS for s in EXTRA_SCENARIOS]
    rows, wait = [], 0
    for i, scenario in jobs:
        if a.only and i not in a.only:
            continue
        time.sleep(wait)
        row, wait = run_row(sys.executable, serial, host, key, locs[i - 1], i, scenario, out, a.settle,
                            a.respect_cooldown)
        row.update(device=model, resolution=size, density=dens)
        rows.append(row)
        print(f"[{i}] {row['name']} ({scenario}): {row['verdict']} {row['scored']} scored {row['reason']}", flush=True)
    (out / "report.json").write_text(json.dumps(rows, indent=1))
    (out / "report.md").write_text(render_report(rows))
    print((out / "report.md").read_text())
    return 0 if suite_verdict(rows)[2] else 1


if __name__ == "__main__":
    sys.exit(main())
