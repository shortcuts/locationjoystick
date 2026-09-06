# Wiki Contributing Guide

## General writing guidelines

All wiki pages are read by **app users, not developers**. Write every sentence as if the reader has never seen a line of code.

**Rules that apply to every page:**

- No code symbols, class names, method names, permission constants, or file paths — ever. If you need to refer to something technical, describe what it does instead.
  - Bad: `MockLocationService` keeps running in the background.
  - Good: The app keeps running in the background.
  - Bad: The `hot_` ID prefix identifies entries added by this feature.
  - Good: The entries added by this feature are removed when the toggle is turned off. Your own entries are never deleted.
- No internal Android or programming concepts: no "service", "foreground service", "StateFlow", "ViewModel", "dependency injection", "SQL database", "HTTP", "DHCP", "annotation processing", "serialization", "reactive streams", etc. Replace with plain English.
  - Bad: A foreground service runs location updates at 1 Hz.
  - Good: Location updates run continuously in the background.
- No library or framework names in descriptions unless the library itself is the subject (e.g. the Acknowledgements page). Even then, describe what it does for the user, not how it works internally.
- No XML tag literals, code paths, or module paths.
- `<code>` is allowed **only** for UI labels the user must type or read verbatim (e.g. `geo:` links, URL parameters like `lat` and `lon`).
- Active voice, present tense. One idea per sentence.
- Audience test: could a non-technical friend understand every sentence? If not, rewrite.

---

## Adding a new page to the navbar

All nav items live in `docs/wiki/wiki-init.js`, in the `NAV_ITEMS` array near the top of the file.

See the current `NAV_ITEMS` array at the top of `docs/wiki/wiki-init.js` for the live list and label wording — don't copy an example here, it drifts.

Steps:

1. Create `docs/wiki/<page>.html` following the same shell as any existing page (DOCTYPE, head with `style.css` + `@docsearch/css@4`, `<div class="layout"><main>…</main></div>`, `<script src="wiki-init.js"></script>`).
2. Add `{ href: '<page>.html', label: '<Label>' }` to `NAV_ITEMS` at the position where it should appear in the sidebar. Ordering convention: feature pages first (alphabetical within a group), then meta pages (Changelog, Privacy, Acknowledgements) at the end.
3. No other file needs to be edited — `wiki-init.js` injects the sidebar into every page at runtime.

External links (GitHub, issue tracker) go in `EXT_ITEMS` below `NAV_ITEMS`, not in `NAV_ITEMS`.

---

## Maintaining `changelog.html`

`changelog.html` is **generated**, not hand-written — running `scripts/generate-changelog.py`
(`make wiki-changelog`) rebuilds it from every `docs/wiki/changelog/<version>.json` file. Never
hand-edit `changelog.html` directly; edits are overwritten on the next generation.

See @docs/features/whats-new.md, "Maintaining the Changelog", for the authoring workflow and the
JSON schema (`category`/`scope`/`summary` per entry).

Note on the general writing guidelines above: unlike other wiki pages, a changelog entry's
`summary` field must be **plain text only** — no `<code>` — even for a UI literal like a `geo:`
link. Wrap it in single quotes instead (`'geo:'`), since the same string is also rendered by a
Compose `Text` in the in-app popup, which can't render HTML.
