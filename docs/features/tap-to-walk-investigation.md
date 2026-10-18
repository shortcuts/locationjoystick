# Tap to Walk — bearing investigation (bench record)

The measured record behind Tap to Walk's no-compass design: how the bearing, scale and ground map were found on the bench (`scripts/registration_bench.py`, branch `tap-to-walk-no-compass`). It keeps the measured results and the reasoning that led to the shipped design. For current behaviour, see [tap-to-walk.md](tap-to-walk.md).

## What later work must not assume

- The bench constants were fitted on a Pixel 5 (1080x2340). They scale to the screen's own width and height, and the tap rows come from the device's own ground band, but only one second screen (Pixel 7, 1080x2400) has been run.
- Close-up zoom (z1.6) is not met: p90 96.9 m on the Pixel 7, target under 10 m.
- The pass rate is proven only for the matches the bench accepted (24 of 36 walked taps were skipped as ambiguous on the second Pixel 7 run).

## Bearing registration investigation

Goal: recover the map's rotation and scale from a screenshot of the game map, without a compass icon and without
reading the other app's memory. Method: register the game screenshot against this app's own map rendered around
the same position. Harness: `scripts/registration_bench.py` (run with `scripts/registration-bench/.venv`).

Dataset and ground truth. One device only (Pixel 5, 1080x2340, 440 dpi; a second display config was not built,
because changing density restarts the game on a live account). One location (Louvre courtyard, Paris). The game
view is a fixed pitched 3D camera at a closer zoom than our top-down map, so hand-annotated landmark pairs were not
reliable. Ground truth instead comes from motion: the mock walked about 25 m north and then east (game frames every
3 s, positions read from the system), and a pitched-camera model (heading, lateral and forward pixels per metre,
perspective term) was fitted so that the ground flow between frames matches the walked distance. This truth does not
use the registration images, but it is noisy: fitting the north walk and the east walk alone gave headings of -53 and
-38 degrees (joint fit -46.7, 8 px/m across at the player, 3.2 px/m forward), so treat truth as good to about 8
degrees and 15% scale (superseded by "Re-measurement against the commanded heading" below). The Pyramid pools (blue triangles left of the player in the game) agree with -47 and not with
the heading the best-scoring matches picked.

Landing error is the largest gap in metres between where a tap would land under the estimate and under the truth,
over a 3x3 grid of game pixels on the ground band. Rows were scored on 10 walk frames, with our map also rotated
0/90/180/270 degrees about the marker (rotation is the unknown the method must recover). Host numbers:

| kind | method | n | reject % | landing err p50 / p90 (m) | median score | p50 ms |
|---|---|---|---|---|---|---|
| real | logpolar (raw) | 10 | 0 | 760 / 773 | -0.07 | 415 |
| real | orb_similarity (raw) | 10 | 0 | 398 / inf | 0.23 | 72 |
| real | orb_homography (raw) | 10 | 0 | 650 / inf | 0.23 | 83 |
| real | unpitch + heading search, luminance | 10 | 0 | 221 / 610 | 0.06 | 3095 |
| real | unpitch + heading search, park-green | 10 | 0 | 611 / 612 | -0.12 | 3072 |

("raw" = the method run on the pitched screenshot, as in the earlier synthetic table below. "unpitch" removes the
camera pitch with the per-device calibrated camera, then searches the heading about the player marker only; the
function `register_unpitched` recovers a synthetic heading within 1 m at 100 m reach.) Rotated rows are identical
to the 0 degree rows within the disc effect; the full table is `registration_bench.py real` then `report`.

Method choice: none viable. Rule: lowest p90 landing error with reject rate at most 20% and p90 at most 25 m.
Nothing is within an order of magnitude; the best p50 is 221 m. Scores do not separate good from bad: the
best-scoring heading across five frames of both walks sat near +120 degrees, about 170 degrees from the truth, with
scores of 0.3. Colour-class features (blue, green, mixed) and template matching with free translation did not give a
consistent heading either. So the landing-error threshold, verification-score threshold and latency budget are n/a:
no method produces a score that tells a correct result from a wrong one. Host latency of the heading search is about
3 s unoptimised; on-device latency is not measured (no OpenCV in the Android build).

Learned model: not built; about 20 correlated frames from one location are not a training set.

Lead: a motion-calibrated heading, prototyped in "Motion-calibrated heading prototype" below.

Not measured, dropped by user direction: on-screen north indicator, device rotation sensors, gesture-derived heading
and the manual overlay.

Earlier synthetic-only table (same-image warps, not game-to-map):

