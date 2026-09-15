# Technical Constraints

- Min SDK API 28. `MockLocationService.setupTestProvider()` gates on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`: the `ProviderProperties.Builder()` path on API 31+, the deprecated raw-arg `addTestProvider` overload (`@Suppress("DEPRECATION")`) below it. Compass orientation tracking (see @docs/features/tap-to-walk.md) requires API 30 (`takeScreenshot`) with no fallback — its Settings row and `CompassAccessibilityService` binding are both gated on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R`, effectively unavailable on API 28–29.
- No Play Services. MapLibre, not Google Maps. No Firebase.
- Offline-first. Core features work without internet. OSRM opt-in, degrades gracefully.
- No `Thread.sleep()`. Use `delay()` in coroutines.
- No empty catch blocks. Every `catch` must log or handle.
- No `GlobalScope`. Use `viewModelScope`, `lifecycleScope`, or scoped `CoroutineScope`.
- No memory leaks. Every `WindowManager.addView` needs matching `removeView` in `onDestroy`. Every scope cancelled in `onDestroy`/`onCleared`.
- Location updates at 1 Hz.
- Battery: use `IMPORTANCE_LOW` notification channel. Wake locks used only when necessary for essential background operations. `MockLocationService` holds `PARTIAL_WAKE_LOCK` while spoofing is active (state != IDLE) — foreground service alone doesn't guarantee the `delay()`-based update loop fires reliably under Doze/Adaptive Battery throttling on some devices (e.g. Android 15 Pixel) once the screen locks. Released on stop/idle/onDestroy.
- OSM tiles require an identifying `User-Agent` and MapLibre's per-host download cap. The full required recipe (install path, interceptor replace, R8 keeps, cache wipe, 20 requests/host, overlay TextureView) is @docs/features/map-tiles.md. Do not invent a second `MapLibre.getInstance` / `MapView` path.