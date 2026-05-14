# ISSUES.md

This document references known issues for agents to pick them and iterate on

## silent crash

05-14 23:42:24.071 23545 23559 E OpenGLRenderer: Unable to match the desired swap behavior.
05-14 23:42:25.066 23545 23563 W Parcel  : Expecting binder but got null!
05-14 23:42:25.095 23545 23559 E OpenGLRenderer: Unable to match the desired swap behavior.
05-14 23:44:04.249 23545 23545 W WindowOnBackDispatcher: sendCancelIfRunning: isInProgress=falsecallback=android.view.ViewRootImpl$$ExternalSyntheticLambda17@70d0427
05-14 23:44:04.272 23545 23545 W InputEventReceiver: Attempted to finish an input event but the input event receiver has already been disposed.
05-14 23:44:05.625 23545 23559 E OpenGLRenderer: Unable to match the desired swap behavior.
05-14 23:44:07.653 23545 23559 E OpenGLRenderer: Unable to match the desired swap behavior.
05-14 23:44:08.239 23545 23559 E OpenGLRenderer: Unable to match the desired swap behavior.
05-14 23:44:44.351 23545 24317 I MockLocationService: Test provider removed
05-14 23:44:44.354 23545 24032 D LocationRepository: stopSpoofing requested
05-14 23:44:44.354 23545 24317 I MockLocationService: Spoofing stopped
05-14 23:44:44.359 23545 24033 I MockLocationService: Overlay services stopped
05-14 23:44:44.385 23545 23545 W WindowOnBackDispatcher: sendCancelIfRunning: isInProgress=falsecallback=android.view.ViewRootImpl$$ExternalSyntheticLambda17@7f650a2
05-14 23:44:44.396 23545 23545 D FloatingWidgetService: Overlay view removed from WindowManager
05-14 23:44:44.415 23545 23545 W WindowOnBackDispatcher: sendCancelIfRunning: isInProgress=falsecallback=android.view.ViewRootImpl$$ExternalSyntheticLambda17@8eb791d
05-14 23:44:44.429 23545 23545 D JoystickOverlayService: Overlay view removed from WindowManager
05-14 23:44:44.432 23545 24033 D LocationRepository: stopSpoofing requested
05-14 23:44:44.432 23545 23545 I MockLocationService: Spoofing stopped

## route and favorite floating screen

when clicking the route or favorite icon from the floating overlay, it opens a floating window. that window should be with the black background and white text from LjColors. The window should have a padding all around the screen edges to represent the floating effect.

Favorite specifics:
- list every favorites
- add button to:
    - teleport to a favorite
    - walk to a favorite
    - add favorite from current location

Route specifics:
- route is running/being replayed/ongoing:
    - on the right of the route floating icon (if not enough space, open on the left):
        - pause (the location doens't change anymore, can be resume)
        - stop (the route is stopped, can't be resumed, location stays here)
- route is not running
    - opens the window and list every routes
    - add buttons to
        - start a route
        - start in reverse a route
        - create route from map (same as in the Route screen)
