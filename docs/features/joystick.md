# Floating Joystick

Circular overlay atop all apps. Drag = move spoofed location. Release = stop. Drag anywhere on screen.

Key files: `:feature:joystick:impl/JoystickOverlayService.kt`, `:feature:joystick:impl/JoystickView.kt`

## Requirements

- `SYSTEM_ALERT_WINDOW` required.
- Uses `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` (mandatory — prevents stealing keyboard focus from foreground app) and `FLAG_NOT_TOUCH_MODAL`.
- Overlay utils shared via `:core:overlay`.

## Movement

- Drag → direction vector × speed (m/s).
- New lat/lon via Haversine.
- Pushed to `MockLocationService`.
- Overlay reposition: `View.OnTouchListener` → `WindowManager.LayoutParams`.

## Cleanup

Call `windowManager.removeView` in `onDestroy`, null/attached check.

## Edge Cases

- Revoke `SYSTEM_ALERT_WINDOW` while showing → `removeView` throws. Wrap in try/catch.
- MIUI/ColorOS: overlay perms reset on reboot. Show startup reminder.