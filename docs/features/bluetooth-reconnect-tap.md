# Bluetooth Reconnect Tap

**Status:** Implemented, off by default. The reliability proof is open (see "Reliability proof").

## Goal

Reconnect a Bluetooth companion device by automating the on-screen tap in the other app, with the lowest practical chance of detection. The tap is the only automated action. The Bluetooth connection itself is never driven through the Bluetooth API.

## Hard rule

Nothing is injected into, hooked into, patched in, or read from the target app's process or memory. Any approach that needs that is out of scope.

The only data taken from the target app is the pixels on screen, captured the same way as the tap-to-walk automatic fit ([tap-to-walk.md](tap-to-walk.md), "Screen access").

## Why this tap path

Four paths were considered:

- **Accessibility gesture dispatch:** chosen. The accessibility service is already enabled and disclosed for screenshots. A tap adds no permission prompt, no helper app, and no new entry in the enabled-services list. It is the only path that reaches the target app without injection, shell access or reading its view tree.
- **Shell or privileged input** (wireless debugging, a helper app, root): rejected. It needs a setup the app does not have, and a dependency outside the app. That conflicts with offline-first and no-setup use.
- **Node click** (performing a click on a view node): rejected. It reads another app's view tree, which the hard rule forbids. The game also exposes few or no nodes, so it would not work anyway.
- **Platform injection, automation instrumentation, or an overlay that passes touches through:** not possible for a normal app. Injection needs a permission only system apps hold. Instrumentation conflicts with an enabled accessibility service. An overlay cannot synthesise touches for another window.

The Bluetooth API reconnect is out of scope by definition of the task.

