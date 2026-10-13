#!/usr/bin/env python3
"""Self-check for registration_bench.py. Run directly: scripts/registration-bench/.venv/bin/python scripts/test_registration_bench.py"""

import importlib.util
import math
from pathlib import Path

import cv2
import numpy as np

spec = importlib.util.spec_from_file_location(
    "registration_bench", Path(__file__).parent / "registration_bench.py"
)
rb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rb)


def synthetic_map(seed, size=512):
    rng = np.random.default_rng(seed)
    img = np.zeros((size, size), np.uint8)
    for _ in range(25):
        pts = rng.integers(0, size, size=(rng.integers(2, 5), 2)).astype(np.int32)
        cv2.polylines(img, [pts], False, 255, int(rng.integers(1, 4)))
    return img


def test_similarity_from_points_recovers_known_transform():
    true = cv2.getRotationMatrix2D((100, 50), 25.0, 1.3)
    src = np.array([[0, 0], [400, 0], [0, 300], [400, 300], [200, 150]], dtype=np.float64)
    dst = src @ true[:, :2].T + true[:, 2]
    est = rb.similarity_from_points(src, dst)
    assert np.allclose(est, true, atol=1e-6)


def test_landing_error_zero_for_identical_and_linear_in_bearing_error():
    m = cv2.getRotationMatrix2D((540, 960), 0.0, 1.0)
    assert rb.landing_error_m(m, m, 1080, 2400, 0.23) == 0.0
    # 1 degree of rotation about the centre moves the far corner by ~ half-diagonal * sin(1 deg).
    rot = cv2.getRotationMatrix2D((540, 1200), 1.0, 1.0)
    err_m = rb.landing_error_m(rot, np.array([[1, 0, 0], [0, 1, 0]], dtype=np.float64), 1080, 2400, 1.0)
    half_diag = math.hypot(540, 1200)
    assert abs(err_m - half_diag * math.sin(math.radians(1.0))) < 0.5


def test_logpolar_recovers_rotation_and_scale():
    ref = synthetic_map(seed=1)
    true = cv2.getRotationMatrix2D((256, 256), 30.0, 1.15)
    game = cv2.warpAffine(ref, true, (512, 512))
    est = rb.register_logpolar(ref, game)
    # cv2.getRotationMatrix2D uses m[1,0] = -s*sin(angle), so negate to get the cv2 angle back.
    est_angle = -math.degrees(math.atan2(est[1, 0], est[0, 0]))
    est_scale = math.hypot(est[0, 0], est[1, 0])
    assert abs(est_angle - 30.0) < 0.5, est_angle
    assert abs(est_scale / 1.15 - 1.0) < 0.01, est_scale


def test_chamfer_accepts_true_alignment_and_rejects_unrelated_map():
    ref = synthetic_map(seed=2)
    true = cv2.getRotationMatrix2D((256, 256), 12.0, 1.05)
    game = cv2.warpAffine(ref, true, (512, 512))
    unrelated = synthetic_map(seed=3)
    # Placeholder separation only: the real tau is calibrated on the device dataset in the report step.
    tau = 0.005
    aligned = rb.chamfer_score(cv2.warpAffine(ref, true, (512, 512)) > 0, game > 0)
    wrong = rb.chamfer_score(unrelated > 0, game > 0)
    assert aligned <= tau < wrong, (aligned, wrong)


def textured_map(seed, size=512):
    """Blurred noise plus strokes: enough local structure for AKAZE to find stable keypoints."""
    rng = np.random.default_rng(seed)
    noise = rng.integers(0, 256, size=(size, size), dtype=np.uint8)
    img = cv2.GaussianBlur(noise, (0, 0), 2.0)
    img = cv2.normalize(img, None, 0, 255, cv2.NORM_MINMAX)
    return cv2.addWeighted(img, 0.7, synthetic_map(seed, size), 0.3, 0)


