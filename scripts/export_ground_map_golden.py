"""Print Kotlin golden vectors for GroundMapTest from the bench ground map.

Rerun and paste into core/model/.../GroundMapTest.kt whenever registration_bench.ground_to_game changes.
Run: scripts/registration-bench/.venv/bin/python scripts/export_ground_map_golden.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import registration_bench as rb  # noqa: E402

CAM = {"s": 7.5, "sy": 3.1, "a": -0.0022, "px": 540.0, "py": 1510.0}
PIXELS = [(x, y) for y in (800, 1100, 1400) for x in (150, 540, 930)]
SCREENS = [(1080, 2340), (1080, 2400)]
for heading in (-20.0, 5.0, -21.0):
    cal = dict(CAM, theta_deg=heading)
    for w, h in SCREENS:
        for rx, ry in PIXELS:
            # screen pixel that lands on reference pixel (rx, ry); model scales it back linearly
            sx, sy = rx * w / rb.REF_W, ry * h / rb.REF_H
            e, n = rb.tap_to_ground_persp(cal, (rx, ry))
            print(f"G({heading}, {w}, {h}, {sx!r}, {sy!r}, {float(e)!r}, {float(n)!r}),")
