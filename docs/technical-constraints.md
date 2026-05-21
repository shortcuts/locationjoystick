# Technical Constraints

- Min SDK API 31. Use `ProviderProperties.Builder` (API 31+). No deprecated raw-int overload.
- No Play Services. MapLibre, not Google Maps. No Firebase.
- Offline-first. Core features work without internet. OSRM opt-in, degrades gracefully.
- No `Thread.sleep()`. Use `delay()` in coroutines.
- No empty catch blocks. Every `catch` must log or handle.
- No `GlobalScope`. Use `viewModelScope`, `lifecycleScope`, or scoped `CoroutineScope`.
- No memory leaks. Every `WindowManager.addView` needs matching `removeView` in `onDestroy`. Every scope cancelled in `onDestroy`/`onCleared`.
- Location updates at 1 Hz.
- Battery: use `IMPORTANCE_LOW` notification channel. No wake locks unless necessary.