def test_homography_recovers_pitched_view_and_scores_unrelated_lower():
    ref = textured_map(seed=4)
    size = 512
    src = np.float32([[0, 0], [size, 0], [size, size], [0, size]])
    # Pitched camera: the top edge of the view is narrower than the bottom edge.
    dst = np.float32([[size * 0.2, 0], [size * 0.8, 0], [size, size], [0, size]])
    true = cv2.getPerspectiveTransform(src, dst)
    game = cv2.warpPerspective(ref, true, (size, size))
    est, inlier_ratio = rb.register_homography(ref, game)
    assert est is not None
    err = rb.landing_error_m(est, true, size, size, 1.0)
    assert err < 2.0, err
    _, wrong_ratio = rb.register_homography(textured_map(seed=5), game)
    assert inlier_ratio > wrong_ratio, (inlier_ratio, wrong_ratio)


def test_truth_from_annotations_recovers_known_homography():
    size = 512
    src = np.float32([[0, 0], [size, 0], [size, size], [0, size]])
    dst = np.float32([[size * 0.2, 0], [size * 0.8, 0], [size, size], [0, size]])
    known = cv2.getPerspectiveTransform(src, dst)
    # 8 annotated ref points spread over the frame, mapped through the known homography.
    ref_pts = np.array([[10, 20], [500, 30], [40, 250], [480, 260], [256, 10],
                        [256, 500], [120, 400], [400, 120]], dtype=np.float64)
    game_pts = rb._apply(known, ref_pts)
    truth = rb.truth_from_annotations(np.hstack([ref_pts, game_pts]))
    assert np.allclose(truth / truth[2, 2], known / known[2, 2], atol=1e-6)
    assert rb.landing_error_m(truth, truth, size, size, 0.23) == 0.0
    # A one-pixel annotation shift gives a small, non-zero error.
    shifted = game_pts.copy()
    shifted[0, 0] += 1.0
    off = rb.truth_from_annotations(np.hstack([ref_pts, shifted]))
    err = rb.landing_error_m(off, truth, size, size, 0.23)
    assert 0.0 < err < 1.0, err


def test_register_unpitched_recovers_heading_and_tap_landing_error():
    cam = {"s": 7.0, "sy": 3.0, "a": -0.0008, "px": 540.0, "py": 1400.0}
    ref = cv2.GaussianBlur(synthetic_map(seed=3, size=1100), (0, 0), 2)
    marker, mpp = (550, 550), 0.6
    true = rb.ground_to_game(cam, 47.0) @ rb.ref_to_ground(marker, mpp)
    game = cv2.warpPerspective(ref, true, (1080, 2340))
    est, score = rb.register_unpitched(ref, game, cam, marker, mpp)
    err = rb.tap_landing_error_m(est, rb.ground_to_game(cam, 47.0), rb.TAP_PROBES)
    assert err < 1.0, err
    assert score > 0.3, score
    wrong = rb.tap_landing_error_m(rb.ground_to_game(cam, 0.0), rb.ground_to_game(cam, 47.0), rb.TAP_PROBES)
    assert wrong > 20.0, wrong


def _flows(s, sy, theta_deg, moves):
    r = math.radians(theta_deg)
    a = np.array([[s * math.cos(r), -s * math.sin(r)], [-sy * math.sin(r), -sy * math.cos(r)]])
    return [tuple(-(a @ np.asarray(d, dtype=np.float64))) for d in moves]


def test_heading_from_moves_recovers_known_heading_and_scales():
    d1, d2 = (0.0, 8.0), (8.0, 0.0)
    f1, f2 = _flows(7.0, 3.0, 47.0, [d1, d2])
    est = rb.heading_from_moves(d1, f1, d2, f2)
    assert abs(est["theta_deg"] - 47.0) < 0.01, est
    assert abs(est["s"] / 7.0 - 1) < 0.01 and abs(est["sy"] / 3.0 - 1) < 0.01, est
    cam = {"s": 1.0, "sy": 1.0, "a": 0.0, "px": 540.0, "py": 1400.0}
    assert np.allclose(rb.motion_g_est(est, cam), rb.ground_to_game({**cam, "s": 7.0, "sy": 3.0}, 47.0), atol=1e-3)


