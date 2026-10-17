# Location Links

Get externally shared location links into the app. Two tiers, automatic and opt-in. Both end in the same confirm sheet (teleport, walk, or do nothing).

## Overview

**Automatic tier** (always on): map links and location shares from other apps open the confirm sheet. No setup.

**Opt-in tier** (Capture mode): every map link can be captured while this app is the default browser. The user can collect links into a list, jump to them, or both. Setup is needed.

## Automatic tier

### Link format

The app's own HTTPS link carries a latitude and a longitude. A custom-scheme link with the same parameters works for app-to-app use. Both are parsed identically. HTTPS is preferred for sharing. The exact URL format is in the code (`AppInfo.buildDeepLink`), not repeated here.

Rules that third-party tools rely on:

- Both coordinates are required. A link missing either one is dropped silently.
- Out-of-range coordinates are rejected.
- No API key or account is needed. Anyone can build a link.

### Generating share links

The share action appears beside the recenter and search buttons on the map, on the floating map, and in the long-press and tap sheets. Share copies the coordinates or sends the link. The floating map closes its overlay before the system share sheet opens, so the sheet is not hidden behind it.

Favorites copy plain coordinates instead of a link. A link for a saved place is not needed, because the place already has a name in the app.

### Receiving

The app opens on the map and pans to the coordinate. The coordinate is never acted on automatically. The user must choose teleport, walk, walk via roads, or do nothing. Confirming teleport or walk also opens the app the user picked for after-link launch, if any.

If the link cannot be read, the user sees a message rather than nothing.

### Supported formats

The app accepts the common location formats from map apps: geo URIs (including the placeholder-base form, where the query value takes priority), the navigation scheme, and the Google Maps web forms (search, camera view, and place links). For place links the place's own coordinates win over the camera position, because the camera can drift from the place that was actually shared.

### Shortened Maps links

Maps' Share button sends a shortened link with no coordinates in it. The app follows the redirect chain to the full link before reading coordinates. This needs network access; offline, a shortened link cannot be read.

### Domain verification

Opening the app's own HTTPS links directly, with no chooser, requires the domain to publish the app's signing fingerprint. Without it, links still work, but Android asks which app to use.

## Opt-in tier: Capture mode

Collect map links from other apps into an on-device list, then save them as a route. Capture only intercepts when Capture mode is on and this app is the default browser.

### Why the default browser role

Android sends every web link to the default browser. Only then can the app see links from any app, not just the ones that declare a filter for them. That is why Capture needs the role, and why the app must forward links to a real browser when it does not capture them.

### Setup

Capture is off by default. Setup needs two things the app can check and one it cannot:

1. Set this app as the browser in Android's default apps settings. The app checks this and rechecks when the user returns.
2. Turn on supported links for this app in its Android settings. The app cannot verify this. The user must check it.
3. Turn off "open supported links" for Google Maps. Otherwise Maps opens links itself, and the app never sees them.

Until setup is complete, the Capture screen shows only the setup step. Captured points are kept and reappear after setup.

### Configuration

- **Capture mode:** the master switch. The first time it is enabled, the user chooses whether to clear or keep existing points.
- **List** and **Jump:** independent options. List appends a location to the list. Jump teleports to it at once, using the same teleport path as the rest of the app.
- **Pass-through browser:** where a link goes when it is not captured.

Capture settings are per device and are not exported. They reset when this app stops being the default browser. A separate restore action gives back the user's usual browser, while keeping the captured points.

Settings and the Capture screen read and write the same stored choices, so a change in one shows in the other. Setup steps and the restore action live only in settings.

### Intercept behaviour

The interceptor activity does not bring the app to the front. The calling app stays in the foreground, so the user can keep copying links.

| Capture | List | Jump | Coordinates | Result |
|---|---|---|---|---|
| on | on | off | yes | append, then finish |
| on | off | on | yes | teleport, then finish |
| on | on | on | yes | append, teleport, then finish |
| off | any | any | any | forward to pass-through browser |
| on | off | off | any | forward to pass-through browser |
| on | any | any | no | forward to pass-through browser |

When Capture is off, or both List and Jump are off, and the link has coordinates, the confirm sheet appears, with the same outcome as the automatic tier.

### Forwarding to a browser

This app cannot forward to the system's default browser, because it is the default. So it remembers the browser that held the role before setup, and lets the user pick another installed browser. Forwarding never goes back to this app, which would loop.

Google Maps web links go straight to Maps when Capture is off, even with Maps' supported-links switch off, because Maps can still open them when named explicitly. If Maps is missing, the chosen browser is used.

Browser discovery combines web-link handlers with browser launchers, because a phone that gives only the current role holder would hide the other browsers.

### Collecting and saving

Captured points appear as a numbered list. An exact repeat of the last point is skipped. The default order is nearest-neighbour, the same as the paste builder. The user can choose capture order instead.

The user can copy, remove the last point, or clear the list. Saving as a route needs at least two points. The route is added to the routes list under the name the user gives it.

### After-link launch

The user can pick an installed app to open after a link is handled. Focus then returns to the game, rather than staying in this app. It runs after a capture jump, and after a teleport or walk confirmed from a link. It never runs for list-only captures, for "do nothing", or for taps and pasted coordinates. The choice is per device and survives the browser restore.

The app list uses the launcher query only, so the app does not need broad package-listing permission.

## Implementation map

- Automatic entry: the main activity reads the link and hands it to a shared channel. The map view model consumes it and pins the coordinate through the same path as paste.
- Capture entry: the interceptor activity handles web links. Its decision is a pure function of the mode switches and the parsed coordinates, so it is testable without a device.
- The Capture list lives in the map feature. The stored choices live in the capture repository.
