# Design System

Reference for developers and AI agents who change UI in the app or mirror its look in `docs/wiki/`. Code symbols are fine here. Theme behavior (modes, storage, application) lives in [features/theme.md](features/theme.md).

Source of truth is the code in `core/designsystem/src/main/kotlin/com/locationjoystick/core/designsystem/`. When a value here disagrees with the code, the code wins; fix this file.

## Direction and why

**Quiet Utility.** Flat and dense. Hairline dividers, 8-12 dp radii, one system sans, edge-to-edge sheets, no shadows, text-first rows, orange accent on charcoal. A live speed strip sits on the map.

Why: the app is a tool used one-handed, often outdoors, over a map. Chrome must stay out of the way and keep contrast high.

Uber and Airbnb guidance (VoltAgent `awesome-design-md`) was a reference for density and restraint, not a template. The color palette and navigation were kept. Do not copy their marks, type or layouts.

## Tokens

### Palette roles

Defined in `LjColors.kt`. Features read roles from `MaterialTheme.colorScheme`, never raw hex.

| Role | Dark | Light | Use |
|---|---|---|---|
| `background` | `LjBg` #1E1E24 | `LjLightBg` #FAF7F5 | Screen background |
| `surface` | `LjSurface` #252530 | `LjLightSurface` #FFFFFF | Sheets, cards on background |
| `surfaceVariant` | `LjSurfaceVariant` #2D2D3A | `LjLightSurfaceVariant` #F0E9E4 | Default `LjCard` fill, fields |
| `onSurface` | `LjText` #F7EBE8 | `LjLightText` #231E1B | Primary text |
| `onSurfaceVariant` | `LjTextSecondary` #B0A8B4 | `LjLightTextSecondary` #5C5259 | Secondary text, captions |
| `primary` | `LjAccent` #F79D5C | `LjLightAccent` #984313 | Actions, selected state |
| `outlineVariant` | `LjDarkOutlineVariant` #8A8490 | `LjLightOutlineVariant` #7A6F68 | Hairlines, unchecked controls |
| `error` | `LjError` #EF4444 | `LjLightError` #B3261E | Errors, destructive |

Status colors are not in the scheme. Use `ljSuccessColor()`, `ljWarningColor()`, `ljWarningContainerColor()` (they follow the active theme). `LjInactive` is a fixed grey for disabled state. `LjOnScrim` is white text on scrims.

Map colors (`LjMapColors`): `ActiveButton` green, `PositionBlue`, `RouteOrange`, `PointStroke` white, `PendingTapGreen`. Same in both themes; use only on the map.

### Type

Defined in `LjTypography.kt`. System sans (`FontFamily.Default`) only. Titles and headlines are SemiBold; body and label styles are Normal or Medium as set in that file. Use `MaterialTheme.typography.*` roles; never set `fontSize` inline.

### Shape

`LjShapes.kt`: extraSmall 4, small 8, medium 12, large 16, extraLarge 20 dp. Cards and fields use `medium` (12); chips and badges `small` (8); sheets use `large` top corners.

### Spacing

`LjSpacing.kt`: `xs` 4, `sm` 8, `md` 16, `lg` 24, `xl` 32 dp. Use these, not literals. Screen gutter is `md`.

### Sizes

`UiConstants.kt`: FAB container 42 dp, FAB icon 24 dp, small icon 16 dp, wide content max width 600 dp (see `WideContentClamp`).

### Elevation

None. No shadows, no tonal lift. Separate surfaces with a 1 dp `outlineVariant` hairline (`LjCard`, `LjOutlinedCard`, `LjMapIconButton`, `LjOutlinedButton` do this). Card elevation is forced flat.

### Motion

Only what exists today; no motion token file.

| Where | What |
|---|---|
| `rememberPressScale` in `LjButton.kt` | Press scale 0.96, used by every button variant and `LjMapIconButton` |
| `LjMapIconButton`, `LjTopBar`, `LjGuidedStepCard` | 150 ms `tween` crossfade for icon or label swaps |
| `LjGuidedStepCard` | Status color `spring(StiffnessMediumLow)` |
| `LjSkeleton.kt` | Linear shimmer on loading rows |

New motion must be short (about 150 ms), carry meaning (state change, not decoration), and degrade when the system disables animations.

### Icons

`LjIcons` in `LjIcons.kt`. Rounded Material icons only, plus custom vectors (`NorthUp`, joystick). Add new icons there; do not import `Icons.*` in features. Icon-only buttons need a `contentDescription`.

## Components

All in `core/designsystem/.../component/`. Prefer these over raw Material3.