def test_heading_from_moves_returns_none_when_low_confidence():
    d1, d2 = (0.0, 8.0), (8.0, 0.0)
    f1, f2 = _flows(7.0, 3.0, 47.0, [d1, d2])
    assert rb.heading_from_moves(d1, f1, (0.0, 4.0), (f1[0] / 2, f1[1] / 2)) is None  # parallel
    assert rb.heading_from_moves((0.0, 2.0), f1, d2, f2) is None  # move under 3 m
    assert rb.heading_from_moves(d1, (1.0, 1.0), d2, f2) is None  # flow under 5 px
    bad = _flows(7.0, 3.0, 47.0, [d1])[0], _flows(7.0, 3.0, 80.0, [d2])[0]  # rows disagree by 33 deg
    assert rb.heading_from_moves(d1, bad[0], d2, bad[1]) is None


def test_content_flow_recovers_known_shift():
    canvas = np.zeros((2340, 1080), np.uint8)
    canvas[500:1400, :] = cv2.resize(textured_map(seed=6), (1080, 900))
    shifted = cv2.warpAffine(canvas, np.float32([[1, 0, -12], [0, 1, 7]]), (1080, 2340))
    fx, fy, resp = rb.content_flow(canvas, shifted)
    assert abs(fx + 12) < 0.5 and abs(fy - 7) < 0.5 and resp > 0.3, (fx, fy, resp)


def test_commanded_target_and_along_track_move_drop_perpendicular_jitter():
    lat0, lon0 = 48.8615, 2.3365
    tgt = rb.commanded_target(lat0, lon0, 45.0, 40.0)
    e, n = rb.along_track_move((lat0, lon0), tgt, 45.0, lat0)
    assert abs(e - 40 * math.sin(math.radians(45))) < 0.1 and abs(n - 40 * math.cos(math.radians(45))) < 0.1
    # 2 m perpendicular (south-east) offset on the end point is dropped.
    off = (tgt[0] - 2 * math.cos(math.radians(45)) / 111320,
           tgt[1] + 2 * math.sin(math.radians(45)) / (111320 * math.cos(math.radians(lat0))))
    e2, n2 = rb.along_track_move((lat0, lon0), off, 45.0, lat0)
    assert abs(e2 - e) < 0.1 and abs(n2 - n) < 0.1, (e2, n2)


def test_commanded_move_direction_beats_jittered_positions_for_heading():
    s, sy, theta, lat0 = 7.0, 3.0, 33.0, 48.8615
    rng = np.random.default_rng(7)
    p0 = (lat0, 2.3365)
    errs_cmd, errs_raw = [], []
    for _ in range(40):
        pts = []
        for b in (0.0, 90.0):
            p1 = rb.commanded_target(*p0, b, 8.0)
            perp = rng.normal(0, 2.0)  # polled position off the track, perpendicular only
            jit = (-perp * math.sin(math.radians(b)), perp * math.cos(math.radians(b)))  # (north, east)
            pj = (p1[0] + jit[0] / 111320, p1[1] + jit[1] / (111320 * math.cos(math.radians(lat0))))
            exact = ((8.0 * math.sin(math.radians(b))), 8.0 * math.cos(math.radians(b)))
            f = _flows(s, sy, theta, [exact])[0]
            pts.append((b, pj, f))
        raw = [((pj[1] - p0[1]) * 111320 * math.cos(math.radians(lat0)), (pj[0] - p0[0]) * 111320)
               for _, pj, _ in pts]
        cmd = [rb.along_track_move(p0, pj, b, lat0) for b, pj, _ in pts]
        e_cmd = rb.heading_from_moves(cmd[0], pts[0][2], cmd[1], pts[1][2], max_skew_deg=90)
        e_raw = rb.heading_from_moves(raw[0], pts[0][2], raw[1], pts[1][2], max_skew_deg=90)
        errs_cmd.append(abs(e_cmd["theta_deg"] - theta))
        errs_raw.append(abs(e_raw["theta_deg"] - theta) if e_raw else 90.0)
    assert max(errs_cmd) < 0.1, errs_cmd
    assert np.mean(errs_cmd) < np.mean(errs_raw), (np.mean(errs_cmd), np.mean(errs_raw))


