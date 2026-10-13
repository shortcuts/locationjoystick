#!/usr/bin/env python3
"""Bearing-registration harness for tap-to-walk: game-map screenshot -> rotation + scale against our own map.

Step 1 of the investigation: pure geometry helpers plus one registration method (log-polar phase
correlation on the FFT magnitude, rotation and scale in one step). Annotate/run/report subcommands are
added once a device-captured dataset exists (see docs/features/tap-to-walk.md, "Bearing registration
investigation"). The `motion` subcommand scores the motion-calibrated heading prototype (content_flow,
heading_from_moves) on stored walk frames. `varycap` captures walk legs under varied zoom and orientation.

Setup (venv is gitignored):
    python3 -m venv scripts/registration-bench/.venv
    scripts/registration-bench/.venv/bin/pip install opencv-python-headless numpy

Self-check:
    scripts/registration-bench/.venv/bin/python scripts/test_registration_bench.py
"""

import math
from pathlib import Path

import cv2
import numpy as np

# MapLibre GL world size: 512 px tiles, Web Mercator equator circumference / 512 px.
EARTH_EQUATOR_M_PER_PX_AT_Z0 = 78271.517


def similarity_from_points(src, dst):
    """Least-squares 2x3 similarity transform mapping src points onto dst points."""
    src = np.asarray(src, dtype=np.float64)
    dst = np.asarray(dst, dtype=np.float64)
    n = len(src)
    a = np.zeros((2 * n, 4))
    b = dst.reshape(-1)
    a[0::2] = np.column_stack([src[:, 0], -src[:, 1], np.ones(n), np.zeros(n)])
    a[1::2] = np.column_stack([src[:, 1], src[:, 0], np.zeros(n), np.ones(n)])
    p, *_ = np.linalg.lstsq(a, b, rcond=None)
    return np.array([[p[0], -p[1], p[2]], [p[1], p[0], p[3]]])


def truth_from_annotations(points):
    """3x3 ground-truth homography (ref -> game) from annotated rows [rx, ry, gx, gy]; least squares."""
    pts = np.asarray(points, dtype=np.float64)
    h, _ = cv2.findHomography(pts[:, :2], pts[:, 2:4], 0)
    return h


def _apply(m, pts):
    """Map Nx2 points through a 2x3 affine or 3x3 homography (with perspective divide)."""
    m = np.asarray(m, dtype=np.float64)
    homog = np.hstack([pts, np.ones((len(pts), 1))])
    out = homog @ m.T
    if m.shape == (3, 3):
        return out[:, :2] / out[:, 2:3]
    return out


def landing_error_m(est, truth, width, height, m_per_px):
    """Max distance (m) between where est and truth put 8 probe pixels: 4 corners + 4 edge midpoints.

    est and truth may each be a 2x3 similarity or a 3x3 homography (pitched game camera).
    """
    probes = np.array(
        [
            [0, 0], [width - 1, 0], [0, height - 1], [width - 1, height - 1],
            [width / 2, 0], [width / 2, height - 1], [0, height / 2], [width - 1, height / 2],
        ],
        dtype=np.float64,
    )
    pe = _apply(est, probes)
    pt = _apply(truth, probes)
    return float(np.max(np.linalg.norm(pe - pt, axis=1)) * m_per_px)


def chamfer_score(edges_a, edges_b):
    """Symmetric mean chamfer distance between two boolean edge maps, in pixels of the short side."""
    if not edges_a.any() or not edges_b.any():
        return float("inf")
    dist_a = cv2.distanceTransform((~edges_a).astype(np.uint8), cv2.DIST_L2, 3)
    dist_b = cv2.distanceTransform((~edges_b).astype(np.uint8), cv2.DIST_L2, 3)
    d_ab = dist_b[edges_a].mean()
    d_ba = dist_a[edges_b].mean()
    short_side = min(edges_a.shape)
    return float((d_ab + d_ba) / 2 / short_side)


def m_per_px_reference(lat_deg, zoom, density):
    """Ground metres per device pixel for our rendered map at a latitude, zoom and screen density."""
    return EARTH_EQUATOR_M_PER_PX_AT_Z0 * math.cos(math.radians(lat_deg)) / (2**zoom) / density


def _spectrum(img):
    h, w = img.shape
    window = np.outer(np.hanning(h), np.hanning(w)).astype(np.float32)
    mag = np.abs(np.fft.fftshift(np.fft.fft2(img.astype(np.float32) * window)))
    return np.log1p(mag).astype(np.float32)


def _ncc(a, b):
    a = a.astype(np.float32).ravel()
    b = b.astype(np.float32).ravel()
    a = a - a.mean()
    b = b - b.mean()
    denom = float(np.sqrt((a * a).sum() * (b * b).sum())) or 1.0
    return float((a * b).sum() / denom)


def register_logpolar(ref_gray, game_gray):
    """Rotation + scale of game relative to ref, as a 2x3 matrix mapping ref pixels to game pixels.

    Log-polar phase correlation on the FFT magnitude (Reddy-Chatterji). The magnitude spectrum of a real
    image is centrosymmetric, so the angle is ambiguous by 180 degrees; both candidates are scored by
    normalised cross-correlation of the warped images and the better one is returned.
    """
    h, w = ref_gray.shape
    center = (w / 2.0, h / 2.0)
    max_radius = min(h, w) / 2.0
    lp_ref = cv2.warpPolar(_spectrum(ref_gray), (w, h), center, max_radius, cv2.WARP_POLAR_LOG)
    lp_game = cv2.warpPolar(_spectrum(game_gray), (w, h), center, max_radius, cv2.WARP_POLAR_LOG)
    (dx, dy), _ = cv2.phaseCorrelate(lp_ref, lp_game)

    log_step = math.log(max_radius) / w
    scale = math.exp(-dx * log_step)
    angle = -dy * 360.0 / h

    best, best_score = None, -np.inf
    for cand in (angle, angle + 180.0):
        m = cv2.getRotationMatrix2D(center, cand, scale)
        warped = cv2.warpAffine(ref_gray, m, (w, h))
        score = _ncc(warped, game_gray)
        if score > best_score:
            best, best_score = m, score
    return best


def register_homography(ref_gray, game_gray, ratio=0.8, reproj_px=3.0):
    """Perspective (pitched camera) registration: 3x3 homography mapping ref pixels to game pixels.

    ORB features on both images, Hamming matching with Lowe's ratio test, then RANSAC findHomography.
    Returns (H, inlier_ratio) or (None, 0.0) when there are too few matches. inlier_ratio is the
    verification score: RANSAC inliers over ratio-test matches.
    """
    orb = cv2.ORB_create(nfeatures=2000)
    kp_ref, des_ref = orb.detectAndCompute(ref_gray, None)
    kp_game, des_game = orb.detectAndCompute(game_gray, None)
    if des_ref is None or des_game is None or len(kp_ref) < 4 or len(kp_game) < 4:
        return None, 0.0
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING)
    knn = matcher.knnMatch(des_ref, des_game, k=2)
    good = [p[0] for p in knn if len(p) == 2 and p[0].distance < ratio * p[1].distance]
    if len(good) < 4:
        return None, 0.0
    src = np.float32([kp_ref[m.queryIdx].pt for m in good])
    dst = np.float32([kp_game[m.trainIdx].pt for m in good])
    h, inliers = cv2.findHomography(src, dst, cv2.RANSAC, reproj_px)
    if h is None:
        return None, 0.0
    return h, float(inliers.sum()) / len(good)


def _to_gray(img_bgr):
    return cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)


def register_orb_similarity(ref_gray, game_gray):
    """ORB + RANSAC similarity (rotation, scale, translation). Returns (2x3 or None, inlier_ratio)."""
    orb = cv2.ORB_create(nfeatures=2000)
    kp_ref, des_ref = orb.detectAndCompute(ref_gray, None)
    kp_game, des_game = orb.detectAndCompute(game_gray, None)
    if des_ref is None or des_game is None or len(kp_ref) < 3 or len(kp_game) < 3:
        return None, 0.0
    knn = cv2.BFMatcher(cv2.NORM_HAMMING).knnMatch(des_ref, des_game, k=2)
    good = [p[0] for p in knn if len(p) == 2 and p[0].distance < 0.8 * p[1].distance]
    if len(good) < 3:
        return None, 0.0
    src = np.float32([kp_ref[m.queryIdx].pt for m in good])
    dst = np.float32([kp_game[m.trainIdx].pt for m in good])
    m, inliers = cv2.estimateAffinePartial2D(src, dst, method=cv2.RANSAC, ransacReprojThreshold=3.0)
    if m is None:
        return None, 0.0
    return m, float(inliers.sum()) / len(good)


