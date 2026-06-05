# Play Store Publish Checklist

Complete every item in order. Items marked `[x]` are already done.

---

## 1. Keystore Setup

- [x] Generate keystore (one-time):
  ```bash
  keytool -genkeypair -v \
    -keystore ~/locationjoystick-release.jks \
    -alias locationjoystick \
    -keyalg RSA -keysize 2048 \
    -validity 10000
  ```
- [x] Store `locationjoystick-release.jks` in a secure location (password manager, encrypted backup) — never commit it

---

## 2. Version Bump

- [x] Increment `versionCode` in `build-logic/convention/src/main/kotlin/LjApplicationConventionPlugin.kt` (must strictly increase with every upload)
- [x] Update `VERSION_NAME` (the string shown to users)

---

## 3. Build Signed AAB

- [x] Set signing environment variables:
  ```bash
  export KEYSTORE_PATH=~/locationjoystick-release.jks
  export STORE_PASSWORD=<keystore-password>
  export KEY_ALIAS=locationjoystick
  export KEY_PASSWORD=<key-password>
  ```
- [x] Run `make lint` — must pass with zero errors
- [x] Run `make test` — must pass
- [x] Run `make bundle` — AAB output at `app/build/outputs/bundle/release/app-release.aab`

---

## 4. Play Console Account & App Entry

- [ ] Create Google Play Console developer account ($25 one-time fee)
- [ ] Create app entry — package `com.locationjoystick.app`, Android App Bundle
- [ ] On first upload, enrol in **Play App Signing**: upload your upload key (the JKS above). Google manages the final signing key from that point on — keep the upload key safe for all future uploads.

---

## 5. Store Listing Assets

- [x] **Short description** (≤80 chars): `Mock your GPS location on Android — no root, no ads.`
- [ ] **Full description** (≤4000 chars): use `docs/reddit-post.md` as base, expand into Store prose
- [x] **Screenshots**: all 15 in `docs/wiki/screenshots/` (1080×2340) — upload at least 2
- [ ] **App icon** (512×512 px): `docs/wiki/icon.png` is currently 192×192 — resize/export at 512×512 before upload
- [ ] **Feature graphic** (1024×500 px): does not exist yet — design and upload (shown at top of listing)

---

## 6. Privacy Policy

- [x] `docs/wiki/privacy.html` exists
- [ ] Enable GitHub Pages (repo Settings → Pages → main branch / docs folder)
- [ ] Verify URL resolves: `https://shortcuts.github.io/locationjoystick/privacy.html`
- [ ] Add the privacy policy URL in Play Console → App content → Privacy policy

---

## 7. Data Safety Form *(mandatory — blocks submission)*

Play Console → App content → Data safety.

- [ ] Declare the following:

  | Data type | Collected | Shared | Purpose | Required by user |
  |-----------|-----------|--------|---------|-----------------|
  | Approximate location | No | No | App functionality | No |
  | App interactions | No | No | App functionality | No |

- [ ] Declare location data (fine) — collected, not shared — app functionality (map centre, route recording)
- [ ] Declare data is encrypted in transit (HTTPS only)
- [ ] Users can request deletion? → No user data stored server-side; all data is on-device

---

## 8. Content Rating Questionnaire *(mandatory)*

Play Console → App content → App content rating → Start questionnaire.

- [ ] Category: **Utility / Tools**
- [ ] Violence: None
- [ ] Sexual content: None
- [ ] Language: None
- [ ] Controlled substances: None
- [ ] User-generated content: None
- [ ] Expected result: **Everyone (E)**

---

## 9. Target Audience & Content *(mandatory)*

Play Console → App content → Target audience and content.

- [ ] Target age group: **Everyone**
- [ ] Does the app appeal to children? **No** (technical developer tool)

---

## 10. App Content Declarations

Play Console → App content:

- [ ] Ads: **No ads** — app contains no advertising SDKs or ad placements
- [ ] Sensitive permissions: declare `ACCESS_FINE_LOCATION` and `SYSTEM_ALERT_WINDOW` usage
- [ ] News app: No
- [ ] COVID-19 contact tracing: No

---

## 11. Upload & Final Verification

- [ ] Upload AAB to Play Console internal test track
- [ ] Internal test track install succeeds on a device
- [ ] Privacy Policy URL resolves in browser
- [ ] About screen Privacy Policy link opens correctly in-app
- [ ] Promote to production

---

## 12. App Links Setup (`locationjoystick.shrtcts.fr`)

Enables `https://locationjoystick.shrtcts.fr/?coords=LAT,LON` URLs to open the app directly from Discord, Telegram, etc. Requires Play App Signing to be enrolled first (step 4).

- [ ] Get SHA-256 signing cert fingerprint: Play Console → Setup → App signing → "App signing key certificate" → copy the SHA-256 fingerprint
- [ ] Replace `REPLACE_WITH_YOUR_SHA256_SIGNING_CERT_FINGERPRINT` in `docs/wiki/.well-known/assetlinks.json` with the fingerprint (colon-separated uppercase hex, e.g. `AB:CD:EF:…`)
- [ ] Add DNS record: CNAME `locationjoystick.shrtcts.fr` → `shortcuts.github.io` (or wherever GitHub Pages is served from)
- [ ] Enable GitHub Pages to serve `docs/wiki/` so `https://locationjoystick.shrtcts.fr/.well-known/assetlinks.json` resolves with `Content-Type: application/json`
- [ ] Verify: `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://locationjoystick.shrtcts.fr&relation=delegate_permission/common.handle_all_urls`
- [ ] Test on device: tap `https://locationjoystick.shrtcts.fr/?coords=35.62460,139.77627` in Chrome → app opens directly (no chooser dialog)

---

## Blockers (must resolve before submission)

1. **App icon** — resize `docs/wiki/icon.png` from 192×192 to 512×512
2. **Feature graphic** — create a 1024×500 px banner image
3. **Full store description** — write the Play Store long description
4. **GitHub Pages** — enable in repo settings so the privacy URL resolves
5. **Signed AAB** — run `make bundle` with signing env vars configured
6. **Play Console account** — $25 registration if not done yet
7. **App Links** — fill SHA-256 fingerprint in `docs/wiki/.well-known/assetlinks.json` and configure DNS (step 12)
