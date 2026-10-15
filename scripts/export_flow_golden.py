"""Export golden vectors for FrameFlowTest / GroundMapFitTest from the bench band_moves and perspective fit.

Rerun whenever registration_bench.clean_leg, band_moves, _feat, fit_perspective(_horizon) change.
Run: scripts/registration-bench/.venv/bin/python scripts/export_flow_golden.py
Writes core/model/src/test/resources/flow_golden/:
  pairs.txt  "dE dN fx fy resp cx cy" per band, moves in bench order (procedural frames, see FrameFlowTest.Scene)
  moves.txt  "dataset dE dN fx fy resp cx cy" synthetic band flows of a known camera
  fit.txt    "dataset theta s sy a med_abs_px n" bench fit of each dataset
No image files: the frames are rebuilt from the formula in both languages.
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import registration_bench as rb  # noqa: E402

W, H, FRAMES = 1080, 2340, 9  # 9 frames: fewer let chance 'static' speckle (dev < 3 and edge > 60) into the mask
FEET = (540.0, 1500.0)
KS = (2, 3)
STEP_M = 1.3  # metres per frame along the bearing
STEP_PX = {0.0: (0, 9), 90.0: (9, 0)}  # world px the view scrolls per frame
LATTICE = 10  # px between value-noise lattice points


def _lattice(i, j):
    """Integer-hash value 0..255 at lattice point (i, j); the same hash is in FrameFlowTest.Scene."""
    h = ((i.astype(np.uint64) * np.uint64(73856093)) ^ (j.astype(np.uint64) * np.uint64(19349663))) & np.uint64(0xFFFFFFFF)
    h = (h * np.uint64(2654435761)) & np.uint64(0xFFFFFFFF)
    return ((h >> np.uint64(16)) & np.uint64(0xFF)).astype(np.float64)


def frame(t, dx, dy, w=W, h=H, feet=FEET):
    """Smooth value-noise world at (x + dx t, y + dy t) plus a static HUD checkerboard and a ring around the feet."""
    yy, xx = np.mgrid[0:h, 0:w]
    xx = xx + dx * t
    yy = yy + dy * t
    i, j = xx // LATTICE, yy // LATTICE
    fx = (xx - i * LATTICE) / LATTICE
    fy = (yy - j * LATTICE) / LATTICE
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    top = _lattice(i, j) * (1 - fx) + _lattice(i + 1, j) * fx
    bot = _lattice(i, j + 1) * (1 - fx) + _lattice(i + 1, j + 1) * fx
    v = 128.0 + 0.9 * ((top * (1 - fy) + bot * fy) - 127.5)
    img = np.clip(np.rint(v), 0, 255).astype(np.uint8)
    # HUD: static 4 px checkerboard (icons and text are high-gradient, so the whole rectangle is masked)
    sc = w / 1080.0
    r0, r1, c0, c1 = int(900 * sc), int(1000 * sc), int(60 * sc), int(260 * sc)
    hx, hy = np.mgrid[r0:r1, c0:c1]
    img[r0:r1, c0:c1] = np.where(((hy // 4 + hx // 4) % 2) == 0, 255, 0)
    sy, sx = np.mgrid[0:h, 0:w]
    ring = np.abs(np.hypot(sx - feet[0], sy - feet[1]) - 160.0 * sc) <= 3.0
    img[ring] = 255
    return img


def legs_for():
    lat0, lon0 = 45.0, 7.0
    legs = {}
    for b, (dx, dy) in STEP_PX.items():
        fr = []
        for t in range(FRAMES):
            a = math.radians(b)
            north = t * STEP_M * math.cos(a)
            east = t * STEP_M * math.sin(a)
            ll = (lat0 + north / 111320.0, lon0 + east / (111320.0 * math.cos(math.radians(lat0))))
            fr.append({"ll": ll, "img": frame(t, dx, dy), "moving": True})
        legs[b] = fr
    return legs


def synthetic(cam, theta, horizon_cut=None):
    h = rb.ground_to_game(cam, theta)
    hi = np.linalg.inv(h)
    out = []
    n = 0
    for b in (0, 45, 90, 135, 180, 225, 270, 315):
        d = 6.0 * np.array([math.sin(math.radians(b)), math.cos(math.radians(b))])
        for fy in rb.FLOW_ROWS:
            y = (fy[0] + fy[1]) / 2 * 2340
            for x in (180.0, 540.0, 900.0):
                f = rb.predict_flow(h, hi, d, (x, y))
                if horizon_cut is not None and y <= horizon_cut:
                    f = f * 0.4  # far content moves less than the model says
                n += 1
                noise = 0.5 * np.array([math.sin(1.7 * n), math.cos(2.3 * n)])
                if n % 17 == 0:
                    noise = noise + np.array([25.0, -18.0])  # outlier
                out.append((tuple(float(v) for v in d), tuple(float(v) for v in (*(f + noise), 0.9, x, y))))
    return out


def main():
    out = Path(__file__).parent.parent / "core/model/src/test/resources/flow_golden"
    out.mkdir(parents=True, exist_ok=True)
    moves = rb.band_moves(legs_for(), FEET, ks=KS)
    lines = []
    for b, d, fl in moves:
        dx, dy = STEP_PX[b]
        for fx, fy, resp, cx, cy in fl:
            lines.append(f"{d[0]!r} {d[1]!r} {fx!r} {fy!r} {resp!r} {cx!r} {cy!r}")
    (out / "pairs.txt").write_text("\n".join(lines) + "\n")
    print("pairs", len(moves), "moves")
    # sanity: a clean band's flow is the integer scroll (content moves opposite to the view)
    b, d, fl = moves[0]
    k = round(math.hypot(*d) / STEP_M)
    dx, dy = STEP_PX[b]
    assert abs(fl[0][0] + dx * k) < 1.0 and abs(fl[0][1] + dy * k) < 1.0, (fl[0], dx, dy, k)

    datasets = {
        "A": synthetic({"s": 7.5, "sy": 3.1, "a": -0.0022, "px": 540.0, "py": 1510.0}, -20.0),
        "B": synthetic({"s": 7.5, "sy": -1.54, "a": -0.0022, "px": 540.0, "py": 1510.0}, -20.0, 700 + 0.03 * 2340),
    }
    mlines, flines = [], []
    for name, rows in datasets.items():
        mv = [(0.0, d, [f]) for d, f in rows]
        fit = rb.fit_perspective_horizon(mv, (540.0, 1510.0))
        flines.append(f"{name} {fit['theta_deg']!r} {fit['s']!r} {fit['sy']!r} {fit['a']!r} {fit['med_abs_px']!r} {fit['n']}")
        print(name, fit)
        for d, f in rows:
            mlines.append(f"{name} {d[0]!r} {d[1]!r} " + " ".join(repr(float(v)) for v in f))
    (out / "moves.txt").write_text("\n".join(mlines) + "\n")
    (out / "fit.txt").write_text("\n".join(flines) + "\n")


main()
