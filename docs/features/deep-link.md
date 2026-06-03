# Deep Links & Location Sharing

Share any coordinate or saved favorite as a link. Anyone who taps the link on Android (with the app installed) is taken directly into the app with the coordinate pre-selected and the map centered on it.

## URL Format

```
https://locationjoystick.shrtcts.fr/?lat=LAT&lon=LON
```

| Parameter | Type | Range | Description |
|-----------|------|-------|-------------|
| `lat` | `Double` | `[-90, 90]` | Latitude |
| `lon` | `Double` | `[-180, 180]` | Longitude |

Custom scheme equivalent (app-to-app):

```
locationjoystick://open?lat=LAT&lon=LON
```

Both formats are parsed identically. HTTPS is preferred for general sharing. The custom scheme is useful for automation and app-to-app workflows.

## Generating Share Links (UI)

Two entry points:

1. **Favorites** — tap ⋮ → **Share** on any saved favorite.
2. **Map long-press** — tap any location → bottom sheet → **Share this location**.

Both fire the system share sheet with the formatted URL as plain text.

## Receiving Links

When the link is opened:

1. App opens (or comes to foreground via `singleTask` launch mode).
2. Map screen activates.
3. Map pans to the coordinate.
4. Confirmation sheet appears: Teleport / Walk here / Walk via roads / Do nothing.

The coordinate is never acted on automatically — the user must confirm.

## 3rd Party Integration

Any tool can construct a link without an API key or authentication:

```
https://locationjoystick.shrtcts.fr/?lat=35.6762&lon=139.6503
```

Rules:
- Both `lat` and `lon` are required. Missing either silently drops the link.
- Coordinates outside valid range (`lat` > 90 or < −90, `lon` > 180 or < −180) are rejected.
- Standard URL percent-encoding applies for any unusual characters, though coordinates are pure ASCII.

## Domain Verification

`locationjoystick.shrtcts.fr` uses `android:autoVerify="true"`. For HTTPS links to open the app directly (no disambiguation dialog), `/.well-known/assetlinks.json` must be served at that domain with the release signing fingerprint. Without it, Android shows a chooser — links still work but require user selection.

## Implementation

| Layer | Detail |
|-------|--------|
| Entry | `MainActivity.handleIntent` → `parseDeepLinkCoords` (in `DeepLinkParser.kt`) |
| Channel | `DeepLinkRepository` — `Channel.CONFLATED`; holds the latest unread coordinate |
| Consumer | `MapViewModel.observeDeepLinkCoords` — sets `pendingTapPosition` + `pendingCameraTarget` |
| URL builder | `AppConstants.AppInfo.buildDeepLink(lat, lon)` |
| Manifest | Two intent filters on `MainActivity`: HTTPS (`autoVerify`) + custom scheme |
