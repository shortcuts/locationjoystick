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

## Page structure

### Content type

Every page is one of four types; keep each page to a single type.

| Type | Purpose | Structure |
|---|---|---|
| **Tutorial** | Learn by doing on a topic for the first time. | Goal statement → step-by-step instructions → verifiable result after each step → final working outcome. |
| **How-to guide** | Complete a specific task or solve a problem. | Goal statement → prerequisites → numbered steps → expected result. |
| **Reference** | Look up facts: options, parameters, controls, API endpoints. | Consistent format per entry (name, description, default) in tables or lists; prose only for behavior a table cannot carry. |
| **Explanation** | Understand why a feature works the way it does. | Context → core concept → alternatives and trade-offs → broader perspective. |

Do not mix types on one page. If content fits multiple types (e.g. an explanation alongside how-to steps), create separate pages and link between them.

### Length and clarity

- Sentences must be under 20 words. One idea per sentence.
- Settings, options, controls, and API parameters go in a table (name, description, default) or bullet list — never paragraph form.
- Link to an explanation once, then reuse the link text or reference. Do not repeat the same explanation inline.
- Cut history, marketing tone ("powerful", "seamless"), and repeated phrasing. Every sentence must earn its place.

### Reference pages

A reference page opens with a table listing the items (name, description, default). Add prose paragraphs only for behavior a table cannot carry. Keep the table as the first and primary content.

---

## Developer reference sections

A wiki page may include one clearly labelled "For developers" reference section, such as an API reference for integrators. This section is an exception to the general rules above: it may contain code symbols, HTTP paths, JSON fields, and wire-level protocol details. All other content on the page must follow the general writing guidelines.

Each reference section must:
- Be headed with "(for developers)" to set expectations
- Open with a plain-language paragraph explaining what users need to know (even if they never read the reference), linking to related app features
- Keep technical detail to endpoints, request/response format, and error codes — no class names, module paths, or implementation details

---

## Writing or editing wiki prose

Whenever a change adds or edits prose on a `docs/wiki/*.html` page, run the `/no-ai-slop`
skill on the page first, then the `/documentation` skill, before considering the page done.
`/no-ai-slop` cuts filler and AI-sounding phrasing while preserving meaning; `/documentation`
checks structure against the Diátaxis type rules in "Page structure" above. Judge
the result against the audience test above — could a non-technical friend understand every
sentence? — not against either skill's own output in isolation.

This applies to prose edits only. Screenshot regeneration, nav wiring in `wiki-init.js`, and
`changelog.html` generation (see below) are unaffected.

---

## Navigation and page moves

### Sidebar structure

The sidebar is organized into five groups in fixed order:

| Group | Purpose | Pages |
|---|---|---|
| **Start** | First-time setup and getting going. | Getting Started. |
| **Use** | Goal-based task pages first, then everyday features you navigate while using the app. | Common tasks (goal pages, planned), Map, Overlays, Tap to Walk, Routes, Favorites, Location Links, Group Sync. |
| **Configure** | Settings and data backup or transfer. | Settings, Backup & Transfer. |
| **Help** | Fixing problems and integrations for developers. | Troubleshooting, Control API. |
| **About** | Meta pages for the project. | Changelog, Privacy, Acknowledgements. |

All nav items live in `docs/wiki/wiki-init.js`, in the `NAV_ITEMS` array. Each group is an object with a `group` name and an `items` array. See the current `NAV_ITEMS` at the top of `docs/wiki/wiki-init.js` for the live list and label wording — don't copy an example here, it drifts.

### Adding a new page

1. Create `docs/wiki/<page>.html` following the same shell as any existing page (DOCTYPE, head with metadata and `style.css` + `@docsearch/css@4`, `<div class="layout"><main>…</main></div>`, `<script src="wiki-init.js"></script>`).
2. Add `{ href: '<page>.html', label: '<Label>' }` to the `items` array of the group where it belongs. Use `&amp;` for ampersands in labels. See the table above to choose the right group.
3. No other file needs to be edited — `wiki-init.js` injects the sidebar into every page at runtime.

External links (GitHub, issue tracker) go in `EXT_ITEMS` below `NAV_ITEMS`, not in `NAV_ITEMS`.

### When to merge pages instead of adding a new one

Merge a feature into an existing page if:
- The feature is a variation or sub-topic of the existing page's subject, **or**
- The feature would have under roughly 300 words of content on its own.