def ground_to_game(cam, theta_deg):
    """3x3 homography from ground metres (east, north, relative to the player) to game pixels.

    cam holds the fixed pitched-camera terms {s, sy, a, px, py} (px per metre across, px per metre up,
    perspective term, player pixel); theta_deg is the map heading: how far north is rotated in the frame.
    """
    t = math.radians(theta_deg)
    rot = np.array([[math.cos(t), -math.sin(t), 0], [math.sin(t), math.cos(t), 0], [0, 0, 1]])
    cam_h = np.array([[cam["s"], 0, cam["px"]], [0, -cam["sy"], cam["py"]], [0, -cam["a"], 1]])
    return cam_h @ rot


def ref_to_ground(marker, m_per_px):
    """3x3 homography from our map pixels (north up) to ground metres east/north of the marker."""
    return np.array([[m_per_px, 0, -m_per_px * marker[0]], [0, -m_per_px, m_per_px * marker[1]], [0, 0, 1]])


def tap_landing_error_m(g_est, g_true, probes):
    """Max distance (m) between where two ground->game homographies put the same tapped game pixels."""
    pts = np.asarray(probes, dtype=np.float64)
    if abs(np.linalg.det(g_est)) < 1e-12:
        return float("inf")
    est = _apply(np.linalg.inv(g_est), pts)
    tru = _apply(np.linalg.inv(g_true), pts)
    return float(np.max(np.linalg.norm(est - tru, axis=1)))


# Game pixels a user might tap: a 3x3 grid over the ground band (not the HUD, not the player's feet).
TAP_PROBES = [(x, y) for x in (150, 540, 930) for y in (500, 900, 1300)]


def _feat(img):
    """Local-contrast luminance (gray input) or park-greenness (BGR input), blurred for coarse alignment."""
    if img.ndim == 3:
        b, g, r = (img[..., i].astype(np.float32) for i in range(3))
        return cv2.GaussianBlur(g - np.maximum(r, b), (0, 0), 4)
    g = img.astype(np.float32)
    d = g - cv2.GaussianBlur(g, (0, 0), 6)
    return cv2.GaussianBlur(d / (np.sqrt(cv2.GaussianBlur(d * d, (0, 0), 10)) + 4), (0, 0), 1.5)


def rectify_game(game_img, cam, marker, m_per_px, size):
    """Undo the camera pitch: top-down view of the game ground at our map's scale, anchored at marker.

    size is (w, h) of our map image. Returns (image, validity mask). The image is still in the game's own
    heading, so it equals our north-up map rotated by the unknown heading about the marker.
    """
    w, h = size
    cam0 = ground_to_game(cam, 0.0) @ ref_to_ground(marker, m_per_px)
    img = cv2.warpPerspective(game_img, cam0, (w, h), flags=cv2.WARP_INVERSE_MAP | cv2.INTER_LINEAR)
    band = np.zeros(game_img.shape[:2], dtype=np.uint8)
    band[300:1500] = 255
    valid = cv2.warpPerspective(band, cam0, (w, h), flags=cv2.WARP_INVERSE_MAP | cv2.INTER_NEAREST)
    return img, valid


def register_unpitched(ref_img, game_img, cam, marker, m_per_px, step=1.0):
    """Heading search on the pitch-rectified game view. Returns (ground->game 3x3, score).

    The camera terms cam are calibrated once per device (the game camera is fixed); only the heading is
    unknown, and the player stands on the map marker, so there is no translation to search. The score is
    the normalised cross-correlation of local-contrast features at the best heading.
    """
    h, w = ref_img.shape[:2]
    rect, valid = rectify_game(game_img, cam, marker, m_per_px, (w, h))
    fr, fm = _feat(ref_img), valid.astype(np.float32) / 255.0
    ft = _feat(rect)
    yy, xx = np.mgrid[0:h, 0:w]
    disc = ((xx - marker[0]) ** 2 + (yy - marker[1]) ** 2 < (300.0) ** 2).astype(np.float32)

    def score(deg):
        m = cv2.getRotationMatrix2D(tuple(marker), deg, 1.0)
        rr = cv2.warpAffine(fr, m, (w, h))
        mm = fm * disc
        return _ncc(rr * mm, ft * mm)

    cands = np.arange(-180.0, 180.0, 4.0)
    coarse = [score(c) for c in cands]
    best = float(cands[int(np.argmax(coarse))])
    fine = np.arange(best - 4.0, best + 4.0 + step, step)
    sc = [score(c) for c in fine]
    deg = float(fine[int(np.argmax(sc))])
    return ground_to_game(cam, deg), float(max(sc))


def content_flow(game_a, game_b, y0=500, y1=1400):
    """Screen shift (fx, fy) in px of game_b's ground content relative to game_a, plus phase-correlation response.

    No map matching: phase correlation of local-contrast features on the ground band (below the HUD,
    above the player), Hanning-windowed.
    """
    fa, fb = (_feat((g if g.ndim == 2 else _to_gray(g))[y0:y1]) for g in (game_a, game_b))
    win = cv2.createHanningWindow((fa.shape[1], fa.shape[0]), cv2.CV_32F)
    (fx, fy), resp = cv2.phaseCorrelate(fa, fb, win)
    return float(fx), float(fy), float(resp)


def heading_from_moves(d1, f1, d2, f2, min_move_m=3.0, min_flow_px=5.0, max_skew_deg=10.0):
    """Heading and scales from two known moves d (east, north metres) and the content flow f (px) each caused.

    With D = [d1 d2] and F = [f1 f2], the ground->screen linear map is A = -F inv(D); its rows are
    s*(cos t, -sin t) and -sy*(sin t, cos t), so no camera calibration is needed. Returns
    {theta_deg, s, sy, skew_deg} or None (caller falls back to north-up) when the moves are too short,
    nearly parallel, the flow too small, or the two rows' headings disagree by more than max_skew_deg.
    """
    d1, d2, f1, f2 = (np.asarray(v, dtype=np.float64) for v in (d1, d2, f1, f2))
    n1, n2 = np.linalg.norm(d1), np.linalg.norm(d2)
    if min(n1, n2) < min_move_m or min(np.linalg.norm(f1), np.linalg.norm(f2)) < min_flow_px:
        return None
    if abs(d1[0] * d2[1] - d1[1] * d2[0]) < 0.5 * n1 * n2:
        return None
    a = -np.column_stack([f1, f2]) @ np.linalg.inv(np.column_stack([d1, d2]))
    est = _rows_theta_skew(a)
    return est if est["skew_deg"] <= max_skew_deg else None


def _rows_theta_skew(a):
    """{theta_deg, s, sy, skew_deg} from a 2x2 ground->screen map: rows are s*(cos t, -sin t), -sy*(sin t, cos t)."""
    t1, t2 = math.atan2(-a[0, 1], a[0, 0]), math.atan2(-a[1, 0], -a[1, 1])
    skew = abs((math.degrees(t1 - t2) + 180.0) % 360.0 - 180.0)
    t = math.degrees(math.atan2(math.sin(t1) + math.sin(t2), math.cos(t1) + math.cos(t2)))
    return {"theta_deg": t, "s": float(np.hypot(*a[0])), "sy": float(np.hypot(*a[1])), "skew_deg": skew}


def fit_ground_map(moves, min_moves=12, min_move_m=3.0, min_flow_px=5.0, max_skew_deg=10.0):
    """One least-squares ground->screen 2x2 map from many (d, f) moves (d east/north metres, f content flow px).

    A = -F D^T (D D^T)^-1 over every move, then moves whose residual |f + A d| exceeds max(3 MAD, 1.5 px)
    are dropped and the map refitted once. Returns {A, theta_deg, s, sy, skew_deg, n_used, n_total, rms_px}
    or None (too few moves, bearings not spread, or the two rows disagree): never a north-up fallback.
    """
    use = [(np.asarray(d, float), np.asarray(f, float)) for d, f in moves
           if np.linalg.norm(d) >= min_move_m and np.linalg.norm(f) >= min_flow_px]

    def solve(sel):
        dm, fm = (np.array([m[i] for m in sel]).T for i in (0, 1))
        sv = np.linalg.svd(dm / math.sqrt(len(sel)), compute_uv=False)
        if sv[1] < 0.5 * sv[0]:
            return None
        return -fm @ dm.T @ np.linalg.inv(dm @ dm.T), dm, fm

    fit = solve(use) if len(use) >= min_moves else None
    if fit is None:
        return None
    a, dm, fm = fit
    res = np.linalg.norm(fm + a @ dm, axis=0)
    keep = res <= max(3 * float(np.median(np.abs(res - np.median(res)))), 1.5)
    use = [m for m, k in zip(use, keep) if k]
    fit = solve(use) if len(use) >= min_moves else None
    if fit is None:
        return None
    a, dm, fm = fit
    est = _rows_theta_skew(a)
    if est["skew_deg"] > max_skew_deg:
        return None
    rms = float(np.sqrt(np.mean(np.sum((fm + a @ dm) ** 2, axis=0))))
    return {**est, "A": a.tolist(), "n_used": len(use), "n_total": len(moves), "rms_px": rms}


