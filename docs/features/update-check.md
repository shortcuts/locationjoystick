# Update Available Check

A small home-screen badge (`vX.Y.Z available`) tells the user a newer release exists on GitHub. Tapping it opens that release's page in the browser; the inline "X" dismisses it. Both acknowledge that version, so the badge stays hidden until an even newer one appears.

Key files: `:app/UpdateAvailablePopup.kt`, `:app/UpdateAvailableViewModel.kt`, `:app/DismissiblePillBadge.kt`, `:core:data/UpdateCheckRepository.kt`, `:core:common/util/VersionUtils.kt`

## Behaviour

- On app open, `UpdateAvailableViewModel` checks at most once per `AppConstants.UpdateCheckConstants.CHECK_INTERVAL_MS` (24 h), gated by `update_check_last_checked_at_ms`. The timestamp is written before the request, so a failed or offline check also waits a day instead of retrying every launch.
- `UpdateCheckRepository.fetchLatestVersion()` does one `GET api.github.com/repos/<slug>/releases/latest` and returns `tag_name` without its `v` prefix, or `null` on any failure (offline degrades silently).
- The badge shows when the cached version is `isNewerVersion` than `AppInfo.VERSION_NAME` and differs from the dismissed version. The compare strips a `v` prefix and any `-` pre-release suffix; missing segments count as 0.
- The release URL is derived from the cached version (`UpdateCheckConstants.releaseUrl`), not stored separately.
- Shown on the Idle screen only, bottom-end, opposite the What's New badge (@docs/features/whats-new.md). Both share `DismissiblePillBadge`.
- No Settings toggle. `update_check_last_checked_at_ms`, `update_check_cached_latest_version`, and `update_check_dismissed_version` are per-device DataStore keys, not part of `AppSettings`/`ExportData`.

## Not yet

A Play Store link is a planned addition; nothing here hardcodes a Play Store URL.
