# Floating Joystick

A circular overlay on top of all apps. Drag it to move the spoofed location, release it to stop. The stick can be dragged from anywhere on the screen.

Key files: the joystick overlay service and the joystick view in `:feature:joystick:impl`.

## Requirements

- The overlay permission is required.
- The window must not take focus. A focusable overlay steals the keyboard from the app underneath. It must also not be modal, so touches outside the stick reach the app below.
- Overlay helpers are shared through the overlay module.
- The pad must read on both light and dark apps. The colours are tuned to be visible on a white screen without going solid on a dark one.

## Movement

- The stick's displacement sets the direction and the force. Pulling farther gives more speed. A small dead zone at the centre gives zero force.
- Releasing the stick eases it back to the centre and reports zero force.
- The new position is computed as a geographic offset from the current position, then pushed to the mock location service.

### Manual takeover

A new touch past the dead zone pauses automated movement at once. Walk-to, route replay and roaming pause, and keep their destination and progress. A road lookup already in flight may finish, but movement waits for an explicit Resume. Repeated input while paused does not restart anything.

For a follower, the same touch turns Follow leader off. The device stays in the group. See [group-sync.md](group-sync.md).

While paused, the stick can steer without changing the activity's mode. Releasing an unlocked stick leaves the activity paused until the user resumes it. A walk resumes toward its retained target. A route replay, roam or approach walk resumes by walking back to its retained waypoint from where the stick left the position: snapping back would be a teleport that Hide Teleport must prevent.

### Lock

Lock keeps the stick's direction after release.

- A touch in the dead zone stops manual stepping, but lock stays on. Pulling outward resumes locked movement.
- Only the lock button in the widget toggles lock. Dragging the overlay handle does not change lock or pause anything.
- A retained locked direction is ignored while a route, roam, walk or follower owns the position. It is also ignored while those sessions are paused, so a paused session does not move by itself.

## Cleanup

The overlay view is removed when the service is destroyed. The removal is guarded, because the view may already be gone.

## Edge cases

- Revoking the overlay permission while the stick is showing makes removal throw. Removal must be wrapped.
- Some vendor skins reset the overlay permission on reboot. The app should remind the user at startup.

## Visibility

The service tracks whether the stick is shown from the view's attach and detach callbacks, not from the return value of adding the view. Adding a view can return before the view is attached, so reading the attached state right after adding it gives a stale answer.
