# External Teleport (Pokemod command)

An outside app teleports the user through the command it already sends to GPS Joystick:

```
am start-foreground-service -a theappninjas.gpsjoystick.TELEPORT --ef lat $lat --ef lng $lng
```

Key files: `ExternalTeleportService`, `ExternalTeleportDecision` (the pure gating), the service entry in the `:core:location` manifest.

## Why a separate exported service

- `MockLocationService` also takes stop, follower and leader actions. Exporting it would hand those to every app. It stays `exported=false`.
- The sender uses `start-foreground-service`, which resolves only a service. A receiver does not work.
- The shim does no teleport work itself. It checks the command, then calls `TeleportUseCase` (running) or `MockLocationServiceStarter` (idle), so persistence, cooldown and history stay in one place.

## Why the component state is the gate

- A service has no caller check, and `am` sends no key. There is no way to authenticate the sender.
- The only gate is whether Android can resolve the action. The manifest ships the service with `enabled=false`, so the action does not resolve until the app enables the component. Play builds carry no live exported entry point by default.
- The same gate fixes the clash with GPS Joystick: two apps that resolve one action make Android pick one or show a chooser. Off by default means this app never competes unless the user opts in.

## Why both action strings

The literal `theappninjas.gpsjoystick.TELEPORT` works with the sender today. The app's own `com.locationjoystick.app.TELEPORT` is ready for a sender that makes the action configurable. The service also checks the action in code, because an explicit `-n` intent skips the manifest filter.

## Command rules

- Idle: start spoofing at the point.
- Running: teleport. Follower mode is not special-cased, same as any map teleport.
- Paused and error: ignore and log. A pause is deliberate, and an error must clear first.
- Hide teleport on: ignore in every state. The idle-start exception in [hide-teleport.md](hide-teleport.md) covers user taps, not outside commands.
- A missing, NaN, infinite or out-of-range coordinate: ignore and log, in every state.
- `--ef` sends Float extras, and `getDoubleExtra` returns its default for a Float. The service reads the boxed number of either type. A Float keeps about 1 m of precision, which is the sender's limit.

## Notification

`start-foreground-service` forces a foreground start, so the shim posts a minimum-importance notice under its own ID. A shared ID would let the shim's stop remove the real notification. A separate ID avoids that, and the device run confirmed the real one survives. The shim creates the channels first, because the real service never ran if spoofing was idle.

## Limits

- The foreground start comes from outside the app. A start from the shell is allowed in the background, and a cold start with the app not running worked. A sender with no background exemption may be refused by Android 14 and later. The service catches that refusal, logs it and stops.
- The toggle that enables the component, and its persistence, are a separate entry. Until it lands, enable the component with `pm enable` on a test phone.