def test_fit_ground_map_recovers_heading_with_outliers_and_rejects_one_bearing():
    rng = np.random.default_rng(3)
    for theta in (33.0, 200.0):
        moves = []
        for b in range(0, 360, 45):
            for ln in (8.0, 12.0, 16.0, 20.0):
                d = (ln * math.sin(math.radians(b)), ln * math.cos(math.radians(b)))
                f = np.array(_flows(7.0, 3.0, theta, [d])[0]) + rng.normal(0, 0.3, 2)
                moves.append((d, tuple(f)))
        moves[2] = (moves[2][0], (40.0, -35.0))
        moves[9] = (moves[9][0], (-30.0, 25.0))
        moves[20] = (moves[20][0], (0.0, 45.0))
        fit = rb.fit_ground_map(moves)
        diff = abs((fit["theta_deg"] - theta + 180) % 360 - 180)
        assert diff < 1.0 and fit["n_used"] <= len(moves) - 3, (theta, fit)
    same = [((0.0, ln), tuple(_flows(7.0, 3.0, 33.0, [(0.0, ln)])[0])) for ln in np.linspace(5, 30, 20)]
    assert rb.fit_ground_map(same) is None


def test_tap_to_ground_inverts_ground_to_screen_map():
    a = -np.array([_flows(7.0, 3.0, 33.0, [(1.0, 0.0), (0.0, 1.0)])]).reshape(2, 2).T
    anchor, g = np.array([520.0, 1560.0]), np.array([-23.0, 41.5])
    assert np.allclose(rb.tap_to_ground(a, anchor + a @ g, anchor), g, atol=1e-6)


def test_patch_offset_finds_shifted_content_and_ignores_player_sprite():
    rng = np.random.default_rng(11)
    canvas = cv2.GaussianBlur(rng.integers(0, 256, (2340, 1080), dtype=np.uint8), (0, 0), 3)
    anchor, p, want = (520, 1560), (640, 1440), (9, -6)
    shift = (anchor[0] - p[0] + want[0], anchor[1] - p[1] + want[1])
    after = cv2.warpAffine(canvas, np.float32([[1, 0, shift[0]], [0, 1, shift[1]]]), (1080, 2340))
    for img in (canvas, after):
        cv2.circle(img, anchor, 30, 255, -1)  # player sprite
    off, ncc = rb.patch_offset(canvas, after, p, anchor)
    assert abs(off[0] - want[0]) < 3 and abs(off[1] - want[1]) < 3 and ncc > 0.4, (off, ncc)


def test_frame_meta_has_only_state_fields():
    st = {"position": {"lat": 1.0, "lon": 2.0}, "speedMs": 1.4, "bearing": 45.0, "key": "SECRET-SENTINEL"}
    row = rb.frame_meta(3, 9.0, 45.0, st, st)
    assert "SECRET-SENTINEL" not in repr(row) and row["pos"] == [1.0, 2.0] and row["state_bearing"] == 45.0


