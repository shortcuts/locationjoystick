# Update Available Check

A small home-screen badge tells the user a newer release exists on GitHub. Tapping it opens that release's page in the browser. Dismissing it acknowledges that version. The badge stays hidden until an even newer release appears.

Key files: the app module's update popup and view model, the update check repository in `:core:data`, and the version helpers in `:core:common`.

## Behaviour

- On app open, the check runs at most once per day. The time of the last attempt is written before the request, so a failed or offline check also waits a day. It does not retry on every launch.
- The check asks GitHub for the latest release tag. A failure of any kind is silent: the app stays quiet when offline.
- The badge shows only when the latest version is newer than the running one, and differs from the dismissed version. Version comparison ignores a leading prefix and any pre-release suffix.
- The release link is built from the version. Nothing stores a second copy of the URL.
- Shown on the home screen only, opposite the What's New badge ([whats-new.md](whats-new.md)). Both use the same pill badge.
- No settings toggle. The check state is per device and is not part of the exported settings.

## Not yet

The badge always opens the GitHub release page, including on Play Store installs. A Play Store link is planned. Nothing here hardcodes a Play Store URL.
