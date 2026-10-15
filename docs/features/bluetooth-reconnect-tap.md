# Bluetooth Reconnect Tap

**Status:** Implemented (off by default). Reliability proof is still open (see "Open work").

## Goal

Reconnect a Bluetooth companion device by automating the on-screen tap in the other app, with the lowest practical chance of detection. The tap is the only automated action; the Bluetooth connection itself is never driven through the Bluetooth API.

Closes only when proven 100% reliable over repeated disconnect and reconnect cycles on real devices (see the follow-up task in "Open work" below).

## Hard rule

Nothing is injected into, hooked into, patched in, or read from the target app's process or memory. Any approach that needs that is out of scope and is recorded as not possible below.

The only data used from the target app is the pixels on screen, captured the same way the compass feature already captures them (`takeScreenshot`, see [Compass Orientation](tap-to-walk.md)).

## Comparison of tap delivery paths

Columns: what the target app can observe, what the path needs, Play policy fit, verdict.

| Path | What the target app can observe | Required permissions or setup | Play policy fit | Verdict |
|---|---|---|---|---|
| **A. Accessibility gesture dispatch** (`AccessibilityService.dispatchGesture`) | Enabled-service list (already non-empty on real devices, see "Observed on test device"). Touch events carry `MotionEvent.FLAG_IS_ACCESSIBILITY_EVENT` on API 34 and later. Event device id, source and tool type are those of the injected gesture stream. Timing is whatever we choose. | Accessibility service already enabled for the compass feature. Needs `android:canPerformGestures="true"` in the service XML. | Accessibility disclosure already shipped for screenshots. Gesture use needs the disclosure updated to name the tap, shipped before the capability is reachable. Main residual review risk. | **Chosen** |
| **B. Shell or privileged input** (`input tap` via wireless ADB, Shizuku, root) | Developer options and ADB state are visible. A second helper app (Shizuku) is installable and enumerable. Events come from the shell or a privileged process. | Developer mode or a second app, plus a setup flow the app does not have. | Adds a setup burden and a dependency outside the app; not offline-first friendly. | Rejected |
| **C. Node click** (`AccessibilityNodeInfo.performAction(ACTION_CLICK)`) | Needs the target app's view tree, which the hard rule forbids reading. | Needs `canRetrieveWindowContent`, which the service does not set. Game surfaces expose few or no nodes anyway. | Reads another app's content; weakest policy fit. | Rejected |
| **D. Other platform paths** (hidden `InputManager.injectInputEvent`, `UiAutomation`, overlay pass-through) | Injection needs the signature-level `INJECT_EVENTS` permission, which a Play app cannot hold. `UiAutomation` runs only from a shell instrumentation process and conflicts with an enabled accessibility service. A `SYSTEM_ALERT_WINDOW` overlay cannot synthesize touches for another window; `FLAG_NOT_TOUCHABLE` only forwards real user touches. | Not obtainable for a normal app. | Not applicable. | Not possible |

Bluetooth API reconnect is out of scope by the task definition.

## Chosen path

**Accessibility gesture dispatch** via the existing `CompassAccessibilityService`.

Why:

- The service is already enabled, disclosed and screenshot-capable (`feature/widget/impl/.../CompassAccessibilityService.kt`, `app/src/main/res/xml/compass_accessibility_service.xml`). The tap adds no new permission prompt, no new process, no new dependency, and no new entry in the enabled-services list a target app could enumerate.
- The same service also serves `captureGrayFrame()` for the tap-to-walk calibration walk (docs/features/tap-to-walk.md, "Calibration walk"); the reconnect behaviour is unchanged.
- The app is offline-first with no extra installs (see `AGENTS.md`, Project table).
- It is the only path that reaches the target app without injection, shell access or node reads.

Implemented changes:

- `android:canPerformGestures="true"` in `compass_accessibility_service.xml`.
- Disclosure copy (`compass_disclosure_*`, `accessibility_service_description`) names the periodic screenshot and the tap, and a separate `reconnect_tap_disclosure_accepted` flag forces re-acceptance.
- No manual trigger. While `reconnect_tap_enabled` (Settings > Menus > Reconnect accessory, default off) and both disclosure flags (`getReconnectTapDisclosureAccepted()`, `getCompassDisclosureAccepted()`) are true, `CompassAccessibilityService` runs a check every `ReconnectTapConstants.CHECK_INTERVAL_MS` (1 min in release; `DEBUG_CHECK_INTERVAL_MS`, 10 s, in debug builds; first check one interval after the loop starts). Turning any flag off cancels the loop at once.
- The run logic is `runReconnectTap` in `ReconnectTapRunner.kt` (fake-driven unit tests); the detector is `ReconnectControlDetector`. The settings and consent flags are device-local and excluded from export.

## What the target app can observe on the chosen path

