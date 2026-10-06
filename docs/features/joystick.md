# Floating Joystick

Circular overlay atop all apps. Drag = move spoofed location. Release = stop. Drag anywhere on screen.

Key files: `:feature:joystick:impl/JoystickOverlayService.kt`, `:feature:joystick:impl/JoystickView.kt`

## Requirements

- `SYSTEM_ALERT_WINDOW` required.
- Uses `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` (mandatory — prevents stealing keyboard focus from foreground app) and `FLAG_NOT_TOUCH_MODAL`.
- Overlay utils shared via `:core:overlay`.
- Outer disc is a light fill at `JoystickConstants.OUTER_ALPHA` (128) so it reads on light apps without going solid on dark ones. The outer ring is a medium gray (`OUTER_BORDER_RGB`) so the pad is visible on a white screen. The stick stays light with a slightly darker gray edge (`KNOB_EDGE_RGB`).

## Movement

- Drag → direction vector × speed (m/s) × force. Pulling farther increases speed; the central 15% is a dead zone.
- Release eases the knob back to center (180 ms overshoot) then reports zero force.
- New lat/lon via Haversine.
- Pushed to `MockLocationService`.
- Overlay reposition: `View.OnTouchListener` → `WindowManager.LayoutParams`.
- **Manual takeover**: a new touch past the dead zone calls `MapController.pauseAutomatedMovement()` synchronously. Walk-to, route replay, and roaming/planting pause with their destination and progress retained. Pending road lookups may finish, but movement waits for explicit Resume. Repeated input while paused does not restart the activity. A follower instead has that same call turn **Follow leader** off and stays in the group; following has no route pause/resume session.
- While paused, the joystick can steer without changing the activity's mode. Releasing an unlocked stick stops manual movement and leaves the activity paused; the existing Resume control restarts it. A walk resumes from the current position toward its retained target. Choosing another map destination replaces the old walk and starts moving, including when the target coordinates are the same.
- **Lock at center**: a touch or drag into the dead zone reports zero force, so manual stepping stops while lock stays enabled. Pulling outward resumes locked movement; releasing outside the dead zone retains the direction. Only the widget lock button toggles locking. Dragging the overlay handle does not unlock or pause an activity.
- Without a new touch, a retained locked direction is ignored while a route, roam, walk-to, or follower owns the tick. Paused walk-to, route, and roam sessions yield to the joystick (`shouldIgnoreJoystickInput`). The stick fades while input is ignored. While spoofing is active, the widget lock and joystick icons are green while the stick is moving (`MockMode.JOYSTICK`) and `WidgetInactiveTint` grey otherwise; the lock glyph still shows locked vs unlocked. The widget joystick icon shows/hides the overlay independently; lock still shows-then-locks a hidden overlay.

## Cleanup

Call `windowManager.removeView` in `onDestroy`, null/attached check.

## Edge Cases

- Revoke `SYSTEM_ALERT_WINDOW` while showing → `removeView` throws. Wrap in try/catch.
- MIUI/ColorOS: overlay perms reset on reboot. Show startup reminder.

## Visibility synchronization

`JoystickOverlayService.isVisible` follows the overlay view's attach/detach callbacks. `WindowManager.addView()` may return before attachment, so reading `isAttachedToWindow` immediately after showing can leave the widget stale. The callbacks also cover base overlay show/hide broadcasts. `JoystickVisibilityTest` exercises delayed attachment, repeated show/hide, and those base commands.
