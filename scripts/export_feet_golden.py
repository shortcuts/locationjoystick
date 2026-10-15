"""Export golden frames + expected feet pixel for FeetDetectorTest from the bench detect_feet.

Rerun whenever registration_bench.detect_feet changes.
Run: scripts/registration-bench/.venv/bin/python scripts/export_feet_golden.py
Writes core/model/src/test/resources/feet_golden/legs.raw (uint8 gray, 2 legs x 4 frames x 120x260, leg-major)
and expected.txt ("x y", repr floats).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import registration_bench as rb  # noqa: E402
from test_registration_bench import _scrolling_legs  # noqa: E402

W, H, FRAMES = 120, 260, 4
legs = _scrolling_legs(W, H, 0.66)
legs = {b: fr[:FRAMES] for b, fr in legs.items()}
x, y = rb.detect_feet(legs)
out = Path(__file__).parent.parent / "core/model/src/test/resources/feet_golden"
out.mkdir(parents=True, exist_ok=True)
raw = b"".join(rb._to_gray(f["img"]).astype(np.uint8).tobytes() for fr in legs.values() for f in fr)
(out / "legs.raw").write_bytes(raw)
(out / "expected.txt").write_text(f"{x!r} {y!r}\n")
print(len(raw), x, y)
