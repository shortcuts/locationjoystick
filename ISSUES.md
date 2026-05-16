# ISSUES.md

This document references known issues for agents to pick them and iterate on

## Routes not cleared from map

When a route ends (either because the last point have been reached, or the user stopped it), the points and lines should be removed from the mapscreen / mapfloatingview, only leaving the current position point visible. Implement the fix from plan ~/.claude/plans/when-a-route-ends-expressive-shell.md

## App crashes after route ends

Observed when replaying a route, when the last point is reached, the spoofing is stopped. Then if I try to start spoofing again it works, but if I stop it, I can't start it anymore it remains stuck

05-16 23:32:30.428 23123 24544 I RouteReplayEngine: Replay started: 6 waypoints at 1.4m/s looping=false
05-16 23:39:43.695 23123 23903 I MockLocationService: Test provider removed
05-16 23:39:43.697 23123  3873 D LocationRepository: stopSpoofing requested
05-16 23:39:43.697 23123 24544 I MockLocationService: Overlay services stopped
05-16 23:39:43.698 23123 23903 I MockLocationService: Spoofing stopped
05-16 23:39:43.803 23123 23123 W WindowOnBackDispatcher: sendCancelIfRunning: isInProgress=falsecallback=android.view.ViewRootImpl$$ExternalSyntheticLambda17@cb9e093
05-16 23:39:43.824 23123 23123 D FloatingWidgetService: Overlay view removed from WindowManager
05-16 23:39:43.850 23123 23123 W WindowOnBackDispatcher: sendCancelIfRunning: isInProgress=falsecallback=android.view.ViewRootImpl$$ExternalSyntheticLambda17@5a69072
05-16 23:39:43.874 23123 23123 D JoystickOverlayService: Overlay view removed from WindowManager
05-16 23:39:43.879 23123 23904 D LocationRepository: stopSpoofing requested
05-16 23:39:43.880 23123 23123 I MockLocationService: Spoofing stopped
05-16 23:39:43.881 23123 23904 E MockLocationService: currentPosition flow error
05-16 23:39:43.881 23123 23904 E MockLocationService: kotlinx.coroutines.JobCancellationException: Job was cancelled; job=SupervisorJobImpl{Cancelling}@8b0d24e


## Speed issue

The current speed isn't respected, it seems like we are going **at least** 2 times faster than we should, from the GPS speedometer app, when setting 2km/h, we are actually going at 5km/h. From the log when starting a route we can see 05-16 23:32:30.428 23123 24544 I RouteReplayEngine: Replay started: 6 waypoints at 1.4m/s looping=false, which means we are not setting the speed properly from the user settings? Investigate the issue

