#!/usr/bin/env python3
"""Global tap-to-walk smoke gate (docs/testing.md, "Tap-to-walk global smoke gate").

For each location in scripts/tap_smoke_locations.json: teleport through the Control API, wait for the game
to settle, walk the app's eight calibration legs, fit the perspective ground map, then score 12 on-device taps with
registration_bench.py's masked-NCC `taptrials`. Writes report.md and report.json (numbers only, no frames)
under scripts/registration-bench/smoke/<UTC stamp>/ (gitignored). Exit 0 iff 19 of 20 locations pass.

Run with the bench venv python (make tap-smoke). Needs LJ_CONTROL_API_KEY and LJ_CONTROL_API_HOST.
Only on a throwaway game account: global teleports are anti-cheat bait.
Self-check: python3 scripts/test_tap_smoke.py
"""

import argparse
import json
import math
import os
import subprocess
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).parent
BENCH = HERE / "registration_bench.py"
N_TAPS, NEED_TAPS, TOL_M, MAX_M = 12, 11, 15.0, 40.0
NEED_LOCATIONS = 19
# Per-step wall-clock bound: a hang fails the location, not the run. taptrials walks to each tap, so it is the long one.
STEP_TIMEOUT_S = {"walkcap": 1800, "persp": 600, "taptrials": 3600}
# Same legs as the app (CalibrationWalkMath.BEARINGS_DEG, LEG_METERS): fewer legs hide the sprite-erasure bug.
LEG_BEARINGS = ["0", "180", "90", "270", "45", "225", "135", "315"]


# ---- pure logic (pinned by test_tap_smoke.py) ----

def load_locations(path):
    return json.loads(Path(path).read_text())


def percentile(errs, q):
    """Same index rule as registration_bench.cmd_taptrials; NaN when empty."""
    errs = sorted(errs)
    return errs[min(len(errs) - 1, int(q * len(errs)))] if errs else float("nan")


def location_verdict(trials, calibration_ok, need=NEED_TAPS, n=N_TAPS):
    """(PASS|FAIL, reason). Ambiguous taps are skipped by the bench and never counted."""
    if not calibration_ok:
        return "FAIL", "no_calibration"
    scored = [t for t in trials if t["verdict"] in ("pass", "fail", "unverified")]
    passes = sum(t["verdict"] == "pass" for t in scored)
    if len(scored) - passes > n - need:
        return "FAIL", "below_bar"
    if len(scored) < n:
        return "FAIL", "insufficient_scored"
    return "PASS", ""


def trial_counts(trials):
    """(unverified, ambiguous) trial counts for the report."""
    return (sum(t["verdict"] == "unverified" for t in trials), sum(t["verdict"] == "ambiguous" for t in trials))


def prior_fallbacks(persp_log):
    """Feet-anchor prior fallbacks the bench's persp step printed (a frequent one means failed registration)."""
    return sum("prior fallback" in line for line in persp_log.splitlines())


def suite_verdict(rows, need=NEED_LOCATIONS):
    """(passing, total, ok)."""
    p = sum(r["verdict"] == "PASS" for r in rows)
    return p, len(rows), p >= need


def summary_line(p, n, ok):
    return f"TAP SMOKE: {p}/{n} locations pass (bar {NEED_LOCATIONS}/20) -> {'PASS' if ok else 'FAIL'}"


def render_report(rows):
    head = ("| # | name | lat | lon | kind | device | resolution | density | scored | pass | unverified | ambiguous "
            "| prior fallback | p50 (m) | p90 (m) | verdict | reason |\n|" + "---|" * 17)
    body = [f"| {r['index']} | {r['name']} | {r['lat']} | {r['lon']} | {r['kind']} | {r['device']} "
            f"| {r['resolution']} | {r['density']} | {r['scored']} | {r['passes']} | {r['unverified']} "
            f"| {r['ambiguous']} | {r['prior_fallback']} | {r['p50']:.1f} "
            f"| {r['p90']:.1f} | {r['verdict']} | {r['reason']} |" for r in rows]
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
    size = _adb(serial, "wm", "size").split(":")[-1].strip()
    dens = _adb(serial, "wm", "density").split(":")[-1].strip()
    return model, size, dens


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


