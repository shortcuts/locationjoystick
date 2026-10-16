# Contributing

## Building

Prerequisites: Android Studio, or JDK 17 with the Android SDK command-line tools. Install the API 28 or later SDK.

```bash
git clone https://github.com/shortcuts/locationjoystick.git
cd locationjoystick
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Release build: `./gradlew assembleRelease`. AAB for Play Store: `make bundle`.

[release-please](https://github.com/googleapis/release-please) automates releases. When you merge to `main`, it opens or updates a release PR from the Conventional Commits. When you merge that PR, it tags the version, and CI builds, signs, and uploads the APK to GitHub Releases.

## Before you open a PR

1. Read [AGENTS.md](AGENTS.md) for code standards, modules, and architecture rules.
2. Put one feature or fix in each PR.
3. For a new feature, open an issue first.

## Translations

- [docs/features/localization.md](docs/features/localization.md) describes the resource layout and conventions.
- Put each translation in a `values-<qualifier>/strings.xml` next to the module's `res/values/strings.xml`. Use exactly the same keys.
- Do not translate the app name ("locationjoystick") or the units and symbols inside format strings.
- If an AI tool helped with the translation, ask a native speaker to review it before you open the PR. AI tools produce phrasing that sounds right but is wrong, and a reviewer who does not speak the language cannot catch it.

## Required checks

All three must pass:

```bash
make format
make lint
make test
```

Fix every lint error before you finish. Warnings are acceptable. Do not suppress `Errors` rules, and do not use `@file:Suppress`.

## Reference docs

| Topic | File |
|-------|------|
| Architecture & modules | [docs/architecture.md](docs/architecture.md) |
| Code style rules | [docs/code-style.md](docs/code-style.md) |
| Testing strategy | [docs/testing.md](docs/testing.md) |
| Domain models | [docs/domain-models.md](docs/domain-models.md) |
| Constants | [docs/constants.md](docs/constants.md) |
| Permissions | [docs/permissions.md](docs/permissions.md) |
| Technical constraints | [docs/technical-constraints.md](docs/technical-constraints.md) |
| Feature specs | [docs/features/](docs/features/) |