1. **Enabled-service enumeration** (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, `AccessibilityManager.getEnabledAccessibilityServiceList`). The app's service is already listed when the compass feature is enabled. Marginal detectability from this path is zero.
2. **Event flags.** On API 34 and later, gesture-originated events carry `MotionEvent.FLAG_IS_ACCESSIBILITY_EVENT`. This is documented platform behavior, not yet confirmed by a probe on this project's devices (see "Pending measurements").
3. **Event attributes.** Device id, source, tool type, pressure and size of the injected stream. Expected to differ from a finger; the probe in "Pending measurements" records the actual values.
4. **Timing regularity.** Controlled by the tap policy and human-like parameters below.

## Locate and classify the control

- **Region.** Search the same top-right region the compass uses (`AppConstants.CompassTrackingConstants.SEARCH_X_MIN_PCT` and `SEARCH_Y_MAX_PCT`), reused rather than duplicated. The HUD shifted up in the latest build of the target app, so the region is re-measured on test devices and the measured fractions recorded in the doc. **Pending measurement.**
- **Self-calibrating, no fixed colors.** Every run finds the ball by shape and reads its state from colors measured in that same screenshot, so theme, brightness and tint do not matter and there is no per-device calibration. Flat-color regions (neighbors within 4 per channel) are collected; a ball is an upper half and a lower half stacked into a circle (semicircle-sized, centered, nearly touching) with a dot across the split line. This is language-independent.
- **Size.** Bounds are fractions of the screen width (a half is 3% to 10% of it, the dot 12% to 50% of the half), so they hold across screen sizes.
- **Two states, by relative channel dominance.** ON: the dot is mostly green and the top half mostly red. OFF: the dot is dark and not green, the top half leans rose (red over green and blue), the bottom half leans blue. If both an ON and an OFF ball are found, ON wins.
- **Third value.** Classification returns "unknown" when no ball is found or its colors fit neither state (a grayscale ball reads unknown). "Unknown" never taps.

## Tap policy

- Tap only when the control reads "off", confirmed by two consecutive screenshots 400 ms apart. Never tap on "on" or "unknown".
- After a tap, wait 4 s, then read up to 5 times, 2 s apart. Stop as soon as one read is "on". Tap again only when all 5 reads are "off", so a connected or connecting companion is never tapped again.
- A fresh read is taken after the pre-tap pause, right before every tap. "On" ends the run as reconnected with no tap; "unknown" never taps (the run fails if a tap already fired). This closes the window where the companion connects during the pause.
- At most 3 taps per run. Abort and report after the third failure.

## Human-like timing and jitter

Values live in `AppConstants.ReconnectTapConstants`, drawn fresh for every tap from `kotlin.random.Random`:

- Pre-tap delay after a confirmed "off": uniform 700 to 2200 ms.
- Tap position: Gaussian around the blob center, sigma 1.1% of screen width (about 15% of the ball), each axis clamped to 2.5% of screen width so the tap stays inside the ball.
- Gesture duration: uniform 45 to 130 ms.

The implement task may tune these only against evidence from the reliability proof.

## Observed on test device

Recorded on the one attached device during this investigation:

- Pixel, Android API 36 (`ro.build.version.sdk` = 36), serial 32221FDH2001SU.
- Enabled accessibility services already include a third-party automation app's service. This means the enumeration row above is already non-empty on a real device, so adding the app's service does not make the device stand out in a new way.

## Measured on screenshots

Source: two 864x1920 screenshots of the target app on a night map, one per state. They are the fixtures of `ReconnectControlDetectorTest` (`feature/widget/impl/src/test/resources/reconnect/`). Detector: `ReconnectControlDetector` (pure over ARGB pixels).

- **Position.** Ball center at about 91.5% of width and 12.5% of height. Diameter about 7.2% of width. It is inside the compass search window (right 45%, top 35%).
- **ON.** Saturated red upper half, white lower half, pure green center dot (about 0, 252, 7). Signal arcs beside the ball.
- **OFF.** Dimmed ball: flat rose upper half (about 150, 97, 122), flat light-blue ring and lower half (about 86, 156, 212), dark-blue center. No green dot.
- **Detector rule.** See "Locate and classify the control". The measured colors above are test evidence, not detector inputs. Tests also cover 0.6 brightness, a red/blue tint, bilinear scaling to 1080x2400 and 691x1536, and a grayscale ball (reads null).
- **Not yet covered.** Day theme and Play-Store screens are unverified on real shots, as is a "connecting" look. Unsupported: screens narrower than about 720 px (a 0.6 downscale of the ON fixture does not read) and lossy or JPEG screenshots (`takeScreenshot` is lossless).

## Pending measurements

These need the target app's HUD in both states, a companion device, and more than one device or theme. They were not available in the investigation run and are marked pending rather than estimated.

- Probe of `MotionEvent` flags, source, device id, tool type, pressure and size for a real finger tap, `adb shell input tap`, and `dispatchGesture` taps, on API 34 and later.

## Reliability proof

**Result: INCOMPLETE. Proof stays open.** Recorded 2026-10-13, app v0.26.0, one device (Pixel 7, the only device attached; API level not recorded).

