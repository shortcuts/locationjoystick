# Tap to Walk — bearing investigation (bench record)

The measured record behind Tap to Walk's no-compass design: how the bearing, scale and ground map were found on the bench (`scripts/registration_bench.py`, branch `tap-to-walk-no-compass`). It keeps the measured results and the reasoning that led to the shipped design. For current behaviour, see [tap-to-walk.md](tap-to-walk.md).

## What later work must not assume

- The bench constants were fitted on a Pixel 5 (1080x2340). They scale to the screen's own width and height, and the tap rows come from the device's own ground band, but only one second screen (Pixel 7, 1080x2400) has been run.
- Close-up zoom (z1.6) is not met: p90 96.9 m on the Pixel 7, target under 10 m.
- The pass rate is proven only for the matches the bench accepted (24 of 36 walked taps were skipped as ambiguous on the second Pixel 7 run).
- The map superposition retry (entry B) ran on only 4 distinct places with a sane fit; its KILL does not cover other styles or places.

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
calibration. It returns nothing (caller gets no result) when a move is under 3 m, a flow under 5 px, the moves are
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

### Monitor residuals: horizon cut and legs with no usable flow

Bench `monitor` with fixed k 0.25 and h 8, before and after the change, on one fresh `varycap` run (Pixel 5; conditions `1:0 1:8 1.6:0 1:40`, 4 legs of 30 m each, same cached flows for both runs). The grid-search `monitor` default prints the best grid cell, not the cell asked for, so a script scored the fixed cell.

| | False alarms | Unchanged legs |
|---|---|---|
| Before | 3 | 8 |
| After | 3 | 8 |

Every alarm row (including the 1.6x close-up and +40 degree conditions) is identical before and after. The stored fit's horizon sat off screen in this run (negative horizon row), so the horizon cut did not fire, and no leg had zero usable moves. The change is verified on real data only as "no regression"; the cut itself and the all-unusable leg are pinned by unit tests in Kotlin and in the bench. Legs captured with a menu open were not measured: no script drives that.

What the app takes from this:

- The alarm only detects. Any alarm makes the fit suspect and the next legs feed a full four-term re-fit (decision 3 of the plan: the heading-only step is dropped).
- The CUSUM constants in `FitMonitor` are k 0.25 and h 8. On the zoom run they raised an alarm within 15 m on 2 of 4 legs at 1.6x, within 30 m on 2 of 4 at 1.3x, and none at 0.7x. A modest zoom or twist can therefore stay undetected; the cost is a landing error until the residual grows, not a silent wrong tap beyond the fit's own range.
- The learning walk is two perpendicular legs of 30 m, about one minute at walking speed.
- Feet pixel per zoom condition (reference px): 542,1488 at 1x; 540,1501 at 1.3x and 1.6x; 558,1471 at 0.7x. It moves less than 30 px, so a full re-fit re-detects it.
- Recommendations 2 and 3 are not shipped. Recommendation 2 missed on one of two samples and needs more twist data; recommendation 3 is far from its bar.

## Map superposition retry (entry B)

Command: `registration_bench.py chamfer` on the stored smoke frames. It matches road and water masks from game colours
against OSM raster tiles (zoom 18, the tiles the app draws), searching only the heading, with the position and the
pitch terms taken from each session's `calibration_persp.json`. The truth heading is that fit's `theta_deg`, which comes
from commanded bearings and not from the images under test.

Data: 9 sessions with a sane fit (5 New York, 2 London, Tokyo, Sydney), 288 frames (every third moving frame of each leg).
Only 4 distinct places have a sane fit: the other smoke locations have degenerate fits (scale near 0) and carry no truth.
Day and night frames needed separate game colour rules (rain and fog changed the night road colours again). The day
limits come from the 27 cell grid (tuned on the first 5 sessions, held-out overlap 0.31 against 0.78 tuned), which says
the colour limits do not carry over between sessions.