Worked examples:
- Tap to Walk gets its own page. It is about half of the Overlays content (over 300 words), so it no longer merges.
- Routes and Favorites are two pages. Routes is over 2000 words on its own, so Favorites is not merged into it.
- Language/Localization (a setting) merges into Settings.

Add a new page only if the feature is a distinct user goal that does not fit an existing group item. A new group needs explicit reasoning and a user decision — do not add one on your own. The **Common tasks** group in the Use group is the decided exception: it holds goal-based pages (a user arrives with a goal and needs the page that answers it), and it sits at the top of Use.

### Redirect stubs for moved or merged pages

When a page is moved or merged into another:

1. Keep the old file as a redirect stub.
2. Remove the old file from `NAV_ITEMS` and from `sitemap.xml`.
3. Structure: copy the format of `docs/wiki/share.html`. The stub includes:
   - A `<title>` matching the new location (e.g. "Location Links — locationjoystick").
   - A `<meta http-equiv="refresh">` redirecting to the new page's relative URL (and anchor if merged into a section).
   - A `<link rel="canonical">` pointing to the custom domain `https://locationjoystick.shrtcts.fr/<new-page>.html`.
   - A `<meta name="robots" content="noindex">` to exclude it from search.
   - One plain-text link in the `<body>`: "This page moved. See <a href="...">Page Name</a>."

Example: `docs/wiki/tap-to-walk.html` becomes:
```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Overlays — locationjoystick</title>
  <meta http-equiv="refresh" content="0; url=overlays.html#tap-to-walk">
  <link rel="canonical" href="https://locationjoystick.shrtcts.fr/overlays.html">
  <meta name="robots" content="noindex">
</head>
<body>
<p>This page moved. See <a href="overlays.html#tap-to-walk">Overlays</a>.</p>
</body>
</html>
```

Stubs are never deleted — old links from outside the wiki will resolve.

---

## SEO basics

Every real page (not a redirect stub) must have:

### Meta tags

- **Title:** `<title>` of the form "<Page name> — locationjoystick". Must be unique per page. Examples: "Map Screen — locationjoystick", "Settings — locationjoystick".
- **Description:** A unique `<meta name="description">` containing one sentence under 160 characters in plain language. Describes what the page covers. Example: "Move your spoofed GPS location on the map: tap to teleport, long-press to walk, and control real-time position changes from one screen."
- **Open Graph and Twitter:** Match title and description to `og:title`, `og:description`, `twitter:title`, `twitter:description` for social sharing.
- **Canonical and URL:** `<link rel="canonical">` pointing to `https://locationjoystick.shrtcts.fr/<page>.html`. Set `og:url` to the same canonical URL.
- **Stubs:** Stub canonicals and `og:image`/`twitter:image` also use `https://locationjoystick.shrtcts.fr`; `robots.txt` and `sitemap.xml` list real pages only.

### Headings and IDs

- Every page has exactly one `<h1>` with a unique `id` attribute.
- Every `<h2>` and `<h3>` has a unique `id`. The sidebar outline uses these IDs to build the page's table of contents.
- Use descriptive IDs matching the heading text (e.g. `id="tap-to-move"` for "Tap to Move").

### Links and images

- Use descriptive link text. Never "click here" or "more". Examples: good: "See the [troubleshooting guide](troubleshooting.html)"; bad: "[click here](troubleshooting.html)".
- Link to sibling pages using relative `href`s (e.g. `href="map.html"`, not full URLs).
- Link each related page once per section; reuse the link text if you need to refer to it again.
- Every image must have `alt` text describing its content.

### Sitemap

- Add new real pages to `sitemap.xml` with `<loc>https://locationjoystick.shrtcts.fr/<page>.html</loc>`.
- Do not list redirect stubs in `sitemap.xml`.

---

## Maintaining `changelog.html`

`changelog.html` is **generated**, not hand-written — running `scripts/generate-changelog.py`
(`make wiki-changelog`) rebuilds it from every `docs/wiki/changelog/<version>.json` file. Never
hand-edit `changelog.html` directly; edits are overwritten on the next generation.

See docs/features/whats-new.md, "Maintaining the Changelog", for the authoring workflow and the
JSON schema (`category`/`scope`/`summary` per entry).

Note on the general writing guidelines above: unlike other wiki pages, a changelog entry's
`summary` field must be **plain text only** — no `<code>` — even for a UI literal like a `geo:`
link. Wrap it in single quotes instead (`'geo:'`), since the same string is also rendered by a
Compose `Text` in the in-app popup, which can't render HTML.
