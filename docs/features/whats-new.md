# What's New Popup

Small badge, shown app-wide, that tells the user this version has changes worth a look. Not
mandatory — the badge can be dismissed without opening it, and it never blocks the app.

Key files: `:app/WhatsNewPopup.kt`, `:app/WhatsNewViewModel.kt`, `:core:data/WhatsNewRepository.kt`

## Behaviour

- A pill badge ("What's new") appears bottom-start on every screen (`LjApp.kt`'s root `Box`,
  same overlay layer as the global snackbar) whenever `AppConstants.AppInfo.VERSION_NAME`
  differs from the last version the user acknowledged. This check is local (DataStore vs. a
  compile-time constant) — the badge itself needs no network and appears offline.
- **Tap the badge**: marks the current version seen, opens a modal, and fetches
  `AppConstants.WhatsNewConstants.buildUrl(VERSION_NAME)` (`WhatsNewRepository`) — the same
  per-version JSON file the website is built from (see "Single Source of Truth" below). Shows a
  loading spinner while the request is in flight, the fetched bullets on success, or a short
  inline message on failure ("Couldn't load what's new...") with the "View full changelog"
  button still available as a fallback.
- **Tap the badge's close (×)**: marks the version seen without opening the modal or making any
  network request — reviewing the changelog is never required.
- Once marked seen, the badge stays hidden until the next version bump.
- "View full changelog" opens `AppConstants.AppInfo.CHANGELOG_URL`
  (`docs/wiki/changelog.html`) in the browser.

## Single Source of Truth

The app does **not** carry its own copy of the highlights — no hardcoded Kotlin list to drift
out of sync with the website. Both surfaces are built from the same per-version file:

```
docs/wiki/changelog/<version>.json    →  { "version": "...", "highlights": ["...", ...] }
```

served statically by GitHub Pages at `https://shortcuts.github.io/locationjoystick/changelog/<version>.json`.
`WhatsNewRepository.fetchHighlights(version)` fetches exactly that file for the running app's
own version (plain `OkHttpClient` + `org.json`, mirroring `ElevationRepository`) — never a list
or an index, so there's no client-side filtering to keep correct.

- **Offline / fetch failure**: `fetchHighlights` returns `null`, the dialog shows a short error
  with the changelog link as a fallback. The badge itself is unaffected — this only degrades the
  in-app content, matching the app's offline-first stance (OSRM, real elevation lookup, and
  short-link resolution degrade the same way).
- **No file for this version**: a 404 is treated the same as any other fetch failure.

## Storage

`AppSettings.whatsNewLastSeenVersion` is **not** part of `AppSettings`/`ExportData` — it's a
per-device UI acknowledgment, not app data, matching `ThemeMode` and
`REMEMBER_LAST_LOCATION` (see @docs/features/theme.md). Persisted via
`SettingsRepository.getWhatsNewLastSeenVersion()`/`setWhatsNewLastSeenVersion()`,
DataStore key `whats_new_last_seen_version`, default `""` (never seen).

## Maintaining the Highlights

Any release with user-visible changes needs **both** of these, written together in the same
commit — neither is optional, and this file is the only place both steps are spelled out:

1. **`docs/wiki/changelog/<version>.json`** — the machine-readable file this popup fetches.
   2-4 short, plain-English bullets under a `"highlights"` array (see
   `docs/wiki/changelog/0.18.2.json` for the shape). This is the *only* place the popup's
   content comes from — there is no app-side Kotlin list to also update.
2. **`docs/wiki/changelog.html`** — the human-readable, fuller changelog entry for the same
   version, in prose, following `docs/wiki/CONTRIBUTING.md`'s page structure and writing style.
   This is what "View full changelog" links to.

Write the JSON bullets as a short summary of the same release the `changelog.html` entry
describes in full — not a separate editorial pass. No app-side code change is needed to publish
a new version's highlights: the fetch is version-driven, so the next release's popup picks up
its own JSON file automatically once `AppConstants.AppInfo.VERSION_NAME` bumps.