| Recommendation | Measured | Bar | Verdict |
|---|---|---|---|
| 1. Road and water chamfer, heading only | AUC 0.63, true heading is the top peak on 32% of frames, 21% rejected at the margin threshold, p90 landing error 646 m | AUC 0.9, top peak 80%, reject at most 20%, p90 at most 25 m | KILL |
| 2. Three frames from different places agree | Not run: AUC is below 0.7 | consensus on 80% of sessions, within 5 degrees | SKIPPED |
| 3. Directional chamfer refinement, +-10 degrees | Error reduced on 70% of 1140 starts offset by 5 and 10 degrees; p90 landing error 61.9 m before, 67.4 m after | reduced on 80% of starts | KILL |

Why it fails:

- It works on some sessions and not on others. Correct heading share per session: New York 70% (newest run) but 0 to 18% on the four older runs, London 71% and 42%, Tokyo 45%, Sydney 5%. The older New York runs have weaker fits (median residual 2 to 3 px), so part of that gap can be truth noise, which this bench cannot separate.
- Our map and the game map disagree on what a road is. Park footways are salmon dashes in OSM tiles but full roads in the game (Sydney, 5%). Pedestrian ways are lavender grey. Game road colours change with day, night, rain and fog.
- The margin score does not separate right from wrong (AUC 0.63), so a reject rule cannot save it.
- Scale and pitch search (`--ablate`, 9 cells at a 2 degree step) does not help: AUC 0.68, top peak 36%, p90 659 m.
- Game water is unreliable: the blue of Pokestops and gyms matches the water colours, so water is used only where our map also has water.

Open fact: the game's Licences screen lists "OpenStreetMap, Map data (c) OpenStreetMap Contributors" (game version 0.431.0, Pixel 5), so the game uses OSM data. The disagreement above is style and tagging, not the data source.

For shipping: nothing from entry B ships. A heading check from colour masks is not within the bar, so the automatic motion fit stays the only heading source and no Kotlin port or tap smoke gate run is needed. Only 4 places were covered, so a retry would need new captures with trustworthy truth before any conclusion beyond this colour-mask approach.

## Motion fit design (superseded)

Moved from tap-to-walk.md on 2026-10-23, when the range ring and Mercator direction replaced it. The code still ships until that direction passes its gate. Kept here as a record; do not extend it.

Held tap flow: When no valid map exists and learning is possible, the tap does not move the player. The overlay holds the tap pixel and the position it was made at, the coordinator walks its learning route, and the held tap is resolved against the new map and walked. The tap is never resolved against a map the monitor flagged as suspect, when a new one can be learned. `learnMap` returns a `LearnOutcome`, and only a fresh fit walks the held tap. The fit takes minutes (state `Analysing`, cancellable); if the player teleports or moves meanwhile, the tap is dropped with a "tap again" toast, because walking it would send the player back to the tap-time spot. Cancel is silent: the user asked for it.

Cost audit (2026-10-23, host, one desktop core, 2 legs of 18 frames at 1080x2340, `core/model` classes on synthetic frames):

| Step | Time |
|---|---|
| Full fit, `bandMoves` (21 bands, ks 2/3/4/6, 2394 band flows) | 57 s |
| One `PhaseCorrelation.spectrum` / `correlate` (360x180) | 13 ms / 14 ms |
| `FrameFlow.cleanLeg`, per leg | 1 s |
| `FeetDetector.legGradient`, per leg (full frame, though only a centre strip is read) | 0.8 s |
| `GroundMapFit.fit` | 58 ms |
| Monitor check of one leg (k 4) | 13 to 17 s |

Other cost and reliability findings: the FFT is about 10x slower than a planned FFT and allocates about 1 MB per correlation; bands run on one thread at normal priority beside the game; cancellation is checked only between legs, and the monitor check has none; capture stops while any leg is analysed, so the next leg of a walk is lost; `learnMap` gives up after 600 s, close to the 4 minutes measured on a Pixel 5. None of this was fixed, because the direction changed.

The app fits and maintains the ground map itself, so a tap maps to a metre offset, for any camera twist, zoom or screen size. There is no calibration step for the user. The earlier Settings walk (8 legs, 10 minutes, one stored record) was removed: it was a chore, it went stale the moment the player twisted or pinched the map, and nothing readable at tap time says the camera changed.

