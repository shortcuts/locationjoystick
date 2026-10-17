# Tap to Walk — state-of-the-art research (automatic fit and superposition retry)

Scope: what published work says about two backlog entries. Entry A is an automatic ground fit with no calibration step that adapts to camera twist and zoom. Entry B is a retry of map superposition (game screenshot against the app's own map). Date: 2026-10-17. Sources are papers, official library source and platform source. Each claim carries its URL. Claims marked "derived" are our own reasoning from a cited result, not a published result. For the measured history, see [tap-to-walk-investigation.md](tap-to-walk-investigation.md). For shipped behaviour, see [tap-to-walk.md](tap-to-walk.md).

Constraints that every finding is judged against:

- Screen pixels only. Nothing read from the game.
- On-device Kotlin in the model module. No OpenCV in the app build.
- Battery. The platform rejects screenshots closer than 333 ms apart (`ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS`, [AOSP source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/accessibilityservice/AccessibilityService.java)). The rejection code `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` is API 30 ([Android reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)).
- The landing bar for entry B: reject rate at most 20%, p90 landing error at most 25 m.

## 1. Camera calibration from known ego-motion and image flow

Findings:

- One plane homography fixes the camera pose and two intrinsic parameters. Zhang writes the plane-to-image map as `H = A [r1 r2 t]`. He derives two constraints on the intrinsics per homography, because `r1` and `r2` are orthonormal. With one image, only two intrinsic parameters can be solved ([Zhang, MSR-TR-98-71 / TPAMI 2000](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/tr98-71.pdf), eqs. 2–4 and section 3.1).
- A plane that only translates gives no new intrinsic information (Zhang, Proposition 1, same source). Walking is a pure ground translation. So more walking refines the fitted homography but never separates intrinsics from pose.
- Rotation about the plane normal is the useful motion. Knight, Zisserman and Reid show that the complex eigenvectors of the plane-induced homography match the circular points of that motion. Three such homographies give the calibration linearly ([Knight et al., CVPR 2003](https://ora.ox.ac.uk/objects/uuid:ddfd8c0d-7cc0-4da3-9d7c-6e2f12ee5216)). A camera twist about the player is exactly this motion.
- Camera-to-odometer calibration from paired motions has an analytical least-squares solution with no initial guess ([Guo, Mirzaei, Roumeliotis, ICRA 2012](https://experts.umn.edu/en/publications/an-analytical-least-squares-solution-to-the-odometer-camera-extri/)). This is the hand-eye analogue of our walked legs.
- Online re-estimation of the camera-ground geometry inside odometry exists for vehicles. Ground-VIO calibrates camera-ground parameters online and auto-calibrates the inverse perspective map ([Zhou et al., arXiv 2306.08341](https://arxiv.org/abs/2306.08341)). One lane-based method updates pitch, yaw, roll and height with an extended Kalman filter ([arXiv 2008.03722](https://arxiv.org/abs/2008.03722)). Neither gives a number that transfers to a rendered game map.
- Cheap change detection: CUSUM tests a running sum of residuals for a shift in a distribution parameter ([Page, Biometrika 1954](https://doi.org/10.1093/biomet/41.1-2.100)).

Applicability (derived):

- Our fitted model already has the pinhole form: heading, lateral scale, forward scale, perspective term about the feet pixel (`fit_perspective` in the bench). A twist changes only the heading term: `H' = H · Rz(Δ)` in ground coordinates centred on the player.
- So the image-to-image map between two frames at the same position across a twist is `G = H Rz(Δ) H⁻¹`. Its eigenvalues are `1, e^{±iΔ}`. The twist angle comes from the eigenvalue phase without knowing `H` (from Knight et al. above).
- A zoom changes pitch and distance (bench record, "Automated varied-camera capture"). `G` is then not conjugate to a rotation. That difference classifies the change: rotation-like means re-fit heading only; anything else means a full re-fit.
- In the bird's-eye view (frames unwarped with the current `H`), the flow field splits into translation (walk), curl (twist about the player) and divergence (zoom). A 4-parameter similarity fit per frame pair gives all three. It is linear least squares and costs little.
- Minimum motion for a heading-only re-fit, from our own record: east moves gave about 13 px/m and the fit residual was 2.7 px rms. A heading error of 2 degrees needs a flow of about 2.7 / tan(2°) ≈ 77 px, so about 6 m of movement on one row band. Several bands and frames average this down. North moves often show 0 px for 6 m (stepped redraw), so a heading-only re-fit must accept moves in any direction and must not need a perpendicular pair.
- A full re-fit (four parameters) needs at least two non-parallel moves (the two-move prototype rule). It needs moves spread over rows for the perspective term.

Expected accuracy: no source states an accuracy for this setting. Our offline check gives p90 6.7 to 7.4 m at wide zoom.

## 2. Heading after a twist, without walking

Findings:

- Fourier-Mellin registration recovers rotation and scale from the magnitude spectrum, which is invariant to translation, by a log-polar resample and phase correlation ([Reddy and Chatterji, IEEE TIP 1996](https://doi.org/10.1109/83.506761)).
- Same-source pairs are easy. Our synthetic same-image warps gave 0.0 degree median angle error with ORB (bench record, "Earlier synthetic-only table"). Pre-twist and post-twist frames are same-source: same renderer, same style, same position.
- Our one log-polar attempt on raw before/after frames returned about 0 degrees for a visible change (bench record). Derived cause: raw pitched frames are not related by a 2D rotation, and the static HUD dominates the spectrum.

Applicability (derived):

- Unwarp both frames to the bird's-eye view with the stored `H`, mask static content with the existing temporal-median mask, and resample in polar coordinates about the feet pixel. A twist is then a circular shift along the angle axis. A 1-D circular phase correlation per radius ring finds it. This needs no Fourier-Mellin scale axis, because a twist does not change scale.
- The eigenvalue test from section 1 runs on the same frame pair. It confirms that the change was a twist and not a zoom.
- Continuity tracking during the gesture is not possible cheaply. At 333 ms per screenshot a fast twist moves many degrees between frames. A before/after pair at rest is sufficient, because the player does not move during a twist.
- Trigger: the app does not see the user's gestures in the game. A twist is found only when a frame is captured. Candidate trigger: the first frames of the next walk show a residual against the stored fit (section 5).

## 3. Cross-view and map-to-image registration

Findings:

- OrienterNet matches a neural bird's-eye view of a photo against OpenStreetMap rasterised at 0.5 m/pixel with 7 area, 10 line and 33 point classes ([Sarlin et al., CVPR 2023, arXiv 2304.02009](https://arxiv.org/abs/2304.02009)). It needs known intrinsics and known gravity: images are rectified to zero roll and tilt. Single-image recall on its MGL set: position 15.8 / 47.8 / 59.0% at 1 / 3 / 5 m, orientation 22.1 / 52.6 / 66.3% at 1 / 3 / 5 degrees. Fusing 20 s of frames on KITTI raises orientation recall at 1 degree from 34.3% to 77.9% ([full text](https://ar5iv.labs.arxiv.org/html/2304.02009)). Runtime is 94 ms on an RTX 2080 GPU with ResNet-101 and VGG-16 encoders (same source).
- OrienterNet code and weights are CC-BY-NC ([repository](https://github.com/facebookresearch/OrienterNet)). The network was trained on photos, not on rendered maps.
- SliceMatch estimates 3-DoF pose against aerial images and assumes small pitch and roll ([Lentsch et al., arXiv 2211.14651](https://arxiv.org/abs/2211.14651)). Median errors with unknown orientation: 5.07 m and 5.15 degrees (VIGOR same-area), 11.85 m and 7.96 degrees (KITTI cross-area, no prior) ([full text](https://ar5iv.labs.arxiv.org/html/2211.14651)).
- MapLocNet and U-BEV register a BEV against navigation or SD maps with surround-view or multi-camera input ([MapLocNet, arXiv 2407.08561](https://arxiv.org/abs/2407.08561); [U-BEV, arXiv 2310.13766](https://arxiv.org/abs/2310.13766)). Both are vehicle setups.
- Map-to-image registration with classical cost: Elliethy and Sharma register a vector road map to oblique aerial video. They minimise the chamfer distance between detected moving vehicles and the road lines under a projective transform, computed with a distance transform and Levenberg-Marquardt. They start from an approximate projection from metadata. They report that SIFT and SURF give spurious cross-modal matches that make RANSAC fail ([Elliethy and Sharma, EI 2016](https://www.hajim.rochester.edu/ece/sites/gsharma/papers/ElliethyRoadMap2ObliqueWAMIRegEI2016.pdf)).
- Road-map-only localisation can be ambiguous on repeated grids ([Brubaker, Geiger, Urtasun, CVPR 2013](https://openaccess.thecvf.com/content_cvpr_2013/html/Brubaker_Lost_Leveraging_the_2013_CVPR_paper.html)).

Applicability:

- Every learned method above assumes known pitch and intrinsics, a photo domain, and a GPU-class budget. None was shown on a rendered game map. A small network would need our own training set; the bench record notes that about 20 correlated frames are not one.
- The best published orientation numbers (median about 5 degrees, R@3° about 53%) are too coarse for far taps. Our record says one degree of heading is about 6 m at the top of the frame. Derived: 5 degrees is about 30 m there, which alone exceeds the 25 m bar. A source that suggests standalone superposition will not reach the bar: OrienterNet and SliceMatch orientation errors.
- The Elliethy result is the closest classical match. It is a local refinement from a good prior, not a global search. That fits entry B as a verifier or refiner seeded by entry A.

## 4. Making a stylised map comparable to OSM tiles

Findings:

- Chamfer matching scores an edge set against a distance transform of the other edge set. Borgefors made it coarse-to-fine ([Borgefors, TPAMI 1988](https://doi.org/10.1109/34.9107)). Directional chamfer adds edge orientation and computes the cost in sublinear time with 3D distance transforms and directional integral images, up to about 45 times faster ([Liu et al., CVPR 2010](https://merl.com/publications/TR2010-045)).
- An exact Euclidean distance transform runs in linear time with a short algorithm ([Felzenszwalb and Huttenlocher, Theory of Computing 2012](https://theoryofcomputing.org/articles/v008a019/)). It is easy to port to pure Kotlin.
- Mutual information aligns images whose intensities differ in kind ([Viola and Wells, IJCV 1997](https://doi.org/10.1023/A:1007958904918)).
- Learned feature transforms before phase correlation beat classical phase correlation across heterogeneous sensors ([Chen et al., DPCN, arXiv 2008.09474](https://arxiv.org/abs/2008.09474)). No numbers are in the abstract.
- OpenCV `phaseCorrelate` reports `response` as the signal power in the 5x5 window around the peak, from 0 to 1 ([OpenCV source](https://github.com/opencv/opencv/blob/4.x/modules/imgproc/src/phasecorr.cpp), lines near `weightedCentroid`). It is not a peak-to-sidelobe ratio.

Applicability (derived):

- Both maps come from OSM-like vector data. The game's map source is not documented by the game's maker (see "Not found / open"). Roads and water are the classes most likely to survive both styles. Park green failed in our record, so do not rely on area fills.
- Luminance and ORB failed across styles in our record. The literature agrees: cross-modal keypoints fail (Elliethy), so binary class masks and distance transforms are the standard fix.
- Unwarp the game frame to the bird's-eye view first with the fitted `H`. Keep only the near ground band: unwarp error grows with distance.
- Our own map can be rendered with only road and water layers in MapLibre. That gives a clean mask with no colour classification on our side.

## 5. Match confidence that separates right from wrong

Findings:

- Peak-to-sidelobe ratio (PSR) is `(g_max − μ_sl) / σ_sl`, with the sidelobe being the correlation output outside an 11x11 window around the peak. MOSSE tracking shows PSR of 20 to 60 when the track is good and about 7 on failure. For a naive filter, PSR stayed between 3 and 10 and did not predict quality ([Bolme et al., CVPR 2010](https://www.cs.colostate.edu/~draper/papers/bolme_cvpr10.pdf), section 3.5).
- Lowe's ratio test rejects a match when the nearest-to-second-nearest distance ratio is above 0.8. That removes 90% of false matches and under 5% of correct ones on his test set ([Lowe, IJCV 2004](https://www.cs.ubc.ca/~lowe/papers/ijcv04.pdf), section 7.1).
- Forward-backward error detects tracking failures by tracking forward then backward and measuring the gap ([Kalal, Mikolajczyk, Matas, ICPR 2010](https://dspace.cvut.cz/handle/10467/9553)).
- Multi-frame fusion turns a weak single-frame match into a strong one: the OrienterNet KITTI numbers in section 3.

Applicability (derived):

- Our bench thresholds `response` at 0.1. `response` measures peak energy, not peak uniqueness. Replace it with PSR or a second-peak margin.
- For a heading search, the score is a 1-D circular profile over angle. Use the margin between the best peak and the best peak outside ±15 degrees. Also check 90 and 180 degrees offsets explicitly, because street grids repeat (Brubaker above).
- Cycle check for flows: estimate the shift A→B and B→A. Keep a band only if they cancel.
- Multi-frame consensus: accept a heading only when several frames at different positions agree. Our record's best-scoring wrong heading (about 170 degrees off with score 0.3) is the failure this guards against.
- Every threshold must be set on a labelled bench set, using the area under the ROC curve of score for right vs wrong. The MOSSE numbers do not transfer.

## 6. The game's camera and map source

- The only near-primary statement found is a report that the game's in-app license section attributes OSM, posted to the OSM talk-us list in December 2017 ([talk-us, 2017-12](https://lists.openstreetmap.org/pipermail/talk-us/2017-December/018199.html)). Confirm it on the device's license screen before relying on it.
- No official document on the game's camera (field of view, pitch range, zoom curve) was found.

## Recommendations for entry A (automatic fit, twist and zoom)

Ranked by expected value per effort.

1. **Residual monitor plus heading-only re-fit.**
   - Method: for each frame pair in any walk, predict the band flows from the stored fit and the commanded bearing (`predict_flow`). Run CUSUM on the residual. On alarm, re-fit the heading only, scales fixed, from the moves of the current walk.
   - Why: a twist changes one parameter (section 1). One parameter needs about 6 m of movement in any direction. Walks already make these frames.
   - Needs: the stored fit, frames during walks (already captured every 3 s), a per-band residual.
   - Bench experiment: `varycap` with conditions `1:0 1:40 1:-60` and four legs each. Fit on condition 1, then feed the frames of conditions 2 and 3 in order. Measure frames until alarm and heading error after re-fit, against a full `persp` fit per condition.
   - Kill: alarm needs more than 15 m of walking, or the re-fitted heading is more than 3 degrees from the full per-condition fit in more than 20% of runs.

2. **Twist angle from a stationary frame pair (no walking).**
   - Method: unwarp the last frame before and the first frame after a suspected change with the stored `H`. Mask static content. Resample in polar coordinates about the feet pixel. Find the circular shift by 1-D phase correlation. Confirm with the eigenvalue test of the image-to-image homography (rotation-like vs not).
   - Why: the twist is a pure rotation in the bird's-eye view (section 2). It uses same-source frames, which matched with 0 degree error in our synthetic test.
   - Needs: two frames at the same position. Polar resample and 1-D FFT in Kotlin.
   - Bench experiment: in a `varycap` run, use `before.png` and `after.png` of each twist condition. Compare the angle with the difference of the per-condition `persp` headings.
   - Kill: error above 3 degrees on more than 20% of twists, or the eigenvalue test does not separate twist from zoom on the `1.6:0` and `0.5:-60` conditions.

3. **Zoom as a learned one-parameter family.**
   - Method: test whether the game's pinch moves the camera along a fixed curve (pitch and distance tied together). If so, store the curve as the fitted (s, sy, a) for a few zoom states. At runtime estimate one zoom value plus heading, from the divergence of the bird's-eye flow and a short walk.
   - Why: a full four-parameter re-fit needs moves spread over rows and over two directions. One zoom value plus heading needs much less. Zhang's result says one homography already fixes pose plus two intrinsics, so the per-zoom fits should be consistent if the field of view is fixed.
   - Needs: `varycap` data at three or more zoom levels. No published camera spec exists (section 6), so this must be measured.
   - Bench experiment: `varycap` with `1:0 1.3:0 1.6:0 0.7:0`. Fit `persp` per condition. Check whether (s, sy, a) lie on one smooth curve and whether the decomposed focal length is constant.
   - Kill: the decomposed focal length varies by more than 10% between zooms, or a curve fitted on three zooms predicts the fourth with p90 landing error above 15 m in `landing`.

Fallback when no fit exists: a short automatic walk of two non-parallel legs, as the backlog entry states. The literature adds nothing cheaper for a first fit with unknown zoom.

## Recommendations for entry B (superposition retry)

Plain statement: no source suggests that standalone superposition beats the bar. The best learned cross-view methods report median orientation errors near 5 degrees on photos, with known pitch and intrinsics, on a GPU. That is about 30 m at the top of our frame. The classical method closest to our case (Elliethy) refines from a good prior. Treat entry B as a verifier and refiner seeded by entry A, not as a replacement.

1. **Road and water masks, bird's-eye unwarp, 1-D heading search, chamfer score, margin test.**
   - Method: unwarp the game frame with entry A's `H` (pitch and scale known). Classify road and water pixels by colour. Render our own map with road and water layers only. Translation is known (feet pixel and mock position), so search heading only. Score each heading by the chamfer distance (distance transform of our mask, sampled at game road pixels). Accept only with a margin over the best peak outside ±15 degrees and a check at 90 and 180 degrees.
   - Why: the earlier attempt used luminance and park green with an older, noisier camera model. Class masks with distance transforms are the standard cross-modal fix (section 4). Heading only is a small search.
   - Needs: a road and water colour classifier for the game style, a road-only render of our map, a Kotlin distance transform (Felzenszwalb-Huttenlocher).
   - Bench experiment: extend `real` with a `chamfer_heading` method. Use the `varycap` frames where the `persp` fit gives an independent heading. Report the margin of correct vs wrong headings as an ROC AUC, then reject rate and landing error.
   - Kill: AUC below 0.9, or the true heading is not the top peak in at least 80% of frames, or p90 landing error above 25 m at a 20% reject rate.

2. **Multi-frame consensus over a walk.**
   - Method: run recommendation 1 on several frames at different positions along one walk. Accept a heading only when at least three agree within 3 degrees.
   - Why: OrienterNet's orientation recall at 1 degree more than doubles with 20 s of fused frames (section 3). Our record's wrong winner was consistent per frame but positions were few.
   - Needs: recommendation 1 working on single frames with AUC above about 0.7.
   - Bench experiment: same data, group frames per leg, score agreement vs truth.
   - Kill: consensus reject rate above 20% at the bar.

3. **Directional chamfer as the refiner of entry A's heading.**
   - Method: start at entry A's heading. Refine heading (and optionally scale) by local minimisation of directional chamfer cost over ±10 degrees.
   - Why: this is the Elliethy setup (good prior, local refinement). It can correct the 6 to 11 degree held-out offsets seen in the record, without global ambiguity.
   - Needs: recommendation 1's masks. Edge orientation from the mask gradient.
   - Bench experiment: perturb the `persp` heading by ±5 and ±10 degrees. Measure the refined error and the change in `landing` p90.
   - Kill: refinement does not reduce heading error on at least 80% of perturbed starts, or `landing` p90 gets worse.

Not recommended now: a learned matcher (OrienterNet, SliceMatch, LightGlue). Reasons: photo-domain training, GPU budget, and licences. OrienterNet weights are CC-BY-NC ([repository](https://github.com/facebookresearch/OrienterNet)). LightGlue is Apache-2.0, but its SuperPoint weights follow "a different, restrictive license", and ONNX export is a third-party project ([LightGlue repository](https://github.com/cvg/LightGlue), [ICCV 2023](https://mlanthology.org/iccv/2023/lindenberger2023iccv-lightglue)). LightGlue with ALIKED (BSD-3) or DISK (Apache-2.0) stays an option for same-source frame pairs (entry A, recommendation 2) if the direct method fails.

## Not found / open

- No official document of the game's camera: field of view, pitch range, zoom curve, or whether the camera orbits the player exactly.
- No official statement from the game's maker on OSM as the map source. Only a 2017 OSM mailing-list report of the in-app license text exists. Data vintage and which OSM classes the game draws are unknown.
- No published accuracy for any registration method on stylised or game-rendered maps.
- No published accuracy for ground-plane self-calibration from rendered map flow. All numbers in sections 1 and 2 for our setting are derived.
- Ground-VIO and the lane-based EKF give no transferable accuracy. Only their abstracts were checked.
- The Borgefors and Liu chamfer papers were checked by bibliographic record and abstract, not full text.
- DPCN's venue and quantitative results were not verified. Only the arXiv abstract was read.
- The PSR thresholds (20 to 60 good, about 7 failure) are for MOSSE tracking. Their transfer to map heading search is not shown anywhere.