| Component | Use when | Don't use when |
|---|---|---|
| `LjScaffold` | Every full screen (top bar slot and insets) | Overlay panels |
| `LjTopBar`, `LjOverflowMenu`, `LjOverflowMenuSectionLabel` | Screen title, back, actions; secondary actions in the overflow | More than one primary action |
| `LjCard` | Grouped content on `surfaceVariant`, optionally clickable | A list row (use `LjListItemCard`) |
| `LjOutlinedCard` | Card on a surface of the same color | Anything needing a fill |
| `LjSurface` | Plain container with theme shape and hairline | Clickable rows |
| `LjListItemCard` | Saved item rows: route, favorite, profile | Settings toggles |
| `LjModalBottomSheet`, `rememberLjSheetState` | Every bottom sheet | Dialogs that need a decision |
| `LjActionSheetRow` | One tappable action in a sheet | Navigation rows in lists |
| `LjAlertDialog` | Short confirmation or choice | Forms, long content (use a sheet) |
| `LjDeleteConfirmDialog` | Any destructive delete | Non-destructive confirms |
| `LjButton` / `LjPrimaryButton` | The one main action | Secondary actions |
| `LjOutlinedButton` | Secondary action | Destructive-only flows |
| `LjTextButton` | Tertiary, dialog dismiss | Primary action |
| `LjFloatingActionButton` | The single screen-level create action | Map controls |
| `LjMapIconButton` | Round control over the map | Off-map screens |
| `LjSegmentedControl` | 2-4 mutually exclusive options | Long option lists |
| `LjCheckboxRow` | Boolean option with label | Immediate on/off settings with side effects |
| `LjLanguageDropdown` | Language choice | Other pickers |
| `LjGuidedStepCard` | Setup step with status | General info cards |
| `LjRouteStartOptions`, `RouteStartSheetContent` | Route start options (shared by map and widget) | Anything else |
| `LjListItemCardSkeletonList` | Loading placeholder for item lists | Short spinners |
| `EmptyState` | A list with no items, with next action | Errors |
| `LoadingIndicator` | Short blocking load | Lists (use skeleton) |
| `ListSearchField` | Filter inside a saved-items list | Place search (use `NominatimSearchBar`) |
| `WideContentClamp` | Cap content at 600 dp on tablets | Map surfaces |

## Copy and tone

- Sentence case. Short. No exclamation marks.
- Buttons start with a verb: "Save route", "Delete".
- State what happened and what to do next; no blame.
- All user-visible text lives in string resources (lint `HardcodedComposeStringDetector`). Keep translations in sync per [features/localization.md](features/localization.md).
- Same word for the same thing everywhere: route, favorite, speed profile, group.

## Accessibility

- Text 4.5:1, controls and outlines 3:1 (WCAG 1.4.11), both themes. `LjColorsContrastTest` guards the palette; run it when changing `LjColors.kt`.
- Touch targets 48 dp minimum, even when the visible control is smaller (FAB is 42 dp, padded).
- Icon-only buttons have a `contentDescription` from string resources.
- Never convey state by color alone; pair with icon or text.

## Light and dark

- One `ColorScheme` per mode (`LjDarkColorScheme`, `LjLightColorScheme`); type, shape and spacing are identical.
- Light is high contrast for sunlight: dark text on #FAF7F5, deeper accent #984313.
- Never hardcode `Color(...)` in features. Use `MaterialTheme.colorScheme`, status helpers or `LjMapColors`.
- Overlay windows (joystick, widget) are outside the activity theme: wrap in `LjTheme(darkTheme = ...)`.
- Check every UI change in both themes (`core/designsystem/.../preview/ThemePreview.kt`).

## Do and don't

```kotlin
// Do: shared card, theme shape, hairline
LjCard { Text(title, style = MaterialTheme.typography.titleMedium) }
// Don't: raw Material card with custom shape and shadow
Card(elevation = CardDefaults.cardElevation(4.dp)) { ... }
```

```kotlin
// Do: separation by hairline
Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
// Don't: shadows
Modifier.shadow(8.dp)
```

```kotlin
// Do: scheme role
color = MaterialTheme.colorScheme.primary
// Don't: hardcoded hex
color = Color(0xFFF79D5C)
```

```kotlin
// Do
LjAlertDialog(...)            // Don't: AlertDialog(...)
LjModalBottomSheet(...)       // Don't: ModalBottomSheet(...)
LjSegmentedControl(...)       // Don't: hand-built Row of buttons
```

```kotlin
// Do: spacing token and string resource
Modifier.padding(LjSpacing.md); Text(stringResource(R.string.route_save))
// Don't: literals
Modifier.padding(15.dp); Text("Save route!")
```

```kotlin
// Do: one primary action
LjPrimaryButton(onClick = save) { Text(stringResource(R.string.save)) }
// Don't: two filled buttons side by side; make the second LjOutlinedButton or LjTextButton
```

## Mirroring tokens in the wiki

The wiki (`docs/wiki/style.css`) is user-facing; keep it visually aligned with the app, but do not copy code terms into pages. Writing rules: `docs/wiki/CONTRIBUTING.md`.

| App token | Wiki custom property | Rule |
|---|---|---|
| `background` | `--bg` | Same hex per theme |
| `surface` / `surfaceVariant` | `--surface` | Same hex per theme |
| `outlineVariant` | `--border` | 1 px hairline; no shadows |
| `onSurface` | `--text` | Same hex |
| `onSurfaceVariant` | `--muted` | Same hex; keep 4.5:1 on `--bg` |
| `primary` | `--accent` | Dark `#F79D5C`, light `#984313` |
| `shapes.small/medium` | `--r` | 8 px and 12 px |

Steps for a wiki author:

1. Read `:root` in `docs/wiki/style.css`; change values there, not per page.
2. Add a light variant under `prefers-color-scheme: light` using the light hex values above.
3. Use hairline borders and flat surfaces; no `box-shadow`.
4. Font: the app uses the system sans. Check `--sans`/`--mono` in `style.css` for what the wiki ships; do not add CDN fonts.
5. Verify with `make wiki-serve`.

## Maintenance

Update this file when you change tokens in `core/designsystem` or add, remove or rename a shared `Lj*` component. List new components with a use-when and don't-use-when. Keep `docs/features/theme.md` for behavior only.