def _needle_frame(angle_deg, size=40, w=1080, h=2340):
    """Blank frame with a red teardrop (tip toward angle_deg, clockwise from up) and a white tail in the top-right."""
    img = np.zeros((h, w, 3), np.uint8)
    cx, cy = 985, 150
    a = math.radians(angle_deg)
    up = np.array([math.sin(a), -math.cos(a)])
    side = np.array([-up[1], up[0]])
    c = np.array([cx, cy], float)
    tri = lambda tip, base: np.array([c + up * tip, c + side * base - up * 2, c - side * base - up * 2]).astype(np.int32)
    cv2.fillPoly(img, [tri(size / 2, size / 4)], (0, 0, 255))
    cv2.fillPoly(img, [-tri(size / 2, size / 4) + 2 * c.astype(np.int32)], (255, 255, 255))
    return img


def test_needle_heading_reads_rotated_synthetic_needle():
    for ang in (0, 40, -40, 135, -170):
        got = rb.needle_heading(_needle_frame(ang))
        assert got is not None and abs((got["angle_deg"] - ang + 180) % 360 - 180) < 8, (ang, got)
    assert rb.needle_heading(np.zeros((2340, 1080, 3), np.uint8)) is None
    assert rb.needle_heading(_needle_frame(0, size=400)) is None  # larger than 0.06 of the short side


def test_twist_events_pairs_two_fingers_and_releases():
    ev = rb.twist_events(540, 1500, 250, 0.0, 40.0)
    xs = [v for t, c, v in ev if t == 3 and c == 0x35]
    ys = [v for t, c, v in ev if t == 3 and c == 0x36]
    assert len(xs) == len(ys) == 48 and all(0 <= x <= 1079 for x in xs) and all(0 <= y <= 2339 for y in ys)
    assert {v for t, c, v in ev if c == 0x2F} == {0, 1}
    assert [v for t, c, v in ev if c == 0x39 and v == -1] == [-1, -1]
    assert ev[-2:] == [(3, 0x39, -1), (0, 0, 0)]


def test_pinch_events_spread_symmetric_and_condition_parse():
    ev = rb.pinch_events(540, 1500, 200, 320)
    xs = [v for t, c, v in ev if t == 3 and c == 0x35]
    assert xs[0:2] == [740, 340] and xs[-2:] == [860, 220] and {v for t, c, v in ev if c == 0x36} == {1500}
    assert ev[-2:] == [(3, 0x39, -1), (0, 0, 0)]
    assert rb.parse_condition("1.6:-40") == (1.6, -40.0) and rb.condition_name(1.6, -40.0) == "z1.6_t-40"


def test_perspective_fit_recovers_known_camera_and_tap_round_trips():
    cam = {"s": 7.5, "sy": 3.1, "a": -0.0022, "px": 540.0, "py": 1510.0}
    h = rb.ground_to_game(cam, -20.0)
    hi = np.linalg.inv(h)
    rng = np.random.default_rng(1)
    moves = []
    for b in (0, 45, 90, 135, 180, 225, 270, 315):
        d = 6.0 * np.array([math.sin(math.radians(b)), math.cos(math.radians(b))])
        fl = [tuple(rb.predict_flow(h, hi, d, (x, y)) + rng.normal(0, 0.5, 2)) + (0.9, x, y)
              for y in (600, 800, 1000, 1200, 1350) for x in (250, 850)]
        moves += [(float(b), d, fl)] * 6
    fit = rb.fit_perspective(moves, (540.0, 1510.0))
    assert abs(fit["theta_deg"] - (-20.0)) < 1.0 and abs(fit["a"] - cam["a"]) < 4e-4, fit
    g = rb.tap_to_ground_persp(fit, (700, 1100))
    want = rb._h_apply(hi, (700, 1100)) - rb._h_apply(hi, (540, 1510))
    assert np.linalg.norm(g - want) < 1.5


if __name__ == "__main__":
    tests = [v for k, v in list(globals().items()) if k.startswith("test_")]
    for t in tests:
        t()
        print(f"ok {t.__name__}")
    print(f"{len(tests)} passed")