| kind | method | n | rejected | median score | median angle err (deg) | median scale err (%) | p50 ms | max ms |
|---|---|---|---|---|---|---|---|---|
| synthetic | logpolar | 6 | 0 | 0.081 | 25.00 | 16.70 | 510 | 623 |
| synthetic | orb_similarity | 6 | 0 | 0.844 | 0.00 | 0.02 | 81 | 151 |
| synthetic | orb_homography | 6 | 0 | 0.875 | n/a | n/a | 70 | 132 |

Safety note for repeat runs: teleport jumps of 25 to 120 m in a few seconds made the game show a speed warning.
Use walking-speed moves (the `walk` Control API route) when recording frames against a live game account.

## Motion-calibrated heading prototype

Chosen bearing source (user decision): the app moves the position a known distance and direction at walking
speed, and two game screenshots around each move give on-screen north and scale with no map matching. Prototype
in `scripts/registration_bench.py` (`content_flow`, `heading_from_moves`, `motion_g_est`, subcommand `motion`):
phase correlation of the ground band gives how far the scene shifted for each move; two roughly perpendicular
moves give the whole ground-to-screen map, so heading and both pixel-per-metre scales fall out with no camera
calibration. It returns nothing (caller keeps north-up) when a move is under 3 m, a flow under 5 px, the moves are
within 30 degrees of parallel, or the two row headings disagree by more than 10 degrees.

Scored on the stored north-walk and east-walk frames, every north move paired with every east move, against the
gate (reject at most 20% and p90 landing error at most 25 m):

| kind | method | n | reject % | landing err p50 / p90 (m) |
|---|---|---|---|---|
| motion_k4 (about 8 m moves) | two_move | 100 | 50 | 70.7 / 86.4 |
| motion_k2 | two_move | 144 | 57 | 75.1 / 89.6 |

Verdict: not viable at the gate. Accepted estimates are tight (k=4 heading median -39.7 degrees, sd 1.7, against
the joint-fit truth of -46.7) but sit about 7 degrees off, and even with the true scales the heading-only landing
error is p50/p90 37.5 / 50.0 m on the far probes, because a degree of heading is about 6 m at the top of the
frame. Half the pairs are rejected, mostly stationary lag frames at the start of a walk. Caveat: truth comes from
these same walks (a joint fit good to about 8 degrees), so this table alone is not independent proof; see
"Re-measurement against the commanded heading" for the follow-up. At that time the app still shipped its (since removed) compass-icon detector; nothing in the
app changed.

### Re-measurement against the commanded heading

The truth above came from polled positions, which carry position jitter. Here each move's direction is the exact
bearing sent to the Control API `/walk` (known by construction), and only its length comes from the polled
positions (projected onto that bearing, `along_track_move`). Recorded with `registration_bench.py walkcap`
(three 40 m straight legs at walking speed, no teleport: north, east and a held-out north-east, one frame every
3 s, about 25 frames per leg) and scored with `motion --commanded`. Key and host come from the environment and are
never written to disk.

The game camera was at a different orientation than the first dataset (estimated heading -0.9 degrees against
-46.7), so the 7 degree offset cannot be compared frame for frame. `/state` reported `speedMs` 0 and `bearing`
null during the walk-to, so the commanded-bearing check and the moving filter use positions instead; every frame
counted as moving, so the filter changes nothing here.

| kind (about 6.5 m / 3.3 m moves) | n pairs | reject % | heading mean / sd (deg) | landing err p50 / p90 (m) |
|---|---|---|---|---|
| north x east, k=4 | 441 | 68 | -0.9 / 3.0 | 99.6 / 185.0 |
| north x east, k=4, flow response at least 0.1 | 51 | 25 | -0.9 / 2.7 | 56.4 / 130.8 |
| north x east, k=2 | 529 | 97 | -0.8 / 2.7 | 35.4 / 110.3 |
| north x north-east, k=4 | 882 | 95 | 2.4 / 11.7 | 148.0 / 561.9 |
| held-out north-east offset, k=4, response at least 0.1 (estimate minus prediction) | 4 | n/a | -6.4 / 3.8 | n/a |
| held-out north-east offset, k=2, response at least 0.1 | 9 | n/a | -10.8 / 2.5 | n/a |

Landing error is against the circular mean of the accepted north x east headings (and their median scales), so it
measures scatter, not absolute error. The held-out offset fits the ground-to-screen map on the north and east moves
only, then compares the predicted and observed flow direction of the north-east leg; without the response filter
it is noise (sd 35 to 47 degrees).