How it works (class docs and tests are the detail): while Tap to Walk is on and the game is in front, every straight stretch of a walk the app already makes (tap to walk, quick walk, joystick) becomes a leg of screenshots. Positions are the app's own mock fixes, so bearing and length are exact. A leg uses the raw move between fixes, not a projection onto its chord: fixes carry no jitter (the offset is added when the fix is built), and a gently curved leg has real cross-track motion. When a leg closes, its screen motion is compared with what the stored fit predicts. A cumulative-sum test on the residual (CUSUM) decides whether it still matches. Any alarm makes the fit suspect. A leg with no usable flow (close-up zoom, an open menu) counts as a mismatch too, because passing it in silence keeps a stale fit. The comparison scores only the bands the fit used, so bands above the fitted horizon do not inflate the residual. All four terms are then re-fitted from the legs that follow. Re-fitting the heading alone was benched and dropped: see "Automatic fit bench results" in [tap-to-walk-investigation.md](tap-to-walk-investigation.md). Route replay and roaming are not legs: their path is not straight and the jitter offset applies. A full re-fit needs legs whose bearings spread by at least `AUTOFIT_MIN_BEARING_SPREAD_DEG`, because opposite legs lie on one line and pin one axis. A failed full re-fit drops its legs, so the same legs never cost minutes of CPU twice.

Why legs, not a calibration route: every walk is a free measurement, and a camera change is noticed within the walk that follows it. The accepted cost is that a tap made between a twist and the next closed leg uses a stale fit.

Why scale is searched and never asked: a fixed or hand-set scale cannot follow a pinch. The fit solves scale together with heading.

Why a tap with no valid map walks first: with no fit there is nothing to resolve the tap against, and a wrong guess is a wrong teleport target. The learning route walks at walking speed, never a teleport, so it does not touch the teleport cooldown and does not look like a jump to the game. Progress and a Cancel action are on the foreground notification, not in the tap overlay, because the overlay takes every touch and would lock the player out of the game for minutes. The widget crosshair is the second surface, because the notification may never show (notifications denied on API 33+): it takes the active tint while learning and a click cancels instead of opening the overlay. Opening the overlay mid-learning would walk a tap and replace the learning leg, which fails the learn. The learning route is two perpendicular legs. Two legs replaced the bench's four: held-out landing error p90 6.2 m, under the 10 m bar ([investigation](tap-to-walk-investigation.md), decision 12).

Why one stored record and not one per zoom or rotation: the game exposes no signal the app can read at tap time. A record keyed by zoom or rotation could never be looked up, so there is a single latest record per device, overwritten by every re-fit.

Why per device and not exported: the map depends on the phone's screen and the game's camera. A backup restored on another device would give wrong taps. The record is rejected when its screen size differs from the current display.

Frames spool to the app cache, one leg at a time, because a leg does not fit in the heap. A leg's spool is deleted as soon as the fit is valid; while the fit is not valid, the last few legs are kept for the full re-fit, so a twist or zoom from before is never mixed with legs after it. The cache is user storage, so the spool is bounded and short-lived:

- The capture gate lives in `ScreenCaptureSource`, not in callers. It includes the Tap to Walk switch, so no frame exists while the feature is off.
- The spool is wiped when that gate closes (switch off, disclosure withdrawn, service unbound). A leg already in analysis finishes, and its frames are deleted as soon as it ends.
- The spool is also wiped when observing starts. A process kill skips the cleanup on exit, so the previous session may have left frames.
- A byte cap and a free-space floor apply before each frame. Over either, the oldest buffered leg goes first, because a refit that loses an old leg only waits for a newer one.
- Frames are deleted on every exit path, including the service ending.

At most one analysis runs at a time, never on the tap path.

Invariants a change must not break:

1. Learning walks at the walking speed constant, not the user's walk profile.
2. Geometry uses the flat-earth metres-per-degree constant. Do not swap in the haversine radius.
3. The feet pixel goes into the flow code in device pixels. Only the fit converts to the 1080x2340 reference frame. That reference is fixed; stored fits are in reference pixels.
4. Opposite legs in the synthetic test must differ in scroll size. Identical frame sets give identical temporal medians, which defeat the rejection of repeated world texture. Real walks never repeat terrain exactly.
5. A stored map is valid only for the screenshot size it was fitted at.
6. No frame is taken unless the disclosure is accepted, the screen is on and the game is in front. The check lives in the capture bridge.
7. An alarm makes the fit suspect until a full re-fit succeeds. Never silently keep a map that does not explain the screen.

