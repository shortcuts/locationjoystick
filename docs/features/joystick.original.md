# Floating Joystick

Circular overlay on top of all apps. Drag to move the spoofed location. Release to stop. Draggable anywhere on screen.

Key files: `:feature:joystick:impl/JoystickOverlayService.kt`, `:feature:joystick:impl/JoystickView.kt`

## Requirements

- Requires `SYSTEM_ALERT_WINDOW` permission.
- Uses `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` (mandatory — prevents stealing keyboard focus from foreground app) and `FLAG_NOT_TOUCH_MODAL`.
- Overlay utilities shared via `:core:overlay`.

## Movement

- Drag input is normalized to a direction vector, multiplied by speed (m/s).
- New lat/lon computed via Haversine formula.
- Result pushed to `MockLocationService`.
- Drag-to-reposition the overlay itself uses `View.OnTouchListener` updating `WindowManager.LayoutParams`.

## Cleanup

Must call `windowManager.removeView` in `onDestroy` with a null/attached check.

## Edge Cases

- Revoking `SYSTEM_ALERT_WINDOW` while the overlay is showing → `removeView` throws. Wrap in try/catch.
- MIUI/ColorOS: overlay permissions reset on reboot. Show a reminder on startup.