Verdict: still fails the gate (reject at most 20%, p90 at most 25 m). The accepted north x east headings are tight
(sd about 3 degrees), so the 8 degree truth noise was largely position jitter, but the held-out leg still shows a
6 to 11 degree offset (small sample), so the 7 degree offset was not only truth noise. The dominant problem is
the flow itself: north-going moves often show about 0 px of shift for 6 m of commanded movement (the game redraws
position in steps), while east moves are consistent (about 13 px/m). Only one camera orientation was recorded; the
any-orientation property is unproven (needs the user to rotate the game camera). Annotated end frames with the
estimated screen-north arrow are written to `annotated/` for checking by eye. Nothing in the app changed.

### Multi-leg calibration and on-device pass rate

Prototype only, bench script `scripts/registration_bench.py` (`multileg`, `taptrials`); nothing in the app changed.
The two-move heading was replaced by one least-squares ground-to-screen map fitted over every valid move of eight
40 m walk legs (bearings 0, 180, 90, 270, 45, 225, 135, 315, walking speed, no teleport, Pixel 5). Moves are frame
pairs 4 and 6 frames apart, both moving, flow response at least 0.1, direction taken from the commanded bearing;
outliers beyond max(3 MAD, 1.5 px) are dropped and the map refitted once. No north is assumed: the heading comes
out of the map in whatever orientation the camera has. The acceptance bar replaces the bench p90 gate: at least
48 of 50 real taps land within 15 m. A tap pixel is turned into a ground offset with the inverse map, the mock
position walks there, and the next screenshot is searched for the patch that was under the tapped pixel (masked
normalised cross-correlation, player excluded), so the error is what the game drew, not what the calibration says.
Annotated before and after frames are written to `annotated_trials/` for checking by eye.

| measure | value |
|---|---|
| legs / moves with response at least 0.1 | 8 / 182 |
| moves used after outlier drop | 106 |
| fitted heading, scales, row skew | -1.1 deg, 6.7 and 4.6 px/m, 2.5 deg |
| residual rms | 2.7 px |
| held-out, leave one bearing out (n=165) | offset mean -0.1 deg, sd 7.7 deg, p90 of absolute 3.7 deg |
| taps run | 4 of 50 (stopped once the bar became unreachable) |
| taps within 15 m | 0 of 4 |
| landing error | 17.4, 23.3, 28.6, 32.6 m (all matches NCC 0.43 to 0.82) |

Verdict: not viable at the bar (48 of 50 within 15 m); after 4 fails at most 46 can pass. Averaging fixed the
heading, not the distance: the held-out direction error is small, but every tap's content ended below the player
(the player overshot), so the single linear scale is wrong away from the band it was measured in, because of the
camera's perspective. A 2x2 map cannot fix that; a perspective term (near and far scale differ) or per-band scales
would be the next experiment. Cardinal legs gave mostly zero flow (the game redraws position in steps, as above),
so the fit leaned on the diagonal legs. Camera orientation was recorded once; the any-orientation property still
needs the user to rotate the game camera and rerun, with calibration and taps in one uninterrupted session.
Walking speed is about 0.56 m/s here, so a full 50-tap run takes hours.

### Game-camera orientation signals

Probe (`registration_bench.py probe`, stationary, screenshots and touch only, nothing read from the game): is
there an on-screen cue (compass needle, reset affordance) that reveals or fixes the camera orientation?

| Observation (Pixel 5, latest game build) | Result |
|---|---|
| Top-right search window with the pointer-location developer overlay off, camera as found | no needle; only a weather icon and red non-needle items |
| Same window after a two-finger twist of about 40 degrees (map visibly rotated) | still no needle, no compass or reset button appeared |
| Pointer-location overlay on | its red pressure bar is read by the shipped detector as a needle at 180.0 degrees (false positive) |
| Red icons elsewhere in the window (raid "R" icon, top-right corner item) | read as needles at 78 to 94 degrees (false positives) |

Verdict: no usable cue. The game shows no heading element in the window the shipped detector scans, rotated or
not, so there is nothing to reset against and the detector can only return false positives on this build.
Two-finger camera rotation is drivable from adb by a virtual touchscreen (`uinput`); raw `sendevent` is denied
to the adb shell on this device. The needle-gated criteria (found in 8 of 10 frames, twist delta, reset to 0)
could not be met because there is no needle to read.

