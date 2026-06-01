# Elevation Controls (Experimental)

Injects synthetic accelerometer and rotation-vector sensor events to make apps see the phone as tilted. Useful for apps that behave differently based on device orientation.

Key files: `core/location/src/main/kotlin/com/locationjoystick/core/location/SensorInjector.kt`, `core/common/src/main/kotlin/com/locationjoystick/core/common/root/RootCapabilityChecker.kt`

## Root Requirement

`android.permission.INJECT_EVENTS` is a signature-level permission not grantable to third-party apps normally. On rooted devices, `SensorPermissionBootstrap` grants it via:

```
su -c pm grant <packageName> android.permission.INJECT_EVENTS
```

Root detection (`RootCapabilityChecker`) runs `su -c id` and checks for exit code 0. Both checks run on `Dispatchers.IO`. The Settings UI shows a root status badge and disables the Elevation Controls toggle when root is not detected.

## ElevationMode States

| State | Meaning |
|---|---|
| `Neutral` | Flat — gravity vector points straight down (z-axis only) |
| `TiltUp` | Phone pitched forward — negative Y component added to gravity |
| `TiltDown` | Phone pitched backward — positive Y component added to gravity |

Controlled from the floating widget via a 3-button column (↑ / ○ / ↓). State is held in `_elevationMode: MutableStateFlow<ElevationMode?>` in `FloatingWidgetService` and relayed to `MockLocationService.setElevationMode()` via the bound service reference.

## Integration Point

`MockLocationService.pushLocationUpdate()` calls `SensorInjector.inject()` after `applyToProvider()` on every tick (1 Hz) when `elevationControlsEnabled == true` and `currentElevationMode != null`.

## SensorInjector

Uses reflection to call `SensorManager.injectSensorData(Sensor, FloatArray, Int, Long)` — a hidden API present on AOSP builds. Discovery is lazy and cached:

```kotlin
SensorManager::class.java.getDeclaredMethod("injectSensorData", ...)
    .also { it.isAccessible = true }
```

If the method is absent (`NoSuchMethodException`), `injectMethod` is `null` and all injection silently no-ops. Two sensor types are injected per tick:

- `TYPE_ACCELEROMETER`: gravity vector decomposed by tilt angle + per-axis Gaussian noise
- `TYPE_ROTATION_VECTOR`: quaternion matching the tilt angle (X-axis rotation only)

## Noise Model

Each injected value adds per-axis noise sampled from `Uniform(-NOISE_AMPLITUDE_MS2, +NOISE_AMPLITUDE_MS2)` (default ±0.35 m/s²). The tilt angle itself also receives jitter of `±TILT_JITTER_DEGREES` (default ±2.5°) before the gravity decomposition, simulating natural hand tremor.

## Constants (`AppConstants.ElevationConstants`)

| Constant | Value | Purpose |
|---|---|---|
| `DEFAULT_TILT_DEGREES` | 45° | Default slider position |
| `MIN_TILT_DEGREES` | 20° | Slider lower bound |
| `MAX_TILT_DEGREES` | 75° | Slider upper bound |
| `NOISE_AMPLITUDE_MS2` | 0.35 m/s² | Per-axis accelerometer noise |
| `TILT_JITTER_DEGREES` | 2.5° | Random tilt variation per tick |
| `GRAVITY` | 9.80665 m/s² | Standard gravity constant |

## Anti-Patterns to Avoid

- Do not call `SensorManager.registerListener()` — injection bypasses the listener pipeline entirely.
- Do not hold a wakelock for injection — the 1 Hz tick from `MockLocationService` is sufficient.
- Do not inject sensors when `elevationControlsEnabled == false` or `currentElevationMode == null` — both guards are checked before calling `SensorInjector.inject()`.
- Do not cache the `Method` reference across process restarts — the lazy property handles this correctly.
