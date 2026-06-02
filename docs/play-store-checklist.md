# Play Store Launch Checklist — Manual Steps

These require action in Google Play Console or external tools.
Everything code-side is already implemented.

**Code validation status (as of launch day):**
- `make lint` → BUILD SUCCESSFUL ✓
- `make test` → BUILD SUCCESSFUL ✓

---

## Before First Upload

- [ ] Create Google Play Console developer account ($25 one-time fee)
- [ ] Create app entry — package `com.locationjoystick.app`, Android App Bundle

---

## Privacy Policy

- [x] `docs/wiki/privacy.html` exists
- [ ] Enable GitHub Pages on the repo (Settings → Pages → main branch / docs folder)
  - Verify URL works: `https://shortcuts.github.io/locationjoystick/privacy.html`
- [ ] Add the privacy policy URL in Play Console → App content → Privacy policy

---

## Data Safety Form (mandatory — blocks submission)

Complete at Play Console → App content → Data safety.

Declare the following:

| Data type | Collected | Shared | Purpose | Required by user |
|-----------|-----------|--------|---------|-----------------|
| Approximate location | No | No | App functionality | No |
| App interactions | No | No | App functionality | No |

Also declare:
- Location data (fine) — collected, not shared — app functionality (map centre, route recording)
- Data is encrypted in transit (HTTPS only)
- Users can request deletion? → No user data stored server-side; all data is on-device

---

## Content Rating Questionnaire (mandatory)

Play Console → App content → App content rating → Start questionnaire.

Expected answers for locationjoystick:
- Category: **Utility / Tools**
- Violence: None
- Sexual content: None
- Language: None
- Controlled substances: None
- User-generated content: None (no UGC features)

Expected result: **Everyone (E)**

---

## Target Audience & Content (mandatory)

Play Console → App content → Target audience and content.

- Target age group: **Everyone** (no adult content, utility app)
- Does the app appeal to children? **No** (technical developer tool — not designed for children)
- This keeps content rating at E (utility app, no adult content)

---

## Store Listing Assets

- [x] **Short description** (≤80 chars): `Mock your GPS location on Android — no root, no ads.`
- [ ] **Full description** (≤4000 chars): use the feature list from `docs/reddit-post.md` as base, expand into Store prose
- [x] **Screenshots**: all 15 in `docs/wiki/screenshots/` (1080×2340) — upload at least 2
- [ ] **Feature graphic** (1024×500 px): does not exist yet — design and upload (shown at top of listing)
- [ ] **App icon** (512×512 px): `docs/wiki/icon.png` is currently **192×192** — must be resized/exported at 512×512 before upload

---

## App Content Declarations

Play Console → App content:

- [ ] Ads: **No ads** — app contains no advertising SDKs or ad placements
- [ ] Sensitive permissions: declare `ACCESS_FINE_LOCATION` and `SYSTEM_ALERT_WINDOW` usage
- [ ] News app: No
- [ ] COVID-19 contact tracing: No

---

## Pre-launch Checklist

- [x] `make lint` passes with zero errors
- [x] `make test` passes
- [ ] `make bundle` produces a signed AAB (env vars `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` set)
- [ ] Internal test track upload succeeds in Play Console
- [ ] Privacy Policy URL resolves in browser (`https://shortcuts.github.io/locationjoystick/privacy.html`)
- [ ] About screen Privacy Policy link opens correctly in-app

---

## Blockers (must resolve before submission)

1. **App icon** — resize `docs/wiki/icon.png` from 192×192 to 512×512
2. **Feature graphic** — create a 1024×500 px banner image
3. **Full store description** — write the Play Store long description
4. **GitHub Pages** — enable in repo settings so the privacy URL resolves
5. **Signed AAB** — run `make bundle` with signing env vars configured
6. **Play Console account** — $25 registration if not done yet
