# Localization

The app's interface can be shown in a language other than English. English is the source language. Translations are added as extra resource sets beside it.

Key files: the locale wrapper and the language constants in `:core:common`, the language model in `:core:model`, the language dropdown in `:core:designsystem`, and the lint check that flags hardcoded strings in `:lint:checks`.

## Supported locales

English is the default and source. Simplified and Traditional Chinese are shipped. The list of folders is the list of locales; the build generates the system locale list from them.

## Resource layout

Each module that shows user-facing text owns its own English strings file, plus a sibling file per supported locale. Keys must be identical across locales. A module may split its strings across several files, and each split needs a sibling in every locale.

Cooldown text is kept in the design system's own strings and formatted by helper functions, so the state objects hold no text.

## Choosing a language

- **Android 13 and later:** the system's per-app language screen lists the shipped locales. The build generates the list, so nothing is maintained by hand.
- **Android 9 to 12:** the system has no per-app language screen. The app wraps the base context in each component that shows text, including the main activity, the overlays and the foreground service and its notification. The app's own choice is read from its preferences.
- **In-app picker:** a compact dropdown in the header of onboarding and of general settings. It offers EN, CN, TW and system default. On Android 13 and later, the choice goes to the system. On earlier versions it goes to the app's preferences, and the activity is recreated so the change shows at once.
- **Limitation below Android 13:** a running spoofing session keeps the language it started with. Its notification and overlays change at the next session, because a component reads its language only when it is created. Android 13 applies the change everywhere at once.
- **Play installs:** language splitting is off, so every install carries every locale. A device's language at install time does not decide what is shipped.

The language choice is per device. It is not part of the export, like the theme.

## Lint enforcement

A lint check flags hardcoded user-facing strings in Compose code, including strings in conditional expressions and semantics blocks. New text must go in a strings file, so it stays translatable.

## Translation rules

- English is the source of truth. A translation carries the same keys and nothing else.
- The app name and brand are never translated. Units and symbols inside format strings are not translated either.
- Strings that Play policy depends on need native-speaker review before release. These are the accessibility disclosure, the onboarding card and the service description. See [tap-to-walk.md](tap-to-walk.md).
- AI-assisted translations need native-speaker review before merging. See `CONTRIBUTING.md` at the repo root, "Translations".

## Adding a language

1. For each module's English strings file, add a sibling file for the new locale with every key translated. No keys added or removed.
2. Leave the brand and embedded units untranslated.
3. Run the format, lint and test targets.
4. Get a native speaker to review the strings before merging.
