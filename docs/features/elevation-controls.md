# Elevation Controls

Injects synthetic accelerometer and rotation-vector sensor events to make apps see the phone as tilted. Useful for apps that behave differently based on device orientation.

Key files: `core/location/src/main/kotlin/com/locationjoystick/core/location/SensorInjector.kt`, `core/common/src/main/kotlin/com/locationjoystick/core/common/root/RootCapabilityChecker.kt`

## Settings

Elevation Controls is a widget feature — it appears in the **Floating Widget** section of Settings alongside other widget features (Map shortcut, Joystick toggle, etc.). It participates in the standard save/discard top-bar pattern. The row is disabled (greyed out) when root is not detected.

The "enabled" source of truth is whether `WidgetFeature.ELEVATION_CONTROLS` is present in the saved widget features set. There is no separate DataStore key for elevation controls enabled state.

## Tilt Angle

The tilt angle is fixed at `AppConstants.ElevationConstants.DEFAULT_TILT_DEGREES` (45°). It is not user-configurable. Jitter of `±TILT_JITTER_DEGREES` (±2.25°, which is 5% of 45°) is applied per tick to simulate natural hand tremor.

## Root Requirement

`android.permission.INJECT_EVENTS` is a signature-level permission not grantable to third-party apps normally. On rooted devices, `SensorPermissionBootstrap` grants it via:

```
su -c pm grant <packageName> android.permission.INJECT_EVENTS
```

Root detection (`RootCapabilityChecker`) runs `su -c id` and checks for exit code 0. Both checks run on `Dispatchers.IO`. The Settings UI disables the Elevation Controls toggle when root is not detected.

The permission grant is triggered inside `SettingsViewModel.saveChanges()` when `ELEVATION_CONTROLS` is being added to the widget features set.

## ElevationMode States

| State | Meaning |
|---|---|
| `Neutral` | Flat — gravity vector points straight down (z-axis only) |
| `TiltUp` | Phone pitched forward — negative Y component added to gravity |
| `TiltDown` | Phone pitched backward — positive Y component added to gravity |

Controlled from the floating widget via a 3-button column (↑ / ○ / ↓). State is held in `_elevationMode: MutableStateFlow<ElevationMode?>` in `FloatingWidgetService` and relayed to `MockLocationService.setElevationMode()` via the bound service reference.

## Integration Point

`MockLocationService.pushLocationUpdate()` calls `SensorInjector.inject()` after `applyToProvider()` on every tick (1 Hz) when `elevationControlsEnabled == true` and `currentElevationMode != null`. `elevationControlsEnabled` is derived by observing `settingsRepository.getWidgetFeatures()` and checking whether `ELEVATION_CONTROLS` is present.

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

Each injected value adds per-axis noise sampled from `Uniform(-DEFAULT_NOISE_AMPLITUDE_MS2, +DEFAULT_NOISE_AMPLITUDE_MS2)` (default ±0.35 m/s²). The tilt angle receives jitter of `±TILT_JITTER_DEGREES` (±2.25°) before the gravity decomposition, simulating natural hand tremor.

## Constants (`AppConstants.ElevationConstants`)

| Constant | Value | Purpose |
|---|---|---|
| `DEFAULT_TILT_DEGREES` | 45° | Fixed tilt angle used for injection |
| `NOISE_AMPLITUDE_MS2` | 0.35 m/s² | Per-axis accelerometer noise |
| `TILT_JITTER_DEGREES` | 2.25° | Random tilt variation per tick (5% of 45°) |
| `GRAVITY` | 9.80665 m/s² | Standard gravity constant |

## Anti-Patterns to Avoid

- Do not call `SensorManager.registerListener()` — injection bypasses the listener pipeline entirely.
- Do not hold a wakelock for injection — the 1 Hz tick from `MockLocationService` is sufficient.
- Do not inject sensors when `elevationControlsEnabled == false` or `currentElevationMode == null` — both guards are checked before calling `SensorInjector.inject()`.
- Do not cache the `Method` reference across process restarts — the lazy property handles this correctly.
- Do not add a separate DataStore key for elevation enabled state — use `WidgetFeature.ELEVATION_CONTROLS` presence in widget features as the source of truth.