#### Ground map

The ground map is a pure-Kotlin perspective model in the model module. It maps device pixels to ground offsets and back, and it has no Android dependency so the math can be tested on the host. A device pixel is scaled into the reference frame, so one fit serves any screen size. Its valid range was checked only on part of the screen and up to about 31 m; outside that it is extrapolated, not rejected.

#### Feet detection and frame flow

Feet detection finds the player's feet pixel by removing the moving world (a per-pixel temporal median over a leg), then looking for the sharp edges of the figure in a central column. Taking the minimum over legs drops roads that run parallel to one walk. The row prior exists because the detector can be pulled up by a range ring or a shadow. The prior is a fallback, and each use of it counts as a failed registration.

Frame flow removes static content (HUD, range rings, companion sprite) from the frames, then measures how each horizontal band of the screen shifted between frames a known distance apart. The perspective fit uses those shifts with robust weighting and rejects bands near the horizon.

Constants here are tuned values with a reason attached. Do not "fix" one back to a value from an older bench version. Known deviations from the bench, each covered by a test:

- Hole filling is a smoothed fill, not the library inpainting. Without the smoothing, boundary streaks copy inward and pull flows to zero.
- Bands that overlap the player disc are down-weighted, because the bench gave them full weight and they biased the forward scale.
- The reported camera is always the positive-scale solution. Both signs describe the same homography.
- The phase correlation shifts by a true cyclic shift with an integer centre. OpenCV's `fftShift` differs for odd padded sizes (1080x1920 bands pad to 135 rows), by up to 0.5 px. The Kotlin side is the correct one.

Parity: each recorded device has a fixture of bench numbers, and a test checks the port's fit and tap offsets against them. Fixtures hold numbers only, never frames. Frames are not committed because the game HUD shows the player's name.

#### Status and open work

Accepted state of the no-compass investigation (branch `tap-to-walk-no-compass`; the bench record is in [tap-to-walk-investigation.md](tap-to-walk-investigation.md)):

- Heading and scale come from the walked-leg fit. No north is assumed.
- Wide zoom on one device passes the bar. Close-up zoom does not yet.
- Twist, still-frame twist and one-parameter zoom were benched for the automatic fit: results and thresholds are in the investigation doc, "Automatic fit bench results".
- Not proven: other devices and screen sizes, close-up zoom, and the end-to-end pass rate of the port. The smoke gate for the pass rate has not been run in full.
- Smoke gate, run on 2026-10-17 on a Pixel 5 (1080x2340), not complete: 3 of 20 locations pass. Locations 4 to 9 fail: Sydney is below the bar (7 of 9 scored); the five locations after it (5 to 9) fit a static loading screen, so no candidate taps were scored. Locations 10 to 20 did not run because the Control API was unreachable. Per-location counts are in that run's `report.md` (not in the repo).
- Matching skips most candidate taps: 14 of 26 at New York. Far taps err more than near taps. Both are open work below.
- A perspective fit from a static screen (flow median under 5 px) is rejected in the bench (`fit_perspective`). The app's solver rejects a fit with a non-finite term or a median residual above a ceiling (`GroundCalibrationSolver`), but the bench's static-screen check is not ported, and that path is not run on a device.

Open work, in order:

1. Lower the skip rate of the matcher. It skips most taps as ambiguous, and the pass rate only covers the taps it could match.
2. Close-up zoom. The fit and the match step fail there, not the feet anchor alone. Needs a second device and a cause for the ambiguity before another attempt.
3. Prove the pass rate across devices, screen sizes, zooms and rotations before the epic closes. The gate is the tap smoke command in [testing.md](../testing.md).

## Relaunch bench: fixed zoom and heading on start (2026-10-24)

