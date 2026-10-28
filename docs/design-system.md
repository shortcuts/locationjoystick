# Design System

Reference for developers and AI agents who change UI in the app or mirror its look in `docs/wiki/`. Theme behavior (modes, storage, application) lives in [features/theme.md](features/theme.md).

Tokens, palette, type, shape, spacing and the component inventory live in code under `core/designsystem/src/main/kotlin/com/locationjoystick/core/designsystem/`. Read them there. This file keeps the direction, the rules, and the judgement calls that code cannot express.

## Direction and why

**Quiet Utility.** Flat and dense, with hairline separators and no shadows. Orange accent on charcoal.

Why: the app is a tool used one-handed, often outdoors, over a map. Chrome must stay out of the way and keep contrast high.

Uber and Airbnb guidance (VoltAgent `awesome-design-md`) was a reference for density and restraint, not a template. Do not copy their marks, type or layouts.

## Rules

- Features read colour roles from the theme, never raw hex. Status colours and the map palette have their own helpers; use them rather than inventing colours.
- No elevation. Cards are separated by fill and spacing. Only the outlined variants draw a hairline.
- Use the type, shape and spacing tokens rather than literals. Never set font size inline.
- Prefer the `Lj*` components over raw Material components. Raw Material is a last resort, and the components exist to hold these rules in one place.
- Overlay windows (joystick, widget) sit outside the activity theme, so they must be wrapped in the app theme. Without it they render with the wrong colours.
- The guided path reaches composables through `LocalGuidedPath`, which `LjTheme` provides. Read it there; never pass a path flag down as a parameter, because a forgotten call site then shows the wrong controls.
- Motion must be short, carry meaning, and respect the system's reduce-motion setting. There is no motion token file on purpose: the app has little motion, and a token file would invite more.
- Icons come from the shared icon set, not from the platform icon package in features. Icon-only buttons always need a content description.

## Component choice

Pick by intent. The component inventory is in code.

- Card vs list row: use the list-item card for saved items (routes, favorites, profiles). Use the plain card for grouped content. Settings toggles are never list-item cards.
- Dialog vs sheet: a dialog only for a short confirmation or a choice. Forms and long content go in a sheet.
- Delete: always the shared delete-confirm dialog. Non-destructive confirms use the generic dialog.
- One primary action per screen or group. A second filled button beside it is a mistake; make it outlined or text.
- Selection: the selection row covers radio, checkbox and switch. Choose the mode by what the option means, not by how it looks.
- Loading: a skeleton for lists, a spinner only for a short blocking load.
- The wide-content clamp is for tablets and never applies to map surfaces.

## Copy and tone

- Sentence case. Short. No exclamation marks.
- Buttons start with a verb.
- State what happened and what to do next; no blame.
- All user-visible text lives in string resources. Translations stay in sync per [features/localization.md](features/localization.md).
- Use one word for one thing across the app (route, favorite, speed profile, group).

## Accessibility

- Text and controls meet WCAG contrast in both themes. The palette test guards this; run it when changing the colour definitions.
- Touch targets are at least 48 dp even when the visible control is smaller. Padding is how a small visual control keeps a large target.
- Never convey state by colour alone. Pair it with an icon or text.

## Light and dark

- Both themes share type, shape and spacing. Only the colour scheme differs.
- Light is high contrast for sunlight. Dark text on a warm off-white, with a deeper accent for the same role.
- Check every UI change in both themes using the theme preview.

## Mirroring tokens in the wiki

The wiki is user-facing and is written under `docs/wiki/CONTRIBUTING.md`. Keep its look aligned with the app, but do not copy code terms into pages.

- Wiki colours, radii and fonts are defined once in the `:root` block of `docs/wiki/style.css`. Change them there, not per page, and take the values from the app's colour definitions so the two stay the same.
- Light variants go under the light colour-scheme media query.
- Hairline borders and flat surfaces only. No box shadows.
- The wiki uses system fonts only. Do not add web or CDN fonts.
- Verify with `make wiki-serve`.

## Maintenance

Update this file when a rule or a judgement changes, or when a new component needs a use-when rule. Token values and component lists are not recorded here; a new token or component needs no edit to this file.