def run_location(py, serial, host, key, loc, i, out, settle, respect_cooldown):
    d = out / f"{i:02d}_{''.join(c if c.isalnum() else '_' for c in loc['name'].lower())}"
    row = {"index": i, **{k: loc[k] for k in ("name", "lat", "lon", "kind")}, "scored": 0, "passes": 0,
           "unverified": 0, "ambiguous": 0, "prior_fallback": 0, "p50": float("nan"), "p90": float("nan")}
    cooldown = 0
    try:
        cooldown = teleport_and_settle(host, key, loc, settle, respect_cooldown)
        for step in (
            ["walkcap", str(d), "--serial", serial, "--legs", *LEG_BEARINGS, "--dist", "40", "--every", "3"],
            ["persp", str(d)],
            ["taptrials", str(d), "--serial", serial, "--persp", "--n", str(N_TAPS), "--need", str(NEED_TAPS),
             "--tol-m", str(TOL_M), "--max-m", str(MAX_M)],
        ):
            d.mkdir(parents=True, exist_ok=True)
            ok, log = _bench(py, serial, *step)
            (d / f"{step[0]}.log").write_text(log)  # kept whole: the report counts fallbacks from it
            if not ok:
                raise RuntimeError(f"{step[0]} failed: {log[-400:]}")
    except (RuntimeError, OSError, KeyError, subprocess.SubprocessError) as e:
        print(f"[{i}] {loc['name']}: {e}", file=sys.stderr)
        row.update(verdict="FAIL", reason="bench_error")
        return row, cooldown
    cal, tj = d / "calibration_persp.json", d / "trials.json"
    cal_ok = cal.exists() and not json.loads(cal.read_text()).get("rejected")
    trials = json.loads(tj.read_text()) if tj.exists() else []
    errs = [t["error_m"] for t in trials if t["verdict"] in ("pass", "fail")]
    unverified, ambiguous = trial_counts(trials)
    row.update(scored=sum(t["verdict"] in ("pass", "fail", "unverified") for t in trials),
               passes=sum(t["verdict"] == "pass" for t in trials), unverified=unverified, ambiguous=ambiguous,
               prior_fallback=prior_fallbacks((d / "persp.log").read_text()), p50=percentile(errs, 0.5),
               p90=percentile(errs, 0.9))
    row["verdict"], row["reason"] = location_verdict(trials, cal_ok)
    return row, cooldown


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--locations", default=str(HERE / "tap_smoke_locations.json"))
    p.add_argument("--out", default=None)
    p.add_argument("--serial", default=None)
    p.add_argument("--only", type=int, nargs="+", help="1-based location numbers to run")
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
    model, size, dens = device_info(serial)
    locs = load_locations(a.locations)
    out = Path(a.out) if a.out else HERE / "registration-bench" / "smoke" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out.mkdir(parents=True, exist_ok=True)
    rows, wait = [], 0
    for i, loc in enumerate(locs, 1):
        if a.only and i not in a.only:
            continue
        time.sleep(wait)
        row, wait = run_location(sys.executable, serial, host, key, loc, i, out, a.settle, a.respect_cooldown)
        row.update(device=model, resolution=size, density=dens)
        rows.append(row)
        print(f"[{i}] {row['name']}: {row['verdict']} {row['passes']}/{row['scored']} {row['reason']}", flush=True)
    (out / "report.json").write_text(json.dumps(rows, indent=1))
    (out / "report.md").write_text(render_report(rows))
    print((out / "report.md").read_text())
    return 0 if suite_verdict(rows)[2] else 1


if __name__ == "__main__":
    sys.exit(main())