def tap_to_ground(a, p, anchor):
    """Ground offset g (east, north metres) the player must move so tapped screen pixel p reaches the anchor.

    A ground point at offset g appears at screen offset A g, so g = inv(A) (p - anchor). No heading needed.
    """
    return np.linalg.solve(np.asarray(a, float), np.asarray(p, float) - np.asarray(anchor, float))


def patch_offset(before, after, p, anchor, half=100, search=150, scales=(1.0, 1.15, 1.3), sprite=70, sprite_dy=-40):
    """Where the game drew the content that was under pixel p in `before`, found in `after`.

    Returns ((dx, dy) of the match centre minus anchor in px, normalised cross-correlation). The player
    sprite (a disc of radius `sprite` centred `sprite_dy` px above the anchor, the feet) is excluded from both frames by a masked NCC,
    so a match that lands under the sprite still scores on the pixels around it. The (2*half)^2 template
    is tried at several scales inside +-search px of the anchor; at least half of it must be unmasked.
    """
    def gray(img):
        return ((img if img.ndim == 2 else _to_gray(img)).astype(np.float32) - 128.0) / 128.0  # float32 sums

    b, a = gray(before), gray(after)
    ax, ay = int(anchor[0]), int(anchor[1])
    mask = np.ones(b.shape, np.float32)
    cv2.circle(mask, (ax, ay + sprite_dy), sprite, 0, -1)
    px, py = int(p[0]), int(p[1])
    sl = (slice(max(py - half, 0), py + half), slice(max(px - half, 0), px + half))
    tpl, tm = b[sl], mask[sl]
    h, w = a.shape
    corr = lambda x, k: cv2.matchTemplate(x, k, cv2.TM_CCORR)
    best = ((0.0, 0.0), -1.0)
    for sc in scales:
        t = cv2.resize(tpl, None, fx=sc, fy=sc, interpolation=cv2.INTER_LINEAR)
        mt = cv2.resize(tm, None, fx=sc, fy=sc, interpolation=cv2.INTER_NEAREST)
        th, tw = t.shape
        x0, y0 = max(ax - search - tw // 2, 0), max(ay - search - th // 2, 0)
        x1, y1 = min(ax + search + tw // 2, w), min(ay + search + th // 2, h)
        win, wm = a[y0:y1, x0:x1], mask[y0:y1, x0:x1]
        if win.shape[0] < th or win.shape[1] < tw:
            continue
        n = corr(wm, mt)
        sw, sw2, sww = corr(win * wm, mt), corr(win * win * wm, mt), corr(win * wm, t * mt)
        st, st2 = corr(wm, t * mt), corr(wm, t * t * mt)
        n = np.maximum(n, 1e-6)
        num = sww - sw * st / n
        den = np.sqrt(np.maximum(sw2 - sw * sw / n, 1e-6) * np.maximum(st2 - st * st / n, 1e-6))
        r = np.where(n >= 0.5 * th * tw, num / den, -1.0)
        _, mx, _, loc = cv2.minMaxLoc(r)
        if mx > best[1]:
            best = ((x0 + loc[0] + tw / 2 - ax, y0 + loc[1] + th / 2 - ay), float(mx))
    return best


# Mirrors CompassTrackingConstants (core/common AppConstants.kt) and CompassAccessibilityService.detectNorthAngle.
NEEDLE_X_MIN_PCT, NEEDLE_Y_MAX_PCT = 0.55, 0.35
NEEDLE_MIN_FRAC, NEEDLE_MAX_FRAC, NEEDLE_MIN_PX = 0.008, 0.06, 20


def needle_heading(img_bgr):
    """Python port of the shipped needle detector: None or {bbox, centroid, angle_deg, px} (window coords are full-frame).

    angle_deg is clockwise from screen-up of the tapered tip, full -180..180 (principal axis + third moment).
    """
    h, w = img_bgr.shape[:2]
    x0, sh = int(NEEDLE_X_MIN_PCT * w), int(NEEDLE_Y_MAX_PCT * h)
    if w - x0 <= 0 or sh <= 0:
        return None
    hsv = cv2.cvtColor(img_bgr[:sh, x0:], cv2.COLOR_BGR2HSV)  # H 0..179 (= degrees / 2)
    red = (((hsv[..., 0] < 7.5) | (hsv[..., 0] > 172.5)) & (hsv[..., 1] > 127.5) & (hsv[..., 2] > 76.5))
    short = min(w, h)
    min_dim, max_dim = max(int(NEEDLE_MIN_FRAC * short), 4), int(NEEDLE_MAX_FRAC * short)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(red.astype(np.uint8), connectivity=4)
    best = None
    for i in range(1, n):
        bx, by, bw, bh, cnt = stats[i]
        if min_dim <= bw <= max_dim and min_dim <= bh <= max_dim and cnt >= NEEDLE_MIN_PX:
            if best is None or cnt > best[4]:
                best = (bx, by, bw, bh, cnt, i)
    if best is None:
        return None
    bx, by, bw, bh, cnt, i = best
    ys, xs = np.nonzero(labels == i)
    cx, cy = xs.mean(), ys.mean()
    theta = 0.5 * math.atan2(2 * ((xs - cx) * (ys - cy)).mean(), ((xs - cx) ** 2).mean() - ((ys - cy) ** 2).mean())
    ax, ay = math.cos(theta), math.sin(theta)
    if (((xs - cx) * ax + (ys - cy) * ay) ** 3).sum() < 0:
        ax, ay = -ax, -ay
    return {"bbox": [int(bx + x0), int(by), int(bw), int(bh)], "centroid": [float(cx + x0), float(cy)],
            "angle_deg": math.degrees(math.atan2(ax, -ay)), "px": int(cnt)}


def _two_finger_events(finger_xy, steps):
    """Ordered (type, code, value) touch events (protocol B) for two fingers at finger_xy(k, sign), then released."""
    EV_ABS, EV_SYN = 3, 0
    SLOT, TID, X, Y, SYN = 0x2F, 0x39, 0x35, 0x36, 0
    ev = []
    for k in range(steps):
        for slot, sign in ((0, 1), (1, -1)):
            ev.append((EV_ABS, SLOT, slot))
            if k == 0:
                ev.append((EV_ABS, TID, 100 + slot))
            x, y = finger_xy(k, sign)
            ev.append((EV_ABS, X, int(round(x))))
            ev.append((EV_ABS, Y, int(round(y))))
        ev.append((EV_SYN, SYN, 0))
    for slot in (0, 1):
        ev += [(EV_ABS, SLOT, slot), (EV_ABS, TID, -1)]
    ev.append((EV_SYN, SYN, 0))
    return ev


def twist_events(cx, cy, radius, start_deg, delta_deg, steps=24):
    """Two fingers twisting about (cx, cy) by delta_deg, then released."""
    def xy(k, sign):
        a = math.radians(start_deg + delta_deg * k / max(steps - 1, 1))
        return cx + sign * radius * math.cos(a), cy + sign * radius * math.sin(a)
    return _two_finger_events(xy, steps)


def pinch_events(cx, cy, r0, r1, steps=24):
    """Two fingers on a horizontal line through (cx, cy), spreading from r0 to r1 px each side (r1 > r0 zooms in)."""
    def xy(k, sign):
        return cx + sign * (r0 + (r1 - r0) * k / max(steps - 1, 1)), cy
    return _two_finger_events(xy, steps)


def commanded_target(lat, lon, bearing_deg, dist_m):
    """(lat, lon) dist_m metres from the start along a compass bearing (flat earth, 111320 m/deg)."""
    b = math.radians(bearing_deg)
    return (lat + dist_m * math.cos(b) / 111320,
            lon + dist_m * math.sin(b) / (111320 * math.cos(math.radians(lat))))


def along_track_move(p0, p1, bearing_deg, lat0):
    """(east, north) metres of the move p0 -> p1 (lat, lon) projected onto the commanded bearing.

    The direction is the bearing the Control API /walk was given, known by construction; only the
    length comes from the polled positions, so perpendicular position jitter drops out.
    """
    east = (p1[1] - p0[1]) * 111320 * math.cos(math.radians(lat0))
    north = (p1[0] - p0[0]) * 111320
    b = math.radians(bearing_deg)
    along = east * math.sin(b) + north * math.cos(b)
    return (along * math.sin(b), along * math.cos(b))


def motion_g_est(est, cam):
    """Ground->game homography from a heading_from_moves estimate; px/py/a are the fixed camera terms."""
    return ground_to_game({**cam, "s": est["s"], "sy": est["sy"]}, est["theta_deg"])


def cmd_motion(args):
    """Score motion-calibrated heading on the stored walk frames (north walk x east walk pairs).

    Each frame pair (i, i+K) is one move; one north-walk move and one east-walk move give an estimate.
    Rows feed cmd_report; landing error is against truth.json's joint-fit heading with the same camera
    terms. Truth comes from these same walks, so a pass is not independent proof.
    """
    import json
    import time

    if args.commanded:
        return cmd_motion_commanded(args)
    data = Path(args.data)
    tj = json.loads((data / "truth.json").read_text())
    cam, lat0 = tj["cam"], tj["ref_pos"][0]
    g_true = ground_to_game(cam, tj["theta_deg"])
    moves = {}
    for walk in tj["walks"]:
        meta = json.loads((data / walk / "meta.json").read_text())
        pos = [(((m["mock"][1] + m["mock_after"][1]) / 2) * 111320 * math.cos(math.radians(lat0)),
                ((m["mock"][0] + m["mock_after"][0]) / 2) * 111320) for m in meta]
        imgs = [cv2.imread(str(data / walk / f"w{m['i']:02d}.png")) for m in meta]
        moves[walk] = {}
        for k in args.ks:
            moves[walk][k] = []
            for i in range(len(meta) - k):
                d = (pos[i + k][0] - pos[i][0], pos[i + k][1] - pos[i][1])
                fx, fy, _ = content_flow(imgs[i], imgs[i + k])
                moves[walk][k].append((i, d, (fx, fy)))
    rows = []
    for k in args.ks:
        for a in moves["walk_L1_n"][k]:
            for b in moves["walk_L1_e"][k]:
                t0 = time.perf_counter()
                est = heading_from_moves(a[1], a[2], b[1], b[2])
                row = {"pair": f"n{a[0]:02d}/e{b[0]:02d}", "kind": f"motion_k{k}", "method": "two_move",
                       "score": est["skew_deg"] if est else 0.0, "ms": (time.perf_counter() - t0) * 1000,
                       "rejected": est is None}
                if est:
                    row["landing_error_m"] = tap_landing_error_m(motion_g_est(est, cam), g_true, TAP_PROBES)
                    row["theta_deg"] = est["theta_deg"]
                rows.append(row)
    (data / "results_motion.json").write_text(json.dumps(rows, indent=1))
    print(f"{len(rows)} rows -> {data / 'results_motion.json'}")


def _commanded_legs(data, moving_min=0.3):
    """{bearing: [{ll, img, moving}]} from walk_C_* dirs. /state speedMs stays 0 during a walk-to, so a
    frame counts as moving when its neighbours' polled positions imply at least moving_min m/s."""
    import json

    legs = {}
    for d in sorted(data.glob("walk_C_*")):
        meta = json.loads((d / "meta.json").read_text())
        ll = [((m["pos"][0] + m["pos_after"][0]) / 2, (m["pos"][1] + m["pos_after"][1]) / 2) for m in meta]
        fr = []
        for i, m in enumerate(meta):
            a, b = max(i - 1, 0), min(i + 1, len(meta) - 1)
            dn = (ll[b][0] - ll[a][0]) * 111320
            de = (ll[b][1] - ll[a][1]) * 111320 * math.cos(math.radians(ll[0][0]))
            speed = math.hypot(dn, de) / max(meta[b]["t"] - meta[a]["t"], 1e-6)
            fr.append({"ll": ll[i], "img": cv2.imread(str(d / f"w{m['i']:02d}.png")), "moving": speed >= moving_min})
        legs[meta[0]["bearing_cmd"]] = fr
    return legs


def cmd_motion_commanded(args):
    """Motion-calibrated heading scored with the commanded /walk bearing as each move's direction.

    Legs N (0), E (90) and a held-out NE (45) from walkcap. Move vector = along-track length times the
    commanded direction. Rows: N x E (kind motion_c_k<K>), N x NE and E x NE (kind motion_c_k<K>_ne), plus
    held-out offset rows: the ground->screen map fitted on N+E moves predicts the NE flow direction; the
    row's theta_deg is the angle of observed minus predicted flow (deg). Landing error is against the
    circular mean heading of the accepted N x E estimates (no independent heading exists).
    """
    import json
    import time

    data = Path(args.data)
    tj = json.loads((data / "truth.json").read_text())
    cam = tj["cam"]
    legs = _commanded_legs(data)
    lat0 = next(iter(legs.values()))[0]["ll"][0]
    mv = {}
    for b, fr in legs.items():
        for k in args.ks:
            for i in range(len(fr) - k):
                if args.moving_filter and not (fr[i]["moving"] and fr[i + k]["moving"]):
                    continue
                fx, fy, resp = content_flow(fr[i]["img"], fr[i + k]["img"])
                if resp < args.min_resp:
                    continue
                mv.setdefault((b, k), []).append(
                    (i, along_track_move(fr[i]["ll"], fr[i + k]["ll"], b, lat0), (fx, fy)))
    rows, ests = [], []

    def pair_rows(kind, ba, bb, k):
        for a in mv.get((ba, k), []):
            for b in mv.get((bb, k), []):
                t0 = time.perf_counter()
                est = heading_from_moves(a[1], a[2], b[1], b[2])
                row = {"pair": f"{ba:.0f}_{a[0]:02d}/{bb:.0f}_{b[0]:02d}", "kind": kind, "method": "two_move",
                       "score": est["skew_deg"] if est else 0.0, "ms": (time.perf_counter() - t0) * 1000,
                       "rejected": est is None}
                if est:
                    row["theta_deg"] = est["theta_deg"]
                    row["_est"] = est
                rows.append(row)

    for k in args.ks:
        pair_rows(f"motion_c_k{k}", 0.0, 90.0, k)
        pair_rows(f"motion_c_k{k}_ne", 0.0, 45.0, k)
        pair_rows(f"motion_c_k{k}_ne", 90.0, 45.0, k)
    base = [r for r in rows if "_est" in r and not r["kind"].endswith("_ne")]
    if base:
        th = [math.radians(r["theta_deg"]) for r in base]
        theta_ref = math.degrees(math.atan2(sum(map(math.sin, th)), sum(map(math.cos, th))))
        cam_ref = {**cam, "s": float(np.median([r["_est"]["s"] for r in base])),
                   "sy": float(np.median([r["_est"]["sy"] for r in base]))}
        g_true = ground_to_game(cam_ref, theta_ref)
        print(f"reference heading {theta_ref:.1f} deg from {len(base)} N x E estimates")
    for r in rows:
        est = r.pop("_est", None)
        if est and base:
            r["landing_error_m"] = tap_landing_error_m(motion_g_est(est, cam), g_true, TAP_PROBES)
    for k in args.ks:
        fit = [(m[1], m[2]) for b in (0.0, 90.0) for m in mv.get((b, k), [])]
        if len(fit) < 2:
            continue
        dm = np.array([f[0] for f in fit]).T
        fm = np.array([f[1] for f in fit]).T
        a = -fm @ dm.T @ np.linalg.inv(dm @ dm.T)
        for m in mv.get((45.0, k), []):
            if np.hypot(*m[1]) < 3.0 or np.hypot(*m[2]) < 5.0:
                continue
            pred, obs = -a @ np.array(m[1]), np.array(m[2])
            off = (math.degrees(math.atan2(obs[1], obs[0]) - math.atan2(pred[1], pred[0])) + 180) % 360 - 180
            rows.append({"pair": f"45_{m[0]:02d}", "kind": f"motion_c_k{k}_heldout", "method": "offset",
                         "score": 0.0, "ms": 0.0, "rejected": False, "theta_deg": off})
    (data / "results_motion.json").write_text(json.dumps(rows, indent=1))
    print(f"{len(rows)} rows -> {data / 'results_motion.json'}")
    _annotate_commanded(data, legs, mv, cam, args.ks[0])
    for kind in sorted({r["kind"] for r in rows}):
        th = [r["theta_deg"] for r in rows if r["kind"] == kind and "theta_deg" in r]
        n = sum(r["kind"] == kind for r in rows)
        if th:
            print(f"{kind}: n={n} accepted={len(th)} mean={np.mean(th):.1f} sd={np.std(th):.1f} deg")


def _annotate_commanded(data, legs, mv, cam, k, every=4):
    """Write annotated/<pair>.png: end frame of the N move with the estimated screen-north arrow."""
    out = data / "annotated"
    out.mkdir(exist_ok=True)
    px, py = int(cam["px"]), int(cam["py"])
    for a, b in [(a, b) for a in mv.get((0.0, k), []) for b in mv.get((90.0, k), [])][::every]:
        est = heading_from_moves(a[1], a[2], b[1], b[2])
        if not est:
            continue
        t = math.radians(est["theta_deg"])
        v = np.array([-est["s"] * math.sin(t), -est["sy"] * math.cos(t)])
        v = v / np.linalg.norm(v) * 300
        img = legs[0.0][a[0] + k]["img"].copy()
        cv2.arrowedLine(img, (px, py), (int(px + v[0]), int(py + v[1])), (0, 0, 255), 8, tipLength=0.15)
        cv2.putText(img, f"north {est['theta_deg']:.1f} deg", (40, 120), cv2.FONT_HERSHEY_SIMPLEX, 3, (0, 0, 255), 6)
        cv2.imwrite(str(out / f"n{a[0]:02d}_e{b[0]:02d}_k{k}.png"), img)


def cmd_real(args):
    """Real game-vs-own-map rows scored against walk-derived truth (landing error in metres on the ground).

    truth.json in the data dir: {cam, theta_deg, ref, ref_pos, marker, mpp, walks}. The truth heading comes
    from game frames recorded while the mock walked a known path (not from the images under test). Each
    walk frame is registered against our map shifted by the walked offset, and the map is also rotated by
    0/90/180/270 degrees about the marker (a map rendered at another heading; truth turns with it).
    """
    import json
    import time

    data = Path(args.data)
    tj = json.loads((data / "truth.json").read_text())
    cam, mpp, marker0 = tj["cam"], tj["mpp"], tj["marker"]
    ref_bgr = cv2.imread(str(data / tj["ref"]))
    lat0, lon0 = tj["ref_pos"]
    h, w = ref_bgr.shape[:2]
    rows = []
    for walk in tj["walks"]:
        meta = json.loads((data / walk / "meta.json").read_text())
        for fm in meta[:: args.every]:
            la = (fm["mock"][0] + fm["mock_after"][0]) / 2
            lo = (fm["mock"][1] + fm["mock_after"][1]) / 2
            east = (lo - lon0) * 111320 * math.cos(math.radians(lat0))
            north = (la - lat0) * 111320
            game = cv2.imread(str(data / walk / f"w{fm['i']:02d}.png"))
            for psi in args.rotations:
                rm = cv2.getRotationMatrix2D(tuple(marker0), psi, 1.0)
                ref = cv2.warpAffine(ref_bgr, rm, (w, h))
                mk0 = (marker0[0] + east / mpp, marker0[1] - north / mpp)
                mk = tuple(rm @ np.array([mk0[0], mk0[1], 1.0]))
                g_true = ground_to_game(cam, tj["theta_deg"] - psi)
                ref_g, game_g = _to_gray(ref), _to_gray(game)
                m_inv = np.linalg.inv(ref_to_ground(mk, mpp))

                def raw(est):
                    if est is None:
                        return None
                    est = np.vstack([est, [0, 0, 1]]) if np.shape(est) == (2, 3) else np.asarray(est)
                    return est @ m_inv

                runs = [
                    ("unpitched_gray", lambda: register_unpitched(ref_g, game_g, cam, mk, mpp)),
                    ("unpitched_green", lambda: register_unpitched(ref, game, cam, mk, mpp)),
                ]
                for name, fn in _methods():
                    runs.append((name + "_raw", lambda fn=fn: (lambda m, sc, _: (raw(m), sc))(*fn(ref_g, game_g))))
                for name, fn in runs:
                    t0 = time.perf_counter()
                    g_est, score = fn()
                    ms = (time.perf_counter() - t0) * 1000
                    row = {"pair": f"{walk}/{fm['i']:02d}", "kind": f"real_rot{int(psi) % 360}", "method": name,
                           "score": float(score), "ms": ms, "rejected": g_est is None}
                    if g_est is not None:
                        row["landing_error_m"] = tap_landing_error_m(g_est, g_true, TAP_PROBES)
                    rows.append(row)
                print(f"{walk} {fm['i']:02d} rot {psi}", flush=True)
    (data / "results_real.json").write_text(json.dumps(rows, indent=1))


def _adb_screencap(serial, out_path):
    import subprocess

    data = subprocess.run(
        ["adb", "-s", serial, "exec-out", "screencap", "-p"], check=True, capture_output=True
    ).stdout
    Path(out_path).write_bytes(data)


def _adb_swipe(serial, x1, y1, x2, y2, ms=400):
    import subprocess

    subprocess.run(
        ["adb", "-s", serial, "shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms)],
        check=True,
    )


GAME_ACTIVITY = "com.nianticlabs.pokemongo/com.nianticproject.holoholo.libholoholo.unity.UnityMainActivity"
APP_ACTIVITY = "com.locationjoystick.app/.MainActivity"


def _focus(serial, activity):
    import subprocess

    subprocess.run(["adb", "-s", serial, "shell", "am", "start", "-n", activity], check=True,
                   capture_output=True)


def cmd_capture(args):
    """Capture one game + own-map screenshot per pan step, writing pair dirs under args.out.

    Each app is brought to the foreground before its pan and screencap. The same pan vector is
    applied to both views; the pairs are not geometrically matched, only varied by panning.
    """
    import json
    import time

    out = Path(args.out)
    for i in range(args.pairs):
        pair = out / f"pair_{i:02d}"
        pair.mkdir(parents=True, exist_ok=True)
        for serial, activity, name in [(args.game_serial, GAME_ACTIVITY, "game.png"),
                                       (args.app_serial, APP_ACTIVITY, "reference.png")]:
            _focus(serial, activity)
            time.sleep(2)
            if i:
                _adb_swipe(serial, 540, 1500, 540 + args.dx, 1500 + args.dy)
                time.sleep(1.5)
            _adb_screencap(serial, pair / name)
        (pair / "meta.json").write_text(
            json.dumps({"index": i, "device": args.game_serial,
                        "pan_step": [args.dx, args.dy], "captured_at": time.time()}, indent=2)
        )
        print(f"captured {pair}")


def _uinput_script(events, width=1080, height=2340, frame_ms=20):
    """`uinput` JSON stream (register a virtual multitouch screen, inject each event frame, delay) for events."""
    import json

    def info(code, top):
        return {"code": code, "info": {"value": 0, "minimum": 0, "maximum": top, "fuzz": 0, "flat": 0,
                                       "resolution": 0}}

    cmds = [{"id": 1, "command": "register", "name": "lj-probe-touch", "vid": 0x18D1, "pid": 0x4E11, "bus": "usb",
             "configuration": [{"type": 100, "data": [1, 3]}, {"type": 101, "data": [0x14A]},
                               {"type": 103, "data": [0x2F, 0x35, 0x36, 0x39]}, {"type": 110, "data": [1]}],
             "abs_info": [info(0x2F, 9), info(0x35, width - 1), info(0x36, height - 1), info(0x39, 65535)]},
            {"id": 1, "command": "delay", "duration": 500}]
    frame = [1, 0x14A, 1]  # BTN_TOUCH down with the first frame
    for t, c, v in events:
        frame += [t, c, v]
        if (t, c) == (0, 0):
            cmds += [{"id": 1, "command": "inject", "events": frame}, {"id": 1, "command": "delay", "duration": frame_ms}]
            frame = []
    cmds.append({"id": 1, "command": "inject", "events": [1, 0x14A, 0, 0, 0, 0]})
    cmds.append({"id": 1, "command": "delay", "duration": 300})
    return "\n".join(json.dumps(c) for c in cmds)


def _twist(serial, events):
    """Drive a two-finger gesture with `uinput` (a virtual touchscreen, external to the game; `sendevent` is denied)."""
    import subprocess

    subprocess.run(["adb", "-s", serial, "shell", "uinput -"], input=_uinput_script(events).encode(), check=True)


def _annotate_needle(img, found, label):
    out = img.copy()
    if found:
        x, y, w, h = found["bbox"]
        cv2.rectangle(out, (x, y), (x + w, y + h), (0, 255, 0), 3)
        a = math.radians(found["angle_deg"])
        c = (int(found["centroid"][0]), int(found["centroid"][1]))
        cv2.arrowedLine(out, c, (int(c[0] + 120 * math.sin(a)), int(c[1] - 120 * math.cos(a))), (0, 255, 255), 4)
    cv2.putText(out, label, (40, 300), cv2.FONT_HERSHEY_SIMPLEX, 1.4, (0, 255, 0), 4)
    return out


def _wrap(d):
    return (d + 180.0) % 360.0 - 180.0


def cmd_probe(args):
    """Probe the game's needle as an orientation cue: baseline, then per delta reset-tap, twist, reset-tap.

    Stationary; nothing is read from or injected into the game. Raw and annotated frames plus probe.json
    land in args.out (document the numbers, the user validates the annotated PNGs by eye).
    """
    import json
    import subprocess
    import time

    s, out = args.serial, Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    orig = subprocess.run(["adb", "-s", s, "shell", "settings", "get", "system", "pointer_location"],
                          capture_output=True, text=True).stdout.strip() or "0"
    rows, last = [], {}

    def grab(step):
        raw = out / f"{len(rows):02d}_{step}.png"
        _adb_screencap(s, raw)
        img = cv2.imread(str(raw))
        last.clear()  # a tap must only ever use the latest frame's blob, never a stale one
        found = needle_heading(img)
        ang = None if found is None else round(found["angle_deg"], 1)
        cv2.imwrite(str(raw).replace(".png", "_annotated.png"), _annotate_needle(img, found, f"{step}: {ang}"))
        rows.append({"step": step, "found": found is not None, "angle_deg": ang})
        if found:
            last.update(found)
        print(f"{step}: {ang}", flush=True)

    def tap_needle(step):
        if not last:
            print(f"{step}: needle not found, tap skipped", flush=True)
            return
        subprocess.run(["adb", "-s", s, "shell", "input", "tap", str(int(last["centroid"][0])),
                        str(int(last["centroid"][1]))], check=True)
        time.sleep(1.0)

    try:
        _focus(s, GAME_ACTIVITY)
        time.sleep(2)
        grab("baseline_pointer_overlay_on")
        subprocess.run(["adb", "-s", s, "shell", "settings", "put", "system", "pointer_location", "0"], check=True)
        time.sleep(0.5)
        for d in args.deltas:
            last.clear()
            grab(f"pre_{d:+g}")
            tap_needle(f"reset_{d:+g}")
            grab(f"R0_{d:+g}")
            _twist(s, twist_events(540, 1500, 250, 0.0, d))
            time.sleep(1.0)
            grab(f"T_{d:+g}")
            tap_needle(f"reset_after_{d:+g}")
            grab(f"R1_{d:+g}")
    finally:
        subprocess.run(["adb", "-s", s, "shell", "settings", "put", "system", "pointer_location", orig],
                       check=False)
    by = {r["step"]: r["angle_deg"] for r in rows}
    summary = {"rows": rows, "found": sum(r["found"] for r in rows), "frames": len(rows), "deltas": {}}
    for d in args.deltas:
        r0, t, r1 = by.get(f"R0_{d:+g}"), by.get(f"T_{d:+g}"), by.get(f"R1_{d:+g}")
        summary["deltas"][f"{d:+g}"] = {
            "twist_vs_R0_deg": None if None in (r0, t) else round(_wrap(t - r0), 1),
            "R1_deg": r1}
    (out / "probe.json").write_text(json.dumps(summary, indent=1))
    print(json.dumps(summary["deltas"]))


def frame_meta(i, t, bearing_cmd, st0, st1):
    """One walkcap meta row from the /state snapshots read before and after the screencap (no secrets)."""
    p0, p1 = st0["position"], st1["position"]
    return {"i": i, "t": t, "bearing_cmd": bearing_cmd, "pos": [p0["lat"], p0["lon"]],
            "pos_after": [p1["lat"], p1["lon"]], "speed_ms": st0["speedMs"], "speed_ms_after": st1["speedMs"],
            "state_bearing": st1.get("bearing")}


def _api(host, key, method, path, body=None):
    import json
    import urllib.request

    req = urllib.request.Request(
        f"http://{host}/api/v1{path}", method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as r:
        return json.loads(r.read() or b"{}")


def _preflight():
    """(host, key) from LJ_CONTROL_API_HOST / LJ_CONTROL_API_KEY after checking the app is ready for /walk."""
    import os

    key, host = os.environ.get("LJ_CONTROL_API_KEY"), os.environ.get("LJ_CONTROL_API_HOST")
    if not key or not host:
        raise SystemExit("set LJ_CONTROL_API_KEY and LJ_CONTROL_API_HOST (host:port)")
    st = _api(host, key, "GET", "/state")
    if (st["spoofState"] != "RUNNING" or st["walk"]["target"] or st["speedProfileId"] != "walk"
            or st["mode"] in ("ROUTE_REPLAY", "ROAMING", "FOLLOWER") or not st["position"]):
        raise SystemExit("preflight: need RUNNING, no walk target, walk profile, not replay/roam/follower")
    return host, key


def _capture_leg(host, key, serial, leg, b, lat, lon, every, timeout, extra=None):
    """Walk to (lat, lon) via /walk and write w<NN>.png + meta.json (bearing b) into leg until the walk ends."""
    import json
    import time

    leg.mkdir(parents=True, exist_ok=True)
    _api(host, key, "POST", "/walk", {"lat": lat, "lon": lon, "viaRoads": False})
    rows, t0, seen = [], time.time(), False
    while time.time() - t0 < timeout:
        tick = time.time()
        s0 = _api(host, key, "GET", "/state")
        _adb_screencap(serial, leg / f"w{len(rows):02d}.png")
        s1 = _api(host, key, "GET", "/state")
        rows.append({**frame_meta(len(rows), tick - t0, b, s0, s1), **(extra or {})})
        if len(rows) > 1:  # /state speedMs reads 0 during a walk-to, so derive speed from positions
            q, c = rows[-2]["pos"], rows[-1]["pos"]
            dn, de = (c[0] - q[0]) * 111320, (c[1] - q[1]) * 111320 * math.cos(math.radians(c[0]))
            if math.hypot(dn, de) / max(rows[-1]["t"] - rows[-2]["t"], 1e-6) > 3.0:
                _api(host, key, "POST", "/walk/stop")
                raise SystemExit("speed above 3 m/s, aborting")
        seen = seen or s1["walk"]["target"] is not None
        if seen and s1["walk"]["target"] is None:
            break
        time.sleep(max(0.0, every - (time.time() - tick)))
    (leg / "meta.json").write_text(json.dumps(rows, indent=1))
    return len(rows)


def cmd_walkcap(args):
    """Record straight walk legs commanded through the Control API /walk, one frame every args.every s.

    The bearing sent to /walk is the ground truth of each leg's direction (known by construction).
    Legs chain from where the previous one ended; /teleport is never called. Credentials come from
    LJ_CONTROL_API_KEY and LJ_CONTROL_API_HOST (host:port) and are never written anywhere.
    """
    import time

    host, key = _preflight()
    out = Path(args.out)
    _focus(args.serial, GAME_ACTIVITY)
    time.sleep(2)
    try:
        for b in args.legs:
            st = _api(host, key, "GET", "/state")
            lat, lon = commanded_target(st["position"]["lat"], st["position"]["lon"], b, args.dist)
            n = _capture_leg(host, key, args.serial, out / f"walk_C_{int(b):03d}", b, lat, lon, args.every,
                             args.timeout)
            print(f"leg {b}: {n} frames")
    finally:
        _api(host, key, "POST", "/walk/stop")


def parse_condition(text):
    """'zoom:twist' -> (zoom_factor, twist_deg); zoom > 1 pinches out (zoom in), 1 leaves it, twist 0 leaves it."""
    z, t = text.split(":")
    return float(z), float(t)


def condition_name(zoom, twist):
    return f"z{zoom:g}_t{twist:+g}"


# Finger half-spread (px) of a pinch at ratio 1.0; ratios above 1 widen it, below 1 narrow it.
PINCH_BASE_PX = 200
GESTURE_CENTER = (540, 1500)  # map area below the top bar, clear of the buttons along the bottom


def cmd_varycap(args):
    """Automated diverse capture: per condition, vary camera zoom and orientation, then walk legs and record.

    No hand steps and no teleport. A condition is 'zoom:twist': a two-finger pinch of the given ratio and a
    two-finger twist of the given degrees (uinput virtual touchscreen, external to the game), then one walk
    leg per bearing in args.legs. The game offers no camera reset, so gestures accumulate; before.png and
    after.png bracket each condition's gestures for checking by eye (a spread pinch zooms in and lowers the
    camera pitch, a contracting pinch zooms out; the commanded values are relative, not absolute, and a
    registration estimate between the frames is not reliable once the pitch changes). Output out/<condition>/walk_C_<bearing>/ is what `multileg` and `motion --commanded`
    read; truth.json (camera anchor) is copied in from args.truth when given. Needs the Control API env
    vars like walkcap; gestures never tap, so no screen is opened by accident.
    """
    import json
    import shutil
    import time

    host, key = _preflight()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    _focus(args.serial, GAME_ACTIVITY)
    time.sleep(2)
    cx, cy = GESTURE_CENTER
    try:
        for cond in args.conditions:
            zoom, twist = parse_condition(cond)
            cdir = out / condition_name(zoom, twist)
            cdir.mkdir(parents=True, exist_ok=True)
            _adb_screencap(args.serial, cdir / "before.png")
            if zoom != 1.0:
                _twist(args.serial, pinch_events(cx, cy, PINCH_BASE_PX, PINCH_BASE_PX * zoom))
                time.sleep(1.5)
            if twist:
                _twist(args.serial, twist_events(cx, cy, 250, 0.0, twist))
                time.sleep(1.5)
            _adb_screencap(args.serial, cdir / "after.png")
            info = {"zoom_cmd": zoom, "twist_cmd_deg": twist, "legs": args.legs}
            (cdir / "condition.json").write_text(json.dumps(info, indent=1))
            if args.truth:
                shutil.copy(args.truth, cdir / "truth.json")
            print(f"{cdir.name}: {info}", flush=True)
            for b in args.legs:
                st = _api(host, key, "GET", "/state")
                lat, lon = commanded_target(st["position"]["lat"], st["position"]["lon"], b, args.dist)
                n = _capture_leg(host, key, args.serial, cdir / f"walk_C_{int(b):03d}", b, lat, lon, args.every,
                                 args.timeout, {"condition": cdir.name})
                print(f"  leg {b}: {n} frames", flush=True)
    finally:
        _api(host, key, "POST", "/walk/stop")


def cmd_multileg(args):
    """Fit one ground->screen map from every walk_C_* move (no device) and write calibration.json.

    Moves are frame pairs (i, i+k), both frames moving, content-flow response >= 0.1, direction from the
    commanded bearing. Held-out diagnostic: the map fitted without one bearing predicts that bearing's
    flow direction; prints the mean and sd of observed minus predicted angle.
    """
    import json

    data = Path(args.data)
    cam = json.loads((data / "truth.json").read_text())["cam"]
    legs = _commanded_legs(data)
    lat0 = next(iter(legs.values()))[0]["ll"][0]
    mv = []  # (bearing, d, f)
    for b, fr in legs.items():
        for k in args.ks:
            for i in range(len(fr) - k):
                if not (fr[i]["moving"] and fr[i + k]["moving"]):
                    continue
                fx, fy, resp = content_flow(fr[i]["img"], fr[i + k]["img"])
                if resp >= 0.1:
                    mv.append((b, along_track_move(fr[i]["ll"], fr[i + k]["ll"], b, lat0), (fx, fy)))
    print(f"{len(legs)} legs, bearings {sorted(legs)}, {len(mv)} moves with response >= 0.1")
    fit = fit_ground_map([(d, f) for _, d, f in mv])
    if fit is None:
        (data / "calibration.json").write_text(json.dumps({"rejected": True, "n_moves": len(mv)}))
        print("calibration REJECTED (too few moves, bearings not spread, or rows disagree)")
        return
    fit["cam"] = {**cam, **({"px": args.anchor[0], "py": args.anchor[1]} if args.anchor else {})}
    (data / "calibration.json").write_text(json.dumps(fit, indent=1))
    print(f"theta {fit['theta_deg']:.1f} deg  s {fit['s']:.2f}  sy {fit['sy']:.2f} px/m  skew {fit['skew_deg']:.1f} deg  "
          f"used {fit['n_used']}/{fit['n_total']}  rms {fit['rms_px']:.2f} px")
    offs = []  # leave-one-bearing-out: map fitted without leg b predicts leg b's flow direction
    for hb in sorted(legs):
        rest = fit_ground_map([(d, f) for b, d, f in mv if b != hb])
        if rest is None:
            continue
        a = np.array(rest["A"])
        for b, d, f in mv:
            if b != hb or np.hypot(*d) < 3.0 or np.hypot(*f) < 5.0:
                continue
            pred, obs = -a @ np.array(d), np.array(f)
            offs.append((math.degrees(math.atan2(obs[1], obs[0]) - math.atan2(pred[1], pred[0])) + 180) % 360 - 180)
    if offs:
        print(f"held-out (leave one bearing out): n={len(offs)} offset mean={np.mean(offs):.1f} "
              f"sd={np.std(offs):.1f} deg, |offset| p90={np.percentile(np.abs(offs), 90):.1f} deg")


def _walk_and_wait(host, key, lat, lon, timeout=300.0, every=2.0):
    """POST /walk (viaRoads false) and poll until the walk target clears. SystemExit above 3 m/s."""
    import time

    _api(host, key, "POST", "/walk", {"lat": lat, "lon": lon, "viaRoads": False})
    t0, seen, last = time.time(), False, None
    while time.time() - t0 < timeout:
        time.sleep(every)
        st = _api(host, key, "GET", "/state")
        pos, now = (st["position"]["lat"], st["position"]["lon"]), time.time()
        if last:
            dn, de = (pos[0] - last[0][0]) * 111320, (pos[1] - last[0][1]) * 111320 * math.cos(math.radians(pos[0]))
            if math.hypot(dn, de) / max(now - last[1], 1e-6) > 3.0:
                _api(host, key, "POST", "/walk/stop")
                raise SystemExit("speed above 3 m/s, aborting")
        last = (pos, now)
        seen = seen or st["walk"]["target"] is not None
        if seen and st["walk"]["target"] is None:
            return True
    return False


def cmd_taptrials(args):
    """50 on-device taps: walk to each pixel's predicted ground point, then find where the pixel's content landed.

    Needs calibration.json from multileg. A trial passes when the tapped patch ends within args.tol_m of
    the player; unverified (NCC < 0.4) and rejected-calibration trials count as fails. Pass iff
    args.need of args.n trials pass. Writes trials.json and annotated_trials/t<NN>_{before,after}.png.
    """
    import json
    import random
    import time

    data = Path(args.data)
    cal = json.loads((data / "calibration.json").read_text())
    pts = [(x, y) for x in np.linspace(150, 930, 10) for y in (1200, 1250, 1300, 1340, 1380)]
    random.Random(0).shuffle(pts)
    pts = pts[:args.n]
    if cal.get("rejected"):
        print(f"0/{len(pts)} within {args.tol_m} m: calibration rejected -> FAIL")
        return
    a = np.array(cal["A"])
    anchor = (cal["cam"]["px"], cal["cam"]["py"])
    host, key = _preflight()
    out = data / "annotated_trials"
    out.mkdir(exist_ok=True)
    _focus(args.serial, GAME_ACTIVITY)
    time.sleep(2)
    rows = []
    try:
        for i, p in enumerate(pts):
            st = _api(host, key, "GET", "/state")
            lat0, lon0 = st["position"]["lat"], st["position"]["lon"]
            g = tap_to_ground(a, p, anchor)
            tgt = (lat0 + g[1] / 111320, lon0 + g[0] / (111320 * math.cos(math.radians(lat0))))
            _adb_screencap(args.serial, out / f"t{i:02d}_before.png")
            before = cv2.imread(str(out / f"t{i:02d}_before.png"))
            row = {"i": i, "tap": [float(p[0]), float(p[1])], "ground_m": [float(g[0]), float(g[1])]}
            if np.hypot(*g) > 150:
                row.update(error_m=None, ncc=None, verdict="unreachable")
            else:
                arrived = _walk_and_wait(host, key, *tgt)
                time.sleep(3)
                _adb_screencap(args.serial, out / f"t{i:02d}_after.png")
                after = cv2.imread(str(out / f"t{i:02d}_after.png"))
                off, ncc = patch_offset(before, after, p, anchor)
                err = float(np.linalg.norm(np.linalg.solve(a, np.array(off))))
                row.update(error_m=err, ncc=ncc, offset_px=[float(off[0]), float(off[1])], arrived=arrived,
                           verdict="unverified" if ncc < 0.4 else ("pass" if err <= args.tol_m else "fail"))
                cx, cy = int(anchor[0] + off[0]), int(anchor[1] + off[1])
                cv2.rectangle(after, (cx - 100, cy - 100), (cx + 100, cy + 100), (0, 255, 0), 4)
                cv2.drawMarker(after, (int(anchor[0]), int(anchor[1])), (0, 0, 255), cv2.MARKER_CROSS, 60, 6)
                cv2.putText(after, f"err {err:.1f} m ncc {ncc:.2f} {row['verdict']}", (30, 100),
                            cv2.FONT_HERSHEY_SIMPLEX, 2, (0, 0, 255), 5)
                cv2.imwrite(str(out / f"t{i:02d}_after.png"), after)
                _walk_and_wait(host, key, lat0, lon0)  # back to the start, unscored
            cv2.circle(before, (int(p[0]), int(p[1])), 40, (0, 255, 0), 6)
            cv2.putText(before, f"tap -> ground E{g[0]:.0f} N{g[1]:.0f} m", (30, 100),
                        cv2.FONT_HERSHEY_SIMPLEX, 2, (0, 255, 0), 5)
            cv2.imwrite(str(out / f"t{i:02d}_before.png"), before)
            rows.append(row)
            print(f"t{i:02d} {row['verdict']} err={row['error_m']}", flush=True)
            (data / "trials.json").write_text(json.dumps(rows, indent=1))
            if sum(r["verdict"] != "pass" for r in rows) > args.n - args.need:
                print(f"bar {args.need}/{args.n} unreachable after {len(rows)} trials, stopping")
                break
    finally:
        _api(host, key, "POST", "/walk/stop")
    errs = sorted(r["error_m"] for r in rows if r["verdict"] in ("pass", "fail"))
    passes = sum(r["verdict"] == "pass" for r in rows)
    unv = sum(r["verdict"] in ("unverified", "unreachable") for r in rows)
    pct = lambda q: errs[min(len(errs) - 1, int(q * len(errs)))] if errs else float("nan")
    ok = passes >= args.need
    print(f"{passes}/{len(rows)} within {args.tol_m} m, unverified {unv}, p50 {pct(0.5):.1f} m, "
          f"p90 {pct(0.9):.1f} m -> {'PASS' if ok else 'FAIL'} (bar {args.need}/{args.n})")


def _angle_scale(m):
    """Rotation (deg) and scale of a 2x3 similarity matrix in cv2.getRotationMatrix2D convention."""
    return -math.degrees(math.atan2(m[1, 0], m[0, 0])), math.hypot(m[0, 0], m[1, 0])


def _methods():
    """(name, fn(ref_gray, game_gray) -> (matrix or None, score, matches)). Score is method-specific."""

    def logpolar(r, g):
        m = register_logpolar(r, g)
        return m, _ncc(cv2.warpAffine(r, m, (r.shape[1], r.shape[0])), g), 0

    def orb_sim(r, g):
        m, ratio = register_orb_similarity(r, g)
        return m, ratio, 0

    def orb_hom(r, g):
        h, ratio = register_homography(r, g)
        return h, ratio, 0

    return [("logpolar", logpolar), ("orb_similarity", orb_sim), ("orb_homography", orb_hom)]


def cmd_run(args):
    """Run every method on each pair. Two kinds of rows: 'real' (game vs own map, no truth) and
    'synthetic' (game vs the same game screenshot warped by a known rotation + scale, truth known)."""
    import json
    import time

    rows = []
    for pair in sorted(Path(args.data).glob("pair_*")):
        game = cv2.imread(str(pair / "game.png"))
        ref = cv2.imread(str(pair / "reference.png"))
        if game is None or ref is None:
            continue
        g, r = _to_gray(game), _to_gray(ref)
        h, w = g.shape
        known = cv2.getRotationMatrix2D((w / 2.0, h / 2.0), args.synth_angle, args.synth_scale)
        g_synth = cv2.warpAffine(g, known, (w, h))
        for kind, a, b in [("real", r, g), ("synthetic", g, g_synth)]:
            for name, fn in _methods():
                t0 = time.perf_counter()
                m, score, matches = fn(a, b)
                ms = (time.perf_counter() - t0) * 1000
                row = {"pair": pair.name, "kind": kind, "method": name, "score": score,
                       "ms": ms, "rejected": m is None}
                if kind == "synthetic" and m is not None and name != "orb_homography":
                    got_angle, got_scale = _angle_scale(np.asarray(m))
                    row["angle_err_deg"] = abs(got_angle - args.synth_angle)
                    row["scale_err_pct"] = abs(got_scale / args.synth_scale - 1.0) * 100
                rows.append(row)
                print(f"{pair.name} {kind:9s} {name:15s} score={score:.3f} ms={ms:.0f} "
                      f"rejected={row['rejected']} {row.get('angle_err_deg', '')}")
    Path(args.data, "results.json").write_text(json.dumps(rows, indent=2))


def cmd_report(args):
    """Markdown summary per (kind, method): reject %, landing error p50/p90 (m), score, latency."""
    import json
    import statistics

    def pct(vals, q):
        vals = sorted(vals)
        return vals[min(len(vals) - 1, int(q * len(vals)))]

    rows = json.loads(Path(args.results).read_text())
    print("| kind | method | n | reject % | landing err p50/p90 (m) | median score | p50 ms | max ms |")
    print("|---|---|---|---|---|---|---|---|")
    for kind in sorted({r["kind"] for r in rows}):
        for m in sorted({r["method"] for r in rows if r["kind"] == kind}):
            sub = [r for r in rows if r["kind"] == kind and r["method"] == m]
            ms = sorted(r["ms"] for r in sub)
            land = [r["landing_error_m"] for r in sub if "landing_error_m" in r]
            land_s = f"{pct(land, 0.5):.1f} / {pct(land, 0.9):.1f}" if land else "n/a"
            rej = 100.0 * sum(r["rejected"] for r in sub) / len(sub)
            print(f"| {kind} | {m} | {len(sub)} | {rej:.0f} | {land_s} | "
                  f"{statistics.median(r['score'] for r in sub):.3f} | {ms[len(ms) // 2]:.0f} | {ms[-1]:.0f} |")


def main(argv=None):
    import argparse

    p = argparse.ArgumentParser(description="Bearing-registration bench for tap-to-walk.")
    sub = p.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("capture")
    c.add_argument("out")
    c.add_argument("--game-serial", default="0A061FDD4000JF")
    c.add_argument("--app-serial", default="0A061FDD4000JF")
    c.add_argument("--pairs", type=int, default=6)
    c.add_argument("--dx", type=int, default=120)
    c.add_argument("--dy", type=int, default=0)
    c.set_defaults(func=cmd_capture)
    r = sub.add_parser("run")
    r.add_argument("data")
    r.add_argument("--synth-angle", type=float, default=25.0)
    r.add_argument("--synth-scale", type=float, default=1.2)
    r.set_defaults(func=cmd_run)
    rl = sub.add_parser("real")
    rl.add_argument("data")
    rl.add_argument("--every", type=int, default=2, help="use every Nth walk frame")
    rl.add_argument("--rotations", type=float, nargs="+", default=[0.0, 90.0, 180.0, 270.0])
    rl.set_defaults(func=cmd_real)
    mo = sub.add_parser("motion")
    mo.add_argument("data")
    mo.add_argument("--ks", type=int, nargs="+", default=[4, 2], help="frame gap per move")
    mo.add_argument("--commanded", action="store_true", help="score walk_C_* legs from walkcap")
    mo.add_argument("--min-resp", type=float, default=0.0, help="with --commanded: drop flows below this response")
    mo.add_argument("--no-moving-filter", dest="moving_filter", action="store_false",
                    help="with --commanded: keep frames recorded at speed < 0.5 m/s")
    mo.set_defaults(func=cmd_motion)
    wc = sub.add_parser("walkcap")
    wc.add_argument("out")
    wc.add_argument("--serial", default="0A061FDD4000JF")
    wc.add_argument("--legs", type=float, nargs="+", default=[0.0, 90.0, 45.0])
    wc.add_argument("--dist", type=float, default=40.0)
    wc.add_argument("--every", type=float, default=3.0)
    wc.add_argument("--timeout", type=float, default=90.0)
    wc.set_defaults(func=cmd_walkcap)
    ml = sub.add_parser("multileg")
    ml.add_argument("data")
    ml.add_argument("--ks", type=int, nargs="+", default=[4, 6], help="frame gap per move")
    ml.add_argument("--anchor", type=float, nargs=2, help="player's feet px (x y); default truth.json cam px/py")
    ml.set_defaults(func=cmd_multileg)
    tt = sub.add_parser("taptrials")
    tt.add_argument("data")
    tt.add_argument("--serial", default="0A061FDD4000JF")
    tt.add_argument("--n", type=int, default=50)
    tt.add_argument("--need", type=int, default=48)
    tt.add_argument("--tol-m", type=float, default=15.0)
    tt.set_defaults(func=cmd_taptrials)
    vc = sub.add_parser("varycap")
    vc.add_argument("out")
    vc.add_argument("--serial", default="0A061FDD4000JF")
    vc.add_argument("--conditions", nargs="+", default=["1:0", "1.6:0", "1:40", "0.6:-60"],
                    help="zoom:twist pairs, zoom = pinch ratio (above 1 zooms in), twist in degrees")
    vc.add_argument("--legs", type=float, nargs="+", default=[0.0, 90.0, 45.0, 225.0])
    vc.add_argument("--dist", type=float, default=30.0)
    vc.add_argument("--every", type=float, default=3.0)
    vc.add_argument("--timeout", type=float, default=90.0)
    vc.add_argument("--truth", help="truth.json with the camera anchor, copied into each condition dir")
    vc.set_defaults(func=cmd_varycap)
    pr = sub.add_parser("probe")
    pr.add_argument("out")
    pr.add_argument("--serial", default="0A061FDD4000JF")
    pr.add_argument("--deltas", type=float, nargs="+", default=[40.0, -40.0, 135.0])
    pr.set_defaults(func=cmd_probe)
    rep = sub.add_parser("report")
    rep.add_argument("results")
    rep.set_defaults(func=cmd_report)
    args = p.parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()