What the target app can observe on the chosen path: the enabled-services list (already non-empty on real devices, so this app's service adds nothing new), event flags on gesture-originated touches, the event's device and tool type, and the timing. Timing is the main lever, and the tap policy below controls it.

## Play policy

Gesture use under an accessibility service that does not declare itself an accessibility tool is a review risk. Mitigations: off by default, a separate opt-in switch, one disclosure that names the periodic screenshot, the tap and the game-only rule (enforced by Gate below), and no tap on any state other than a confident "off".

## Gate

A check (screenshot or tap) runs only while the screen is interactive and the foreground window belongs to the hardcoded game package (`AppConstants.GameConstants`). Without it the loop screenshotted whatever app was in front, which broke the narrow use the disclosure promises, and the shape-based detector could tap a lookalike control in another app. The foreground package comes from window-state-changed events, reading only the event's package name, never the view tree (hard rule above). The gate is re-checked before every capture and every tap, so a tap never lands after the user switched apps. Our Tap to Walk overlay also closes the reconnect gate: it takes every touch, so an injected gesture would land on it and walk the player. It does not close the foreground check that auto-fit uses, because learning runs while the overlay is up. A run cut short by the gate is neutral for the failure streak.

No extra event type or window-content capability is subscribed. On a Pixel 5 (Android 14) the game raises a window-state event after the shade closes, and the lock screen raises one from the system UI, so the foreground package already follows both. Other devices are unmeasured; if one raises no event, add a keyguard check. No picker: one game, no extra setup.

Limit: the foreground package is empty after a service start and stays empty until a window-state event arrives. After an app update, a crash or a service toggle while the game is already in front, the gate stays closed until the user switches away and back. Window-content access would remove this, but it is a Play review risk, so the limit stays and the wiki gives the workaround. The app cannot tell this stuck state from the user using another app, so after `ReconnectTapConstants.GATE_CLOSED_NOTIFY_MS` with the screen on, the notice in "Failure notification" tells the user to switch apps and back.

## Recognising the control

The control is located and classified from a screenshot, every run, with no fixed colours.

- The control is found by shape: two stacked half-discs with a dot across the split. Its size is a fraction of the short screen side, not the width. The target app sizes the control in dp, so a width fraction drops below the minimum on an unfolded foldable or a landscape tablet and the control is never found. The short side keeps the fraction stable on phones and wide screens. Measured on a synthetic 2560x1600 landscape canvas (control about 2.3% of the width, 3.8% of the short side): not found by width, found by short side. No real foldable or tablet recording was available.
- Its state comes from the colours in that same screenshot, so theme, brightness and tint do not matter. There is no per-device calibration.
- The result is one of on, off, or unknown. Unknown covers "not found" and any colour set that fits neither state. **Unknown never taps.**

The search region is the top-right of the screen, where the target app puts the control. The target app's HUD moves between versions, so the region must be re-measured when it changes. A moved HUD makes classification fail safely (unknown), but reconnect then stops until the region is updated.

## Tap policy

The policy exists to avoid a tap that turns a connected companion off.

- Tap only when the control reads off, confirmed by two screenshots a short time apart.
- After a tap, wait, then read the control several times. Stop as soon as it reads on. Tap again only when every read is still off.
- Take a fresh read right before each tap. A companion that connects during the pause must not be tapped away.
- A read that returns nothing is unknown, never "connected". Before the first tap it ends the run as `NOT_FOUND`. After a tap it keeps the poll going and never retaps, because the control may already read on. `FAILED` needs a delivered tap and no on read.
- A null read in the confirm phase (before any tap) waits once, then repeats both confirm reads. A popup that slides over the ball, or fades in, can hide the control for a moment. Only a second null ends the run as `NOT_FOUND`.
- A tap the system cancels or rejects (for example a finger on the screen) does not count. A run with no delivered tap ends as `GATE_CLOSED`, so it stays neutral.
- A small maximum number of taps per run. Abort and report after that.
- The pause, tap position and gesture duration are randomised within bounds, so taps do not repeat the same timing. Tune them only against reliability evidence.

## Reliability proof

**Result: incomplete.** One device passes on the current build; the multi-device target is open. Open items are in the `reconnect-stable` backlog epic.

Current build, 2026-11-02, Pixel 5 with the original Pokemon GO Plus, debug build:

- Block a, tap disconnect: 19/20. The fail was a connect that showed ON 16 s after the tap, after the post-tap read window had closed.
- Already on: 12/12 checks with zero taps.
- Block c, screen off: 10/10 reconnected, but with zero taps. The game reconnects by itself when it resumes, so this block does not test the tap.
- A typical connect reads ON 8 to 9 s after the tap.
- A retap while the game still connects did not cancel the connect (6/6). A slow connect therefore costs a longer read window, not a lost link.
- While the game searches, it draws a ring around the ball, and the detector reads OFF. Taps during the game's own search right after a disconnect often do nothing.
- After Recents and then Back, the gate stayed closed in some attempts (it never received a new window event from the game) until the game was relaunched. The gate-closed notice in "Failure notification" covers this case.

Earlier runs (2026-10-13, Pixel 7 rows included) predate the re-read logic and the null-read fixes. Their taps-while-on failures do not apply to the current build. The target is still at least three devices, and Pokemon GO Plus + is untested.

The earlier failures were diagnosed as a timing fault, not a detection fault. The first tap run read the control too soon after a tap, so a slow connection looked like a failure and was tapped again, toggling the companion away. The fix is the repeated read after a tap.

The proof is run by the reconnect matrix script in `scripts/`. Its raw rows are not committed. Each row stores the tap-to-link and tap-to-services seconds, read from the phone's Bluetooth history, because the per-cycle `logcat -c` erases the log.

## Backoff

A `FAILED` run means the tap did not bring the companion back, so it is probably away. `ReconnectBackoff` doubles the wait after each `FAILED`, from the base interval up to `MAX_CHECK_INTERVAL_MS`. Retrying at the base rate would repeat taps and screenshots that cannot succeed.

- `RECONNECTED` and `NOT_OFF` reset the wait to base: the companion is connected, or the control is not off.
- `NOT_FOUND` and `GATE_CLOSED` keep the wait, the same neutral rule as the streaks.
- A loop restart (setting toggled) keeps the wait too. The state lives in the service, in memory only.
- Debug builds never back off, because the reliability matrix needs a steady short cycle.

## Failure notification

The check runs in the background, so the user cannot see it fail. `ReconnectFailureStreak` counts consecutive `FAILED` runs and tells the service to post one notification per streak. `NOT_OFF` means the control read on, so it re-arms the streak like `RECONNECTED`: a user who reconnects by hand must not carry old failures into the next outage. `NOT_FOUND` and `GATE_CLOSED` stay neutral for the failure streak. `ReconnectGateClosedStreak` covers a closed gate: after `ReconnectTapConstants.GATE_CLOSED_NOTIFY_MS` of consecutive `GATE_CLOSED` results with the screen on, the service posts one notice on the same channel. Its text names both causes (the game not in front, or Tap to Walk on) because the gate cannot tell them apart from the user using another app. A closed result with the screen off, or any other result, ends the streak; any result other than `GATE_CLOSED` clears the notice, but a screen-off closed result leaves it up. All streaks reset when the loop turns off. The service cancels the failed notice on `RECONNECTED` or `NOT_OFF`, and the not-found notice whenever the control is found again. Turning the loop off or unbinding cancels all three, so no stale notice outlives its cause. The streak is in memory only; a process restart at worst delays one notification. `ReconnectNotFoundStreak` covers the case NOT_OFF hides: the game is in front (the gate) yet the first read finds no control for `NOT_FOUND_NOTIFY_THRESHOLD` checks in a row, which means the HUD moved or the colours changed and reconnect has silently stopped. It posts one notification per streak on the same channel; any other result, including a closed gate, re-arms it. Also in memory only. The notification has its own channel because the foreground-service channels are silent or hidden.

## Status line

The widget's Debug stats block shows the last check's result because the loop is invisible otherwise. It appears only while Debug stats and Reconnect accessory are both on. It travels through `ScreenCaptureSource` (the existing service-to-core bridge), in memory only: a persisted "last check" would mislead after a reboot, so it clears when the loop stops or the service unbinds. `GATE_CLOSED` is its own result so "game not in front" is not read as "connected"; both streaks treat it as neutral.

## Open work

- Prove reliability on real devices: repeated disconnect and reconnect cycles, with the adapter-toggle faithfulness measured first.

## Open risks

- Play review of the gesture capability (see above).
- A popup that is already open and static, with the control still visible, reads OFF on both confirm reads and can be tapped. The detector reads a dimmed control as OFF by design, and no popup detector is planned.
- Target app changes: a HUD move or colour change needs the region re-measured. Unknown prevents false taps, but reconnect stops until fixed; the not-found notification (see "Failure notification") tells the user.

Both features share one screenshot budget ([tap-to-walk.md](tap-to-walk.md)): every screenshot is serialized and spaced, because the platform rejects shots taken too close together. Reconnect reads only the detector's search region, off the main thread.

## Guided path

On the path that hides it, the Reconnect accessory section is hidden. The stored setting is untouched. See [guided-paths.md](guided-paths.md).
