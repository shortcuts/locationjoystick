"""Export golden vectors for CalibrationWalkMathTest from the bench leg geometry.

Rerun whenever registration_bench.commanded_target, along_track_move or the moving rule of
_commanded_legs changes. The moving rule is inlined there (reads files), so it is repeated below.
Run: scripts/registration-bench/.venv/bin/python scripts/export_calibration_walk_golden.py
Writes core/model/src/test/resources/calibration_walk_golden/legs.txt:
  leg <bearing> <startLat> <startLon> <n> <targetLat> <targetLon>
  <t> <lat> <lon> <moving 0|1> <offEast> <offNorth>      (n lines; offsets via along_track_move from frame 0)
"""
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import registration_bench as rb  # noqa: E402

MOVING_MIN = 0.3
out = Path(__file__).parent.parent / "core/model/src/test/resources/calibration_walk_golden"
out.mkdir(parents=True, exist_ok=True)
rng = random.Random(20260514)
lines = []
for lat0, lon0 in [(45.5017, -73.5673), (-33.8688, 151.2093), (60.1699, 24.9384)]:
    for b in (0, 180, 90, 270, 45, 225, 135, 315):
        n = rng.randint(14, 36)
        stop_head, stop_tail = 2, rng.randint(0, 3)
        tl = rb.commanded_target(lat0, lon0, b, 40.0)
        ll, ts = [], []
        walked = 0.0
        t = 0.0
        for i in range(n):
            moving = stop_head <= i < n - stop_tail
            if moving:
                walked = min(40.0, walked + 3.0 * 0.5556 * rng.uniform(0.9, 1.1))
            frac = walked / 40.0
            jit = rng.uniform(-2e-7, 2e-7)
            ll.append((lat0 + (tl[0] - lat0) * frac + jit, lon0 + (tl[1] - lon0) * frac + jit))
            ts.append(t)
            t += 3.0 + rng.uniform(-0.05, 0.05)
        lines.append(f"leg {b} {lat0!r} {lon0!r} {n} {tl[0]!r} {tl[1]!r}")
        for i in range(n):
            a, c = max(i - 1, 0), min(i + 1, n - 1)
            dn = (ll[c][0] - ll[a][0]) * 111320
            de = (ll[c][1] - ll[a][1]) * 111320 * math.cos(math.radians(ll[0][0]))
            speed = math.hypot(dn, de) / max(ts[c] - ts[a], 1e-6)
            mv = 1 if speed >= MOVING_MIN else 0
            e, nn = rb.along_track_move(ll[0], ll[i], b, ll[0][0])
            lines.append(f"{ts[i]!r} {ll[i][0]!r} {ll[i][1]!r} {mv} {e!r} {nn!r}")
(out / "legs.txt").write_text("\n".join(lines) + "\n")
print(len(lines), "lines")
