# What's New Popup

An optional badge on the home screen lets users review this version's changes. Closing the badge acknowledges the version without opening the modal. It stays hidden until the next version.

Key files: the what's-new popup and view model in the app module, and the what's-new repository in `:core:data`.

## Behaviour

- The badge compares the running version with the locally acknowledged version. It appears only on the home screen and works offline.
- Opening it acknowledges the version and reads that version's changelog. The entries are grouped by category and scope, the same way as the wiki.
- Missing or invalid packaged content shows an inline error. The full changelog link stays available, but it needs a browser and a network connection.
- Dismissing the badge without opening it reads no changelog content.

## Single source of truth

`docs/wiki/changelog/<version>.json` is the only authored source for release notes. The same JSON feeds the wiki's changelog page and the in-app popup. Nothing is written twice. The JSON schema is defined by the parser in code.

The data is packaged into the app as an asset, so the popup needs no network fetch and no second list to maintain.

## Storage

The acknowledgement is per device. It is not exported as app data.

## Maintaining the changelog

1. Pick the next release version, not the version constant in the app. Release tooling bumps that constant when it merges the release change, so until then it still names the version already shipped. Never edit the JSON of a version that has a git tag, because that text already reached users.
2. Write one entry per user-visible change, in plain user language.
3. Run the changelog target to regenerate the wiki changelog page. Never edit the generated page by hand.
4. Check that the target version has a JSON file with at least one entry. CI checks that the file exists on the release PR, so a missing file blocks the release PR and never the tagged build. Tests cover parsing and reading the packaged file.

Release tooling controls version bumps. No app-side list or link needs updating for a new release.
