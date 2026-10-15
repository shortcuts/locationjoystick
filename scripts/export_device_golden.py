"""Export per-device parity fixtures for GroundMapFit / GroundMap from the bench walk legs.

One device at a time: its walk_C_* legs (captured through the Control API /walk, see
registration_bench.cmd_walkcap) -> band flows, feet pixel, perspective fit and tap-to-ground probes.
The Kotlin parity test (core/model DeviceParityTest) replays these and fails on drift past the tolerances in
docs/testing.md "Bench parity fixtures".

Run:
  scripts/registration-bench/.venv/bin/python scripts/export_device_golden.py pixel5_1080x2340 \
      scripts/registration-bench/data --model "Pixel 5" --density 440 --camera "fixed pitched 3D camera, closer zoom" \
      --captured-on 2026-10-13

Writes core/model/src/test/resources/device_golden/<device-name>/:
  meta.txt   "key value" lines: device, model, width, height, density_dpi, camera_state, captured_on, source
  moves.txt  "dE dN fx fy resp cx cy" per band, DEVICE pixels, bench order (same columns as flow_golden/moves.txt)
  fit.txt    line 1: "feetX feetY theta s sy a med_abs_px n" in REFERENCE pixels (1080x2340 frame)
             line 2: "feetDeviceX feetDeviceY" in device pixels
  taps.txt   "px py east north" per probe, device pixels; east/north from tap_to_ground_persp on the bench fit

Reference-frame conversion (x * 1080 / width, y * 2340 / height) happens here, before fit_perspective_horizon, so
the expected fit is in the same frame GroundMapFit.fit works in. Rerun when registration_bench.clean_leg,
band_moves, detect_feet, _feat, fit_perspective(_horizon) or tap_to_ground_persp change, or when the legs are
recaptured. Frames are never written: only numbers, see docs/features/tap-to-walk.md "Parity fixtures".
"""

import argparse
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import registration_bench as rb  # noqa: E402

REF_W, REF_H = 1080.0, 2340.0
PROBE_X = (200, 540, 880)
PROBE_Y = (700, 900, 1050, 1150, 1250, 1350, 1420)  # the validated range of the model, see tap-to-walk.md


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("device", help="directory name, e.g. pixel5_1080x2340")
    ap.add_argument("data", type=Path, help="directory holding walk_C_* leg folders")
    ap.add_argument("--model", required=True, help='marketing name, e.g. "Pixel 5"')
    ap.add_argument("--density", type=int, required=True, help="screen density in dpi (adb shell wm density)")
    ap.add_argument("--camera", required=True, help="game camera state while the legs were walked")
    ap.add_argument(
        "--captured-on",
        required=True,
        type=lambda v: datetime.strptime(v, "%Y-%m-%d").strftime("%Y-%m-%d"),
        help="date the legs were captured, YYYY-MM-DD",
    )
    args = ap.parse_args()

    legs = rb._commanded_legs(args.data)
    if not legs:
        sys.exit(f"no walk_C_* legs under {args.data}")
    first = next(f for fr in legs.values() for f in fr if f["img"] is not None)["img"]
    h, w = first.shape[:2]
    kx, ky = REF_W / w, REF_H / h

    feet = rb.detect_feet(legs)
    if feet is None:
        sys.exit("feet pixel not found -> no fixture written")
    moves = rb.band_moves(legs, feet)
    ref_moves = [(b, d, [(fx * kx, fy * ky, r, x * kx, y * ky) for fx, fy, r, x, y in fl]) for b, d, fl in moves]
    fit = rb.fit_perspective_horizon(ref_moves, (feet[0] * kx, feet[1] * ky))
    if fit is None:
        sys.exit("bench fit returned None (fewer than 40 flows) -> no fixture written")

    out = Path(__file__).parent.parent / "core/model/src/test/resources/device_golden" / args.device
    out.mkdir(parents=True, exist_ok=True)
    sources = [f"{args.data.name}/{p.name}" for p in sorted(args.data.glob("walk_C_*"))]

    (out / "meta.txt").write_text(
        "\n".join(
            [
                f"device {args.device}",
                f"model {args.model}",
                f"width {w}",
                f"height {h}",
                f"density_dpi {args.density}",
                f"camera_state {args.camera}",
                f"captured_on {args.captured_on}",
                f"source {' '.join(sources)}",
            ]
        )
        + "\n"
    )
    mlines = []
    for _, d, fl in moves:
        for fx, fy, resp, cx, cy in fl:
            mlines.append(f"{d[0]!r} {d[1]!r} {fx!r} {fy!r} {resp!r} {cx!r} {cy!r}")
    (out / "moves.txt").write_text("\n".join(mlines) + "\n")

    (out / "fit.txt").write_text(
        f"{feet[0] * kx!r} {feet[1] * ky!r} {fit['theta_deg']!r} {fit['s']!r} {fit['sy']!r} {fit['a']!r} "
        f"{fit['med_abs_px']!r} {fit['n']}\n"
        f"{feet[0]!r} {feet[1]!r}\n"
    )

    tlines = []
    for x in PROBE_X:
        for y in PROBE_Y:
            east, north = (float(v) for v in rb.tap_to_ground_persp(fit, (x, y)))
            px, py = x / kx, y / ky
            tlines.append(f"{px!r} {py!r} {east!r} {north!r}")
    (out / "taps.txt").write_text("\n".join(tlines) + "\n")

    print(f"{args.device}: {w}x{h} feet(device)={feet[0]:.1f} {feet[1]:.1f} moves={len(mlines)} "
          f"fit theta={fit['theta_deg']:.2f} s={fit['s']:.3f} sy={fit['sy']:.3f} a={fit['a']:.6f} "
          f"med={fit['med_abs_px']:.3f} n={fit['n']} -> {out}")


if __name__ == "__main__":
    main()
