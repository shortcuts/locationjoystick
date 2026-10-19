# Mock Location Engine

Injects fake GPS into Android. Every app on the device receives the spoofed position as if it were real GPS.

Key files: the location service and the location repository in `:core:location` and `:core:data`.

## Global start and stop

Every screen's top bar shows one start/stop control, so spoofing can be driven from anywhere in the app, not only from the map. The control's label shows the place name when spoofing is off, so the user can see where a start would put them.

The control uses an immediate tap. It must not use a long press. A long press waits out the press timeout before it fires, so a firm first tap would show a toast instead of starting. The place name is already on the button, so a long-press toast would be redundant.

The control reads the spoofing state from one controller method. Feature view models do not depend on the controller for this, so a screen does not need the whole map dependency just to show the button.

## Core mechanics

- The position is pushed once per second, matching the cadence of real GPS.
- A teleport pushes one fix at once, so the map and other apps do not wait up to a second for the new position. Walking and joystick ticks stay on the one-second loop.
- On stop, the test provider must be removed. Leaving it behind is a ghost provider that breaks real GPS until the next reboot.
- Another app may hold the mock slot. The start must catch that and show a clear error rather than crash. The same failure mid-session (the user changes the mock app) must also surface as the error state and stop the loop, because swallowing it leaks the real GPS to other apps.
- The elapsed-time stamp must increase on every fix. A fixed value makes apps discard the fixes.
- Accuracy must stay within a range. A value below the minimum is a known anti-cheat signal.

## Internal architecture

Each tick takes an immutable snapshot of the state. Building the fix from the snapshot, not from live fields, prevents a tick from mixing values written by two threads. The clock is read once per tick, and that same time is used for every value in the fix.

Building the fix is a pure function: it takes the snapshot and a random source, and returns a fix. It has no platform imports, so the logic runs in host unit tests with a fixed seed. Converting the fix to the platform type and pushing it is a separate step.

The steps that shape one fix run in this order. The order matters, because later steps depend on earlier ones:

1. Suspended-phase check (forces a stationary, unjittered fix for the tick).
2. Altitude walk.
3. Bearing hold.
4. Position jitter.
5. Warm-up accuracy envelope.
6. Accuracy perturbation.
7. Bearing accuracy.
8. Satellite extras.

The suspended-phase transitions are a pure function, so they can be tested without the service.

## Realism

Realism is a set of independent switches. Each one makes the fake fix look more like a real GPS fix, so apps that check for a perfectly static or perfectly exact position do not flag it. Defaults are in the settings model. Tuning values are in `AppConstants.RealismConstants`.

- **Bearing hold:** while stationary, report the last real heading, not zero. Real GPS keeps its last heading when it stops.
- **No bearing before first motion:** real GPS reports no heading until it has a fix with motion. The fix does the same, and keeps reporting heading after the first motion, even when still.
- **Dynamic bearing accuracy:** heading accuracy is poor when stopped (heading is undefined) and tightens as speed rises. A fixed value would look like a fake.
- **Altitude drift:** each tick draws a fresh offset around the base altitude. A running random walk drifted far beyond the configured radius, so the offset is not accumulated between ticks. A clamp keeps it within the radius.
- **Warm-up:** accuracy starts poor and converges over about half a minute. It starts once per session and does not restart on pause.
- **Satellite extras:** the fix carries satellite counts that change slowly.
- **Suspended mocking:** alternates normal movement with a stationary period, to mimic a device that loses and regains a fix. The fix is still pushed every second during the stationary period, so it never goes stale. Turned off in route and walk modes, where the movement is driven by the route.
- **Idle speed wobble:** most idle ticks report exactly zero speed, as real GPS does. A small share report a small wobble. The wobble is tied to its own setting, not to the speed profile, so changing the profile does not change idle noise.
- **Position jitter:** the offset wanders inside a radius, by small steps in a fresh direction each tick. It must not jump to a random point and back. A beeline to one target per leg looked like a bounce off the edge of the radius, so the heading is redrawn every tick. While moving, the disc is stretched along the direction of travel, because real GPS error is larger along the track.

### Why the stale-fix rule applies to every pause

Pausing a route, a walk or the spoofing must keep pushing a fix. Apps that see no fix for a while fall back to the real GPS, which exposes the real position. So every pause keeps the last position alive on the provider.

## Real elevation

Altitude is anchored to the real ground elevation at the spoofed position, from a free elevation service, instead of a flat default. A flat default looks wrong when the user is on a hill.

- The lookup runs on start and after a teleport. Both use an immediate jump, because nothing has been reported at the new position yet, so there is no jump for the user to see.
- Periodic lookups during a session move the altitude gradually, a few tenths of a metre per tick. A sudden change between two reported fixes would be a visible jump to an app that checks altitude.
- Lookups are cached per cell of roughly 100 metres, since ground elevation is static. A failed lookup is never cached.
- The fetched value gets a small sub-metre offset. The service returns whole metres, and a round number would be a tell.
- A manual altitude override wins over the lookup and suspends it until the user resets it.
