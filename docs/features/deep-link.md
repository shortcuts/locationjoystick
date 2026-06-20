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

## Intercepting Third-Party Map Links

The app also registers itself as a candidate handler for location links coming from other apps (e.g. a "view on map" button for a point of interest). This lets a user pick locationjoystick from Android's chooser, or set it as the default handler for these link types, to redirect a third-party app's "open in Maps" action straight into a teleport/walk confirmation instead of opening Google Maps.

Supported formats (all parsed by `parseDeepLinkCoords`):

| Format | Example |
|---|---|
| `geo:` URI | `geo:35.62,139.77` |
| `geo:` URI with placeholder base + `q` param | `geo:0,0?q=35.62,139.77(Landmark)` — `q` takes priority over the `0,0` base, since many apps emit a placeholder base coordinate |
| `google.navigation:` scheme | `google.navigation:q=35.62,139.77` |
| `maps.google.com` (any path) | `https://maps.google.com/maps?q=35.62,139.77` |
| `www.google.com/maps*` | `https://www.google.com/maps/search/?api=1&query=35.62,139.77` |
| `www.google.com/maps/@LAT,LON,Zoomz` path form | `https://www.google.com/maps/@35.62,139.77,15z` |

These intent filters are **not** `autoVerify` — we don't control those domains, so Android shows a disambiguation chooser rather than auto-opening. `www.google.com` is scoped to `pathPrefix="/maps"` so other Google links (search, etc.) aren't intercepted.

Apps that launch Maps via an **explicit intent** (`setPackage`/`setClassName` targeting `com.google.android.apps.maps` directly, e.g. Pokémon Go's "Get Directions") bypass intent-filter resolution entirely — there is no fix for this case, since Android never runs the resolver for explicit intents.

### Google Maps "Share" Button

The Maps app's **Share** button uses Android's `ACTION_SEND` share sheet (text/plain), not `ACTION_VIEW` resolution — a separate mechanism with its own intent filter:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

`MainActivity.handleIntent` checks for `ACTION_SEND` + `text/plain`, reads `EXTRA_TEXT`, and extracts the first `http(s)://` URL via `extractUrlFromText` (in `DeepLinkParser.kt`).

The shared text usually contains a shortened link (`https://goo.gl/maps/...` or `https://maps.app.goo.gl/...`) with no embedded coordinates — the real lat/lon only exists in the page the shortener redirects to. `GoogleMapsShortLinkResolver` (`:core:data`) detects these hosts and follows the HTTP redirect chain (OkHttp, `Dispatchers.IO`) to recover the final long URL before parsing coordinates via `parseUrlCoords`.

## Implementation

| Layer | Detail |
|-------|--------|
| Entry | `MainActivity.handleIntent` → `parseDeepLinkCoords` (in `DeepLinkParser.kt`) |
| Channel | `DeepLinkRepository` — `SharedFlow(replay=1)`; `consume()` calls `resetReplayCache()` to clear after delivery |
| Consumer | `MapViewModel.observeDeepLinkCoords` — sets `pendingTapPosition` + `pendingCameraTarget` |
| URL builder | `AppConstants.AppInfo.buildDeepLink(lat, lon)` |
| Manifest | Intent filters on `MainActivity`: HTTPS own domain (`autoVerify`) + custom scheme + `geo:` + Google Maps hosts + `google.navigation:` + `ACTION_SEND` text/plain |
