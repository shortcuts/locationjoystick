# Technical Constraints

- Min SDK API 28. The mock provider is registered through two code paths, split by API level. The newer API needs a builder; the older one uses the deprecated call. Screen capture (Tap to Walk, Reconnect accessory) needs API 30 and has no fallback, so the service is disabled in the manifest below that level. See docs/features/tap-to-walk.md.
- No Play Services. MapLibre, not Google Maps. No Firebase.
- Offline-first. Core features work without internet. OSRM opt-in, degrades gracefully.
- No `Thread.sleep()`. Use `delay()` in coroutines.
- No empty catch blocks. Every `catch` must log or handle.
- No `GlobalScope`. Use `viewModelScope`, `lifecycleScope`, or scoped `CoroutineScope`.
- No memory leaks. Every `WindowManager.addView` needs matching `removeView` in `onDestroy`. Every scope cancelled in `onDestroy`/`onCleared`.
- Location updates at 1 Hz.
- Battery: use `IMPORTANCE_LOW` notification channel. Wake locks used only when necessary for essential background operations. The mock service holds a partial wake lock while spoofing is active. A foreground service alone does not keep the update loop firing under Doze or adaptive battery on some devices once the screen locks. See docs/features/foreground-service.md.
- OSM tiles require an identifying `User-Agent` and MapLibre's per-host download cap. The full required recipe (install path, interceptor replace, R8 keeps, cache wipe, 20 requests/host, overlay TextureView) is docs/features/map-tiles.md. Do not invent a second `MapLibre.getInstance` / `MapView` path.