Incident: the first probe run tapped red blobs as "reset" taps, including a red icon below the companion ball
and, from a stale position, after a frame with no blob; that opened other screens and maps. Frames 02 onward of
that run are discarded (flagged in the run's `FLAGGED_WRONG_MAP.txt`); the verdict above uses only the baseline
frames and the single manual twist captured before any tap. The script now clears the tap target every frame,
but never taps unless a blob is found in that same frame; no needle exists, so no tapping should be rerun.
Not covered: one device, one twist; a compass that appears only after a longer rotation was not seen.

### Automated varied-camera capture

Bench only (`registration_bench.py varycap`), nothing in the app changed. One command records walk legs under
several camera zoom and orientation conditions with no hand steps: per condition it drives a two-finger pinch and
a two-finger twist through a `uinput` virtual touchscreen (external to the game, no taps, nothing read from or
injected into it), then walks one straight leg per bearing through the Control API `/walk` at walking speed. No
teleport is ever called. Output is `<out>/<zoom>_<twist>/walk_C_<bearing>/`, the layout `multileg` and
`motion --commanded` already read, plus `before.png` / `after.png` around each condition's gestures and
`condition.json`. `--truth` copies the camera anchor file into each condition directory.

```
registration_bench.py varycap OUT --conditions 1:0 1.6:0 1:40 0.5:-60 --legs 0 90 45 225 --dist 30 --truth truth.json
```

| Observation (Pixel 5, latest game build) | Result |
|---|---|
| Two fingers spreading horizontally (zoom ratio above 1) | zooms in and lowers the camera pitch to a close-up of the avatar |
| Two fingers contracting (ratio below 1) | zooms out to a wide, high-pitch view |
| Two-finger twist of 40 to 60 degrees | rotates the map and the camera heading |
| Camera reset control | none; gestures accumulate, so conditions are relative to the previous one |
| 8 short walk legs (10 to 15 m) across five conditions | frames and positions recorded, walk target cleared each leg, no speed abort |

The zoom change also changes the pitch, so a close-up is not a scaled copy of the wide view. The earlier unpitched
ground map is fitted per condition (`multileg` on one condition directory); whether one calibration spans zooms is
a question the larger sample set answers, not something shown here. A log-polar estimate of the applied rotation
and scale between the before and after frames returned about 0 degrees and scale 1.0 for a visible zoom, so it
is not used: check `before.png` / `after.png` by eye. Walking is about 0.56 m/s, so 4 conditions of 4 legs of 30 m
take roughly 15 minutes of walking plus screenshots. Not covered: one device; the larger run itself was not
recorded or scored in this entry.
### Perspective ground map and offline landing check

Bench only (`registration_bench.py persp`, `landing`; `taptrials --persp`), nothing in the app changed. Two fixes
to the earlier multi-leg map. First, the flow is measured per band (seven row bands, three column thirds) after
the leg's static content (HUD, ring, companion) and the player disc are inpainted away; band rows and columns are fractions of the frame, so no screen size is special-cased, because phase
correlation over the whole band locked onto that static content and shrank every shift. Second, the 2x2 map is
replaced by a pitched-camera homography (heading, lateral and forward scale, one perspective term) fitted to
those band flows, so px per metre falls with distance (about 7.5 down to 2.6 forward at the Pixel 5 wide view).
A tapped pixel is turned into a ground offset with the inverse homography relative to the player's feet pixel
(detected from the walked legs, see below). Heading still comes out of the fit with no north assumed.

Feet pixel detection (`detect_feet`): the world scrolls under a player that stays put, so in each leg's temporal
median only the player and HUD stay sharp, and the minimum over legs of different bearings also drops roads
parallel to one walk. Along the centre column the figure is the strongest vertical run of sharp rows (gaps of up
to 5% of the height bridged for dark legs); the feet are its last row, lifted by 7% of the figure height (the one
value tuned, on the two hand-measured points). No orientation, game read or per-device number is used. On the
recorded legs it gives 537 1558 on the Pixel 7 (by eye 538 1556) and 542 1725 on the Pixel 5 (by eye 532 1720),
and `landing` without `--anchor` scores p90 9.9 m on the Pixel 7 legs (9.7 m with the hand anchor and the old bands); the wide-zoom twist datasets above are not on disk, so their rows were not re-run.
`--anchor` stays as an override. After the fit, bands at or above the fitted horizon row (`sy / a`) are dropped
and the map refitted (`fit_perspective_horizon`); with the horizon off screen this is a no-op.

Data: 4 legs of 30 m per camera state (bearings 0, 90, 45, 225), walking speed, Pixel 5, three wide-view camera
orientations reached by two-finger twists (fitted headings about -20, +5 and -21 degrees, same lateral, forward
and perspective terms within 10 percent). Check: leave one leg out, fit on the other three, then for every held-out
frame pair with a known move of 5 to 31 m and a grid of 21 pixels, compare where the model says that pixel's
content went with where a masked template match (NCC at least 0.7) finds it. Error is the ground distance in
metres. This is what a tap's landing error is, but it is not an on-device tap run.

| camera state | samples | p50 / p90 (m) | within 10 m | within 15 m |
|---|---|---|---|---|
| wide, heading about -20 | 3554 | 2.3 / 7.4 | 94% | 96% |
| wide, twisted, heading about +5 | 3263 | 2.7 / 7.1 | 95% | 98% |
| wide, twisted again, heading about -21 | 3177 | 2.3 / 6.7 | 95% | 99% |

The old 2x2 map scores p90 7.6 to 8.1 m and 11 to 12 m for moves of 20 m and more on the same data (flat fit,
exploratory run), so the perspective term matters mostly at distance; the larger gain over the first trials is the
static-content cleanup. Samples overlap (many pairs per leg), so they are not independent.

Not shown, honestly: (1) no on-device taps were run for this entry, because the app disappeared from the test
phone mid-session (not installed), so the 10 to 15 m result is the offline check above and still needs a real
tap run; the earlier on-device run (0 of 4) used the old map. (2) Close-up zoom (pinch out about 1.6x, low
pitch, horizon visible) is still not calibrated or re-captured: the band grid is now relative and horizon-aware
and the feet pixel is detected, but the close-up `landing` check (p90 under 10 m on two screens) has not been run.
The first close-up capture (side bands, hand anchor) gave a median response of 0.04. (3) The offline check only covers moves up to about 31 m and pixels
of 700 to 1420 rows; taps beyond that are extrapolated. (4) Taps whose patch ends under the player's shadow or
companion can be mismatched; check the annotated frames by eye. (5) The feet pixel detection, the flow estimator and the fit are ported to the app (`FeetDetector`, `FrameFlow`, `GroundMapFit`), but nothing in the app calls them yet (superseded: `CalibrationWalkOrchestrator` calls them; see [tap-to-walk.md](tap-to-walk.md), "Calibration walk").

## Automatic fit bench results

Data: `varycap` on a Pixel 5, 4 legs of 30 m per condition (bearings 0, 90, 45, 225), walking speed, game on a throwaway
account. Twist run: conditions `1:0 1:40 1:-60`. Zoom run: `1:0 1.3:0 1.6:0 0.7:0`. Gestures accumulate (the game has no
camera reset), so the net twist of the last twist condition is -20 degrees. The commands are `monitor`, `twist`,
`zoomfam` and `legsubset` in `scripts/registration_bench.py`; each prints PASS or KILL against the research criteria
in [tap-to-walk-research.md](tap-to-walk-research.md), "Recommendations for entry A".

| Recommendation | Measured | Bar | Verdict |
|---|---|---|---|
| 1. CUSUM alarm, then heading-only re-fit | Twist +40: alarm after 18 to 28 m, heading off 1.8 to 8.3 degrees on 4 of 4 legs. Net twist -20: no alarm on any leg. 0 false alarms on 4 unchanged legs (k 0.25, h 12) | alarm within 15 m, heading within 3 degrees on 80% of runs | KILL |
| 2. Twist from two still frames | Errors 0.2 and 3.5 degrees; peak-to-sidelobe ratio 9.6 and 5.1 | at most 3 degrees on 80% of twists | KILL (1 of 2) |
| 3. Zoom as one parameter | Focal length 1498 to 2203 px across four zooms, spread 38% | spread at most 10% | KILL |
| 12. Two learning legs instead of four | Fit on bearings 0 and 90, landing error on the other legs p50 2.0 m, p90 6.2 m | held-out p90 at most 10 m | PASS |

What the app takes from this:

- The alarm only detects. Any alarm makes the fit suspect and the next legs feed a full four-term re-fit (decision 3 of the plan: the heading-only step is dropped).
- The CUSUM constants in `FitMonitor` are k 0.25 and h 8. On the zoom run they raised an alarm within 15 m on 2 of 4 legs at 1.6x, within 30 m on 2 of 4 at 1.3x, and none at 0.7x. A modest zoom or twist can therefore stay undetected; the cost is a landing error until the residual grows, not a silent wrong tap beyond the fit's own range.
- The learning walk is two perpendicular legs of 30 m, about one minute at walking speed.
- Feet pixel per zoom condition (reference px): 542,1488 at 1x; 540,1501 at 1.3x and 1.6x; 558,1471 at 0.7x. It moves less than 30 px, so a full re-fit re-detects it.
- Recommendations 2 and 3 are not shipped. Recommendation 2 missed on one of two samples and needs more twist data; recommendation 3 is far from its bar.
