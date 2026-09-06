#!/usr/bin/env python3
"""
Generate docs/wiki/changelog.html from the per-version JSON files under
docs/wiki/changelog/.

Each docs/wiki/changelog/<version>.json is the single human-authored source for
both this page and the in-app What's New popup (see docs/features/whats-new.md).
This script reads every one of those files, groups each version's entries by
category (feat before fix) then scope (alphabetical), and writes static HTML —
no client-side rendering.

Usage:
    python3 scripts/generate-changelog.py

Run this after adding or editing a version's JSON file. Never hand-edit
docs/wiki/changelog.html directly — it is fully overwritten on the next run.
"""

import json
from datetime import date
from html import escape
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CHANGELOG_DIR = REPO_ROOT / "docs" / "wiki" / "changelog"
OUTPUT_FILE = REPO_ROOT / "docs" / "wiki" / "changelog.html"

CATEGORY_LABELS = [
    ("feat", "New &amp; Improved"),
    ("fix", "Fixes"),
]

HEAD = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Changelog — locationjoystick</title>
  <link rel="icon" href="icon.png" type="image/png">
  <meta name="description" content="See what's new in each locationjoystick release: new features, improvements, and fixes.">
  <link rel="canonical" href="https://shortcuts.github.io/locationjoystick/changelog.html">
  <meta property="og:type" content="website">
  <meta property="og:site_name" content="locationjoystick">
  <meta property="og:title" content="Changelog — locationjoystick">
  <meta property="og:description" content="See what's new in each locationjoystick release: new features, improvements, and fixes.">
  <meta property="og:url" content="https://shortcuts.github.io/locationjoystick/changelog.html">
  <meta property="og:image" content="https://shortcuts.github.io/locationjoystick/screenshots/01_idle_playstore.png">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="Changelog — locationjoystick">
  <meta name="twitter:description" content="See what's new in each locationjoystick release: new features, improvements, and fixes.">
  <meta name="twitter:image" content="https://shortcuts.github.io/locationjoystick/screenshots/01_idle_playstore.png">
  <link rel="stylesheet" href="style.css">
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@docsearch/css@4">
</head>
<body>
<div class="layout">

  <main>
    <h1 id="changelog">Changelog</h1>
    <p>What's new in each release. Download the latest APK from <a href="https://github.com/shortcuts/locationjoystick/releases/latest" target="_blank" rel="noopener">GitHub Releases</a>.</p>

"""

FOOT = """  </main>

</div>
<script src="wiki-init.js"></script>
</body>
</html>
"""


def load_versions():
    versions = []
    for path in CHANGELOG_DIR.glob("*.json"):
        versions.append(json.loads(path.read_text()))
    versions.sort(key=lambda v: tuple(int(p) for p in v["version"].split(".")), reverse=True)
    return versions


def render_version(entry):
    version = entry["version"]
    version_id = "v" + version.replace(".", "")
    release_date = date.fromisoformat(entry["date"])
    formatted_date = f"{release_date:%B} {release_date.day}, {release_date:%Y}"

    lines = [
        f'    <h3 id="{version_id}"><a href="https://github.com/shortcuts/locationjoystick/releases/tag/v{version}" '
        f'target="_blank" rel="noopener">{version}</a>, {formatted_date}</h3>'
    ]

    by_category = {}
    for item in entry["entries"]:
        by_category.setdefault(item["category"], []).append(item)

    for category, label in CATEGORY_LABELS:
        items = by_category.get(category, [])
        if not items:
            continue
        lines.append(f"    <h4>{label}</h4>")
        by_scope = {}
        for item in items:
            by_scope.setdefault(item["scope"], []).append(item)
        for scope in sorted(by_scope):
            lines.append(f"    <h5>{escape(scope)}</h5>")
            lines.append("    <ul>")
            for item in by_scope[scope]:
                lines.append(f"      <li>{escape(item['summary'])}</li>")
            lines.append("    </ul>")

    return "\n".join(lines)


def main():
    versions = load_versions()
    sections = "\n\n".join(render_version(v) for v in versions)
    OUTPUT_FILE.write_text(HEAD + sections + "\n\n" + FOOT)
    print(f"Wrote {OUTPUT_FILE} ({len(versions)} versions)")


if __name__ == "__main__":
    main()
