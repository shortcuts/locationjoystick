# ISSUES.md

This document references known issues for agents to pick them and iterate on

## app crashing silently

--------- beginning of system
05-14 22:50:02.403  9847  9847 W VRI[PopupWindow:56577ba]: Dropping event due to root view being removed: MotionEvent { action=ACTION_MOVE, actionButton=0, id[0]=0, x[0]=700.0, y[0]=-44.0, toolType[0]=TOOL_TYPE_FINGER, buttonState=0, classification=AMBIGUOUS_GESTURE, metaState=0, flags=0x800, edgeFlags=0x0, pointerCount=1, historySize=0, eventTime=1430228, downTime=1430217, deviceId=4, source=0x1002, displayId=0, eventId=656390112 }
--------- beginning of main
05-14 23:20:48.233  9847  9861 E OpenGLRenderer: Unable to match the desired swap behavior.
05-14 23:20:50.227  9847  9861 E OpenGLRenderer: Unable to match the desired swap behavior.
05-14 23:21:03.214  9847 10139 D LocationRepository: stopSpoofing requested
05-14 23:21:03.218  9847 10140 I MockLocationService: Test provider removed
05-14 23:21:03.218  9847 10140 I MockLocationService: State changed to IDLE/ERROR - stopped update loop
05-14 23:21:03.224  9847 10140 I MockLocationService: Overlay services stopped
05-14 23:21:03.262  9847  9847 W WindowOnBackDispatcher: sendCancelIfRunning: isInProgress=falsecallback=android.view.ViewRootImpl$$ExternalSyntheticLambda17@f8ae2b7
05-14 23:21:03.281  9847  9847 D FloatingWidgetService: Overlay view removed from WindowManager
05-14 23:21:03.301  9847  9847 W WindowOnBackDispatcher: sendCancelIfRunning: isInProgress=falsecallback=android.view.ViewRootImpl$$ExternalSyntheticLambda17@c0cd882
05-14 23:21:03.313  9847  9847 D JoystickOverlayService: Overlay view removed from WindowManager


## route and favorite creation from map

we should see our current location on the map when trying to create a new favorite or a new route from the map screen

## move speed

it seems like our implementation doesn't properly respect the speed and/or meter per seconds, i've compared with other apps and we seem to move faster (i'd say around 2 times faster) at the same km/h defined in the settings config, I'm comparing with GPS Joystick, we can take inspiration of open source apps such as: https://github.com/henryfung0/fake-gps-android/ https://github.com/mcastillof/faketraveler https://github.com/brutalharsh/mock-location-app https://github.com/noobexon1/XposedFakeLocation

## gps jitter

in order to avoid triggering the anti cheat behavior detection of certain apps and games (such as pokemon go) we should add a small gps jitter, other apps provide such feature: https://github.com/henryfung0/fake-gps-android/ https://github.com/mcastillof/faketraveler https://github.com/brutalharsh/mock-location-app https://github.com/noobexon1/XposedFakeLocation
