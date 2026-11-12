# Privacy Mode

GitHub APK only. The Play build has none of this code, and its manifest declares no install or delete permission.

## Why GitHub only

Installing another app needs `REQUEST_INSTALL_PACKAGES`. Play policy restricts it, so `play` must not hold it. `scripts/check_play_strip.py` fails `make bundle` when the AAB gains it, or `REQUEST_DELETE_PACKAGES`, or the signing libraries.

## What it does

It rewrites the installed APK into a clone under a random package ID, a user-chosen name and a shifted icon hue. It hides only the package name and label from other apps. It does not hide the mock-location setting from apps that read it. The disclosure and the wiki page say so.

## Decisions

- **Opt-in.** The Settings card shows in every `github` build. A switch on its screen, off by default, gates the controls. A one-time disclosure runs when the switch first turns on.
- **Switch storage.** `PrivacyFlagsStore`, a file outside `AppSettings`. The flag exists in `github` only, and `AppSettings` is shared with Play and the export. It sits outside `privacy-clone/`, so Remove keeps it.
- **Switch off keeps the clone.** The switch is a visibility switch. Destroying the key on a toggle would orphan an installed clone.
- **One clone.** Update reuses the package ID, hue and signing key, so the system accepts it as an update. Update also renames.
- **Hidden inside a clone.** `SettingsExtension.isAvailable` is false when `packageName` differs from `BuildConfig.APPLICATION_ID`. A clone would otherwise offer to clone itself with an empty store. The builder never rewrites dex, so `BuildConfig` keeps the original ID.
- **Install hand-off.** `PackageInstaller` with a user confirm. The UI starts the confirm screen, because a receiver start from the background is blocked on API 34. The result flow has no replay, so leaving the screen mid-install loses the status.
- **Install permission checked before the build.** A missing "Install unknown apps" grant sends the user to that system screen. The user taps Start again. An automatic resume would run a hidden 57 MB build on return from a system screen.
- **Remove uses the result code.** `ACTION_UNINSTALL_PACKAGE` with `EXTRA_RETURN_RESULT`. `RESULT_OK` and `RESULT_FIRST_USER` (the clone is already gone) clear the key and record. Detecting an installed clone would need `QUERY_ALL_PACKAGES`, a heavier permission than this flow needs.
- **The original stays.** The app never uninstalls itself. The post-install note tells the user how to move data and that they may uninstall the original.
- **Key per device.** The clone key never enters an export or QR transfer. See [export-import.md](export-import.md).
- **R8.** `app/proguard-rules.pro` keeps ARSCLib, apksig and BouncyCastle. R8 removed the builder when nothing called it, and these libraries reach classes by reflection and provider lookup.

## Stored state

`filesDir/privacy-clone/` holds the clone record and key (`CloneStore`). `filesDir/privacy-mode/flags.properties` holds the switch. `cacheDir/privacy-clone/clone.apk` is deleted at each terminal install status.