| Device | Block | Cycles passed / run | Notes |
|---|---|---|---|
| Pixel 7 | a (system English, game foreground, screen on, app in recents) | 0 / 6 | Attempts from the automated pilot. Three were aborted or dropped the link without a control state change. These rows ran on a build older than 8ee8df9b and are not valid evidence about the current code. |
| Pixel 7 | b, c, d, e | not run | Blocked by the pilot result below. |
| Pixel 7 | already-on checks | not run | |

- The adapter-toggle pilot gate ("fewer than 10 of 10 cycles report `toggle_ok`, do not run the full matrix") was not met. Some runs reported `toggle_ok` false.
- A tap fired while the control read ON in two recorded cycles. This breaks the zero-taps-when-on rule. Both rows predate 8ee8df9b; the pre-tap re-read closes the remaining window between the last read and the tap. The matrix still needs rerunning on the current build.
- Only one device was used. The task's >= 3 devices requirement was dropped by user decision, so the one-device run cannot close the task on its own.
- Full matrix: 5 blocks x 20 cycles per device, each cycle needs the companion disconnected and confirmed, so most of it has to run with an operator at the device.

**Why taps failed on the Pixel 7 (diagnosis).** Detection was not the cause. The previous fixed-color detector, run through its host port on all 8 stored 1080x2400 Pixel 7 shots, read OFF for every `before_OFF` and ON for every `*_ON`; the overlay colors match the 864x1920 fixtures. The failed rows all show `taps: 3` with every post-tap read OFF: the control was read correctly and the old runner took one read 4 s after a tap, so a connect that needed 3 s or more still read OFF and the retap toggled the companion away. The two "tap while ON" rows are the same old runner re-tapping before the adapter dropped. Commit 8ee8df9b (poll 5 reads after a tap, retap only if all are off) fixed this; the three rows after it are RECONNECTED with one tap each. No shot from a device where detection returns null exists, so that failure is unreproduced; the detector was made self-calibrating to remove its only supported fragility (exact colors). The service now logs `Reconnect tap result: <true|false>` for each gesture so a refused or cancelled tap is visible.

The raw rows are in `scripts/reconnect-matrix/cycles.jsonl` (not committed). Failures are not tuned in this task. Follow-up: see "Open work".

## Open risks

- **Play review.** Gesture use under a service that declares `isAccessibilityTool="false"` is a review risk. Mitigation: off by default, separate explicit acceptance, disclosure names the periodic screenshot and tap, never taps on unknown.
- **Target-app changes.** A HUD move or color change breaks classification. The "unknown" value prevents false taps, but reconnect then fails until the ranges are re-measured.

## Open work

- `prove-bluetooth-reconnect-tap-100-reliable-on-real-devices`: repeated disconnect and reconnect matrix on real devices.

### Automated cycles

`scripts/reconnect_matrix.py auto` runs disconnect and reconnect cycles without an operator at each cycle, for blocks a to e. Block c drives the screen with adb keyevents (sleep, 60 s, wake, dismiss a swipe lock screen, launch the game), so no operator is needed.

- Each cycle cycles the phone's Bluetooth adapter off and on to drop the companion link, then waits for the app's check.
- Connect and disconnect each take at least 3 s to load in the UI. The script waits `TOGGLE_SETTLE_S` (5 s) after every toggle before it takes a screenshot.
- Every cycle saves a before and an after screenshot to `scripts/reconnect-matrix/shots/`, named with the host-side class: `<device>_<block>_<cycle>_<before|after>_<ON|OFF|NONE>.png`. The class comes from a host port of `ReconnectControlDetector`. Review the screenshots and their names by hand; the script's pass rule is a convenience, not the verdict.
- A cycle passes only when the adapter toggle worked, the before screenshot reads OFF, the app reports RECONNECTED, the link is back, and the after screenshot reads ON.
- **Faithfulness of the adapter toggle is not yet measured.** A pilot run (10 cycles on the test device) decides whether the adapter cycle is a faithful stand-in for a physical disconnect. If fewer than 10 of 10 cycles report `toggle_ok`, record that finding here and do not run the full matrix.

### Running the full proof

`make reconnect-matrix` runs the whole unattended proof on one device and takes hours. Variables: `SERIAL` (default `0A061FDD4000JF`), `MAC_SUFFIX` (`D3:24`), `DEVICE_SLUG` (`pixel5`).

1. Installs the **release** build (`installRelease`, 60 s loop, the shipped interval) signed with the local debug keystore, so it replaces an existing debug install without an uninstall. A debug build checks every 10 s and does not prove release cadence; the target aborts if the installed build is debuggable.
2. 12 already-on checks (companion connected, zero taps).
3. `auto` blocks a, b, c, d, e of 20 cycles each (5 x 20 per device).
4. `summary`: pass counts per block, then every failed cycle.

A failing cycle never stops the run; it is a `"pass": false` row in `scripts/reconnect-matrix/cycles.jsonl`. Before starting: the game open, the companion connected, Settings > Menus > Reconnect accessory enabled and both disclosures accepted.

## Guided path

On the Pikmin Bloom guided path the Reconnect accessory section in Settings > Menus is removed from the tree. The stored setting is untouched; switching path in Settings shows it again. Source: `GuidedPath.showsReconnectAccessory`.