Question: does the game restore a fixed zoom and heading on each start? Pixel 5 (1080x2340, game 0.433.1), New York (40.758, -73.9855), default zoom at the first cycle. Each cycle: `am force-stop`, `am start` of the game activity, `walkcap` with legs 0, 90, 180, 270 at 30 m, then `persp`. Before cycle 3 (after cycle 2's fit) one `varycap` gesture ran: pinch 1.3 and twist 40 degrees. No teleport was used.

`persp` returns the same map with both signs flipped when `s` is negative (`theta_deg` is 180 degrees off). The table gives the positive-scale form.

| Cycle | Before cycle | theta (reported) | s (reported) | sy (reported) | theta | s | sy |
|---|---|---|---|---|---|---|---|
| 1 | nothing | -4.51 | 10.47 | 1.52 | -4.51 | 10.47 | 1.52 |
| 2 | nothing | 175.96 | -11.45 | -1.38 | -4.04 | 11.45 | 1.38 |
| 3 | pinch 1.3, twist 40 | -3.70 | 13.91 | 1.22 | -3.70 | 13.91 | 1.22 |
| 4 | nothing | 177.67 | -10.35 | -1.44 | -2.33 | 10.35 | 1.44 |
| 5 | nothing | 174.21 | -10.97 | -1.70 | -5.79 | 10.97 | 1.70 |

Spreads across the five cycles:

- Heading: 3.46 degrees (range -5.79 to -2.33). Gate: under 2 degrees.
- Scale `s`: 34 percent of the minimum (10.35 to 13.91). Gate: under 3 percent.
- Scale `sy`: 39 percent of the minimum (1.22 to 1.70).

Answer: no. The game does not restore a fixed zoom or a fixed heading on start, by the gate in this section. Cycle 3 shows the pinch as a 1.33 times larger scale. Cycle 4 returns to the cycle 1 and 2 level, so the change does not persist over the next start. Five cycles cannot say why, and the heading is within about 3.5 degrees of -4 in all five.

Bench notes:

- The game shows a news popup over the map on the first and second start. The cycle 1 walk ran under it, and its fit was rejected (too few usable flows). The popup was closed with a tap on its close button, and cycle 1 was walked again.
- The rejected first walk moved the mock position about 30 m per leg. Its frames are not used.

## 2026-10-24: Mercator re-check, six locations (bench 4/8, part 1)

Question: does s times cos(lat) and sy times cos(lat) stay within 7 percent across Singapore, Nairobi, Mumbai, Moscow, Sao Paulo and Cape Town? Pixel 5, one zoom, `walkcap` legs 0, 90, 180, 270 at 40 m every 3 s, then `persp`. Teleport, settle 45 s, no cooldown wait. Frames are under the git-ignored `scripts/registration-bench/smoke/20261024T120000Z_sg_retry/`.

First attempt (20261024T062712Z_mercator_recheck) failed for every location. The walk frames were a static loading screen with a location error banner, all 204 KB. The game was back on the map at the retry, and every retry walk frame was 1.7 to 2.1 MB.

| Place | Latitude | s | sy | theta | n flows | anchor | s x cos(lat) | sy x cos(lat) |
|---|---|---|---|---|---|---|---|---|
| Singapore Botanic | 1.31 | -9.33 | -1.13 | 174.23 | 1773 | prior | 9.33 | 1.13 |
| Nairobi Uhuru Park | -1.29 | -9.30 | 0.02 | 172.82 | 2280 | prior | 9.29 | 0.02 |
| Mumbai Marine Drive | 18.94 | 9.80 | 0.52 | -4.44 | 1885 | prior | 9.27 | 0.49 |
| Moscow Gorky Park | 55.73 | 8.00 | 1.65 | 0.39 | 1784 | prior | 4.51 | 0.93 |
| Sao Paulo Ibirapuera | -23.59 | rejected | | | | | | |
| Cape Town Company's Garden | -33.93 | rejected | | | | | | |

Negative `s` is the same map with both signs flipped, so `|s|` gives the scale (see the table note above on the inverted-sign form).

Spread: the four accepted fits span 4.51 to 9.33, far over 7 percent. With the four existing valid fits (Tokyo 4.55, New York 4.73, London 4.82, Sydney 5.23), the scale splits into two bands: about 4.5 to 5.2 at 35 to 56 degrees, and about 9.3 at 19 degrees or less. This fails the 7 percent rule.

Every accepted fit used the prior for the feet anchor, so the `sy` values are not usable. The logs show "outside the prior" or "no figure found" for each of them. Sao Paulo and Cape Town were rejected with "too few usable flows".

Open: the cause of the 2x scale at low latitude is not known. The prior-fallback anchor is the first suspect because it applies to all four. The anchor and the flow step must be checked before the rule can be judged.

## 2026-10-24: Mercator re-check, four high-latitude and south-edge places (bench 5/8, part 2)

Question: do s times cos(lat) and sy times cos(lat) stay within 7 percent at Anchorage, Reykjavik, Tromso and Ushuaia? Pixel 5, one zoom, teleport, settle 45 s, no cooldown wait. `walkcap` legs 0, 90, 180, 270 at 40 m every 3 s (the tap smoke gate's setting), then `persp`. Frames and fits are under the scratchpad `bench58/<place>/`, not committed.

Anchorage needed two redos. The first run (30 m legs) gave s = -0.047, and a second run (30 m legs) gave s = 0.189. Both walks ran and moved about 29 m per leg, but the frame-to-frame flow was small (median 1.8 px), so the fit did not hold. The third run (40 m legs) gave a usable fit. The first two frames are real map views, not loading screens.

| Place | Latitude | s | sy | theta | n flows | anchor | s x cos(lat) | sy x cos(lat) |
|---|---|---|---|---|---|---|---|---|
| Anchorage (run 3) | 61.22 | -4.08 | -1.24 | 178.43 | 1540 | prior | 1.96 | 0.60 |
| Reykjavik | 64.15 | 16.05 | 1.28 | -3.19 | 1519 | prior | 7.00 | 0.56 |
| Tromso | 69.65 | -11.20 | -2.41 | -178.35 | 1309 | detected | 3.90 | 0.84 |
| Ushuaia | -54.80 | 18.34 | -0.22 | -2.93 | 1581 | prior | 10.57 | 0.13 |

Spread of |s| x cos(lat) across these four, with the earlier six-location values: 1.96 to 10.57, far over 7 percent. Earlier values (Singapore 9.33, Nairobi 9.29, Mumbai 9.27, Moscow 4.51, Tokyo 4.55, New York 4.73, London 4.82, Sydney 5.23) already split into two bands. The four new values add a third spread: 1.96 (Anchorage) and 3.90 (Tromso) sit below the mid-latitude band, while 7.00 (Reykjavik) and 10.57 (Ushuaia) sit above it. No single cos(lat) correction brings the places together.

Ushuaia's `sy` is about 0.2, and its sign differs from `s`. The fit is weak here, and `sy x cos(lat)` values are not usable.

The feet anchor fell back to the prior at Anchorage, Reykjavik and Ushuaia (detected row outside the prior). At Tromso the anchor was detected.

Open: the cause of the spread is still unknown. Candidates: the feet anchor fallback, low flow per leg (a slow game walk at high latitude, or a short frame gap), and whether the game's zoom changes with latitude. Frames are needed to tell these apart before the rule can be judged.

## Superseded: ring and Mercator direction (2026-10-23 to 2026-10-24)

The plan was: scale and perspective from latitude (Web Mercator, 1/cos(lat) at a fixed zoom), zoom from the drawn range ring (a ground circle of known radius), and only the heading measured. Both scale sources failed.

Ring check (2026-10-24, Python prototype in the session scratchpad, not committed): a lavender-line detector along 90 rays from the feet plus a RANSAC ellipse fit, on bench frames.

- New York walk frames 3 s apart alternate between a ring of about 110 px and one of about 500 px radius. London finds a ring of about 300 px on every other frame. The ring is the white pulse growing over time, not a fixed circle.
- Mumbai, Moscow and Nairobi walks: no ring found. Sydney at night: none visible.
- So the ring fails the kill rule of that plan (ring missing on more than 20% of default-zoom frames).

Mercator check: see the three sections above. The scale times cos(lat) spread 1.96 to 10.57.

Crop-flow prototype (same day, `flowfit.py` in the session scratchpad): heading and lateral scale fitted from five crops per frame pair, forward scale 0.40 and perspective -2.2e-4 times the lateral scale. Against the old four-term fits as reference, one 8-frame leg per fit: London legs p90 14 to 27 m, New York 20 to 38 m, Tokyo 21 to 50 m, Sydney 12 to 60 m, Nairobi and Moscow 26 to 75 m. The reference fits are noisy (their own scale spread 2x), so this is a sanity check, not a gate. The user chose to ship this design as a first draft and bench it after.

## Gate error cause: the full-band ruler (2026-10-26, offline)

Fixed on 2026-10-27: the ruler now fits only near the feet (docs/testing.md). Superseded status of that day:

Status (2026-10-26): the gate runs on the device through `make tap-smoke`. Pixel 5, 1080x2340, Pokémon GO, normal scenario, 12 locations, 12 scored taps each, scored against the reference ruler (see docs/testing.md), run `20261026T160909Z`. Verdict FAIL: 11 of 12 rows miss the 15 m bar. Only Sydney passes (p50 9.3 m, p90 14.9 m). p90 per row, metres: New York 23.4, London 19.3, Tokyo 22.0, Sydney 14.9, Sao Paulo 24.2, Cape Town 27.6, Reykjavik 28.0, Tromso 26.2, Ushuaia 17.6, Singapore 27.2, Nairobi 23.7, Mumbai 25.6. Typical p50 is 13 to 22 m. The earlier New York PASS (p90 4.6 m, a run before this one) does not reproduce on this run, and the second earlier run was above 8 m. The earlier New York PASS (p90 4.6 m) and these failures come from the ruler, not the app (see "Gate error cause"). The old 23.8 m patch-match figure was dropped earlier because the matcher left most taps ambiguous. Open: rows 13 to 20 not run (run stopped by the user after 12 rows); fresh, twist and zoom rows have code in `scripts/tap_smoke.py` and are not proven live. Next: replace the ruler fit (the follow-up task in the epic), then rescore before any new full gate, because one full pass costs about 8 h. The backlog epic `tap-to-walk-flow-fit` lists the work left. Agents: update this section and that epic after every session, kills included.

New York noise repeat (same build, `make tap-smoke ONLY=1`, normal scenario, 12 scored taps, Pixel 5): run 1 of 3 on 2026-10-27 gave p50 20.8 m, p90 25.2 m, first tap error 11.4 m, FAIL. This matches the gate run (p90 23.4 m), not the 4.6 m pass, so the 4.6 m pass is not app noise around 4 m. Runs 2 and 3 not done (stopped by the lead).

An earlier run on 2026-10-25 scored 0 taps because the phone still ran the APK of 2026-10-23 (old learning-walk app). Always run `./gradlew installDebug` from the branch before the gate, then restart spoofing and re-enable the Control API (the port changes on each toggle).

Cause: the reference ruler, not the app. The ruler fits the whole ground band (rows 490 to 1640) with one perspective term. That model misses the rows near the feet, where every tap lands. In 10 of 12 rows the ruler's perspective term has the wrong sign (scale grows with distance) and its lateral scale at the feet is 1.2 to 3.3 times too low. A tap offset of 70 px reads as about 22 m on the ruler and about 11 m in the flow. The app walks the shorter, flow-measured distance, so the scorer counts the gap as app error.

Evidence, from the 12 `trials.json` files, the 12 reference leg sets and a Python surrogate of the app (two-term fit, same fixed ratios, north leg only, on the bench's band flows):

- Error grows with distance. `error_m` over the ruler's tap distance is 0.39 to 0.73, and `error_m` correlates with that distance at +0.83 to +0.99. A constant offset (feet anchor, ratio) would not do that. Slope of error against tap y is under 0.13 m/px with mixed sign.
- Flow per metre near the feet (rows 1392 and 1556, New York) is 6 to 7 px/m. The ruler predicts 2.5 px/m there. Far bands (row 573) agree at 3 px/m, which is why the full fit looks good (median residual 3 to 9 px).
- A ruler fitted only on rows 1200 and below has a lower residual on its own rows (1.8 to 4.2 px), a physical perspective sign (-1e-4 to -2.6e-4 per lateral px/m, close to `PERSPECTIVE_RATIO`) and a lateral scale close to the app's (Tromso 14.7 and 15.0). The surrogate lands 0.6 to 5.0 m (p90 0.8 to 6.6 m) from it in every row. Against the full ruler it lands 5 to 19 m away. That is within 4 m of the observed p50 in 10 of 12 rows and within 6.2 m in all (New York 19.3 against 20.6, London 16.5 against 15.3, Tokyo 17.3 against 19.2).
- The ruler is unstable between runs. New York fits at the same place, one per run, gave a lateral scale of 2.0 to 3.2 in five runs, 5.96 in one and 11.3 in one, and a perspective term from -1.5e-3 to +2.8e-3. The 4.6 m PASS (`ny3`) reused a stored reference (`reference()` returns an existing `calibration_persp.json`; `full` and `ny2` share the file) with lateral 5.96 and perspective -1.5e-3. That ruler agrees with the near-feet ruler (6.66). The surrogate lands 5.2 m (p90 6.7) from it, against the observed 3.9 m (p90 4.6). Same app, a good ruler, a pass. The failing run refit its ruler with lateral 3.18.

Error direction per row. `trials.json` stores only the error length, so the split below comes from the surrogate against the ruler. Along-ray is the share of the ruler's tap distance the app walks too short (negative) or too far; across-ray is the sideways share (heading and skew). Observed ratio and correlation come from `trials.json`.

| Row | Observed p50 / p90 m | Observed error / distance | Corr(error, distance) | Ruler lateral | Near-feet lateral | App-fit lateral | Along-ray % | Across-ray % |
|---|---|---|---|---|---|---|---|---|
| New York | 20.6 / 23.4 | 0.69 | +0.87 | 3.18 | 6.30 | 7.34 | -64 | +4 |
| London | 15.3 / 19.3 | 0.56 | +0.97 | 4.08 | 8.04 | 9.11 | -61 | +5 |
| Tokyo | 19.2 / 22.0 | 0.61 | +0.98 | 3.57 | 6.33 | 7.02 | -54 | +5 |
| Sydney | 9.3 / 14.9 | 0.39 | +0.98 | 5.22 | 6.06 | 6.20 | -20 | +4 |
| Sao Paulo | 18.8 / 24.2 | 0.66 | +0.99 | 3.08 | 5.43 | 6.20 | -56 | +4 |
| Cape Town | 22.0 / 27.6 | 0.73 | +0.97 | 2.34 | 5.47 | 6.01 | -64 | +10 |
| Reykjavik | 14.0 / 28.0 | 0.66 | +0.93 | 4.01 | 11.59 | 12.18 | -76 | +2 |
| Tromso | 15.5 / 26.2 | 0.67 | +0.83 | 4.45 | 14.74 | 15.04 | -80 | 0 |
| Ushuaia | 13.0 / 17.6 | 0.44 | +0.84 | 5.19 | 8.97 | 9.38 | -51 | +8 |
| Singapore | 22.4 / 27.2 | 0.72 | +0.85 | 2.88 | 4.79 | 5.10 | -54 | +2 |
| Nairobi | 18.6 / 23.7 | 0.64 | +0.92 | 3.23 | 4.79 | 5.22 | -43 | +3 |
| Mumbai | 20.9 / 25.6 | 0.70 | +0.97 | 2.56 | 5.03 | 5.02 | -52 | +2 |

Reading: the error is a scale error along the tap ray (the app walks about half the ruler's distance), with a small sideways part. The heading is a minor part: the app-fit heading is within 9 degrees of the near-feet ruler in every row, and the full ruler is 1 to 10 degrees off the near-feet one. Sydney passes because its full ruler happens to have lateral 5.22, close to the near-feet 6.06.

Caveats. The near-feet ruler is also flow-based, so this shows the full ruler disagrees with the flow, not that the flow is the truth. An independent check (patch landing on the tapped pixel) is still open. The surrogate uses the bench's 9-band flows, not the app's five crops, and the north leg only. The app's stored map is not in the gate data.
