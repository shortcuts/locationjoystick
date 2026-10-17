# Theme

Light and dark colour themes, for readability across lighting conditions, such as direct sunlight.

Design tokens and components: see [../design-system.md](../design-system.md). Colour values and shapes are in the design system code.

Key files: the design system's theme and colour files, the settings and preferences layers, the app's main activity and theme view model, and the widget overlay code.

## Modes

Two modes exist. Dark is the default. Light is high contrast, with dark text on a light background for use in sunlight. Both share the same type and shapes, so only colours differ.

Text, control outlines and the top-bar toggle meet contrast minimums in both modes. A test guards the palette, so a colour change that breaks contrast fails the build.

## Storage and application

The choice is a live preference. It takes effect at once, with no save step, and it is per device.

The app's root theme follows the stored choice, so the whole tree redraws in the new scheme. Overlay windows read the same choice, because they sit outside the app's own theme and would otherwise show the wrong colours over other apps.

The icon column of the widget stays dark with bright icons in both modes. It must stay visible over any app underneath.

## Why outlines use their own colours

Unchecked boxes and outlined fields use the outline colour for their theme. Both outline colours are chosen to meet the 3:1 control contrast minimum on their own surface. A shared outline colour fails one of the two modes.

Filled buttons take their label colour from the scheme. Hosts must not paint labels with the general text colour, because it would fail contrast on the filled background.

## Surfaces

- Sheets, cards and dialogs use the theme's surface tokens. Both schemes set these tokens explicitly. An unset token falls back to the library's default palette, which is purple-tinted and wrong for this app.
- Cards and fields are flat, with a hairline border instead of a shadow. Shadows look like a different app on a dark map.
- Map buttons and floating panels use the same hairline, so they stay visible on any map.

## Edge cases

- Not part of the app settings export. It is a per-device display choice, like the remembered location.
