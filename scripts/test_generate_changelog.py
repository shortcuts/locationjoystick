#!/usr/bin/env python3
"""Self-check for generate-changelog.py. Run directly: python3 scripts/test_generate_changelog.py"""

import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "generate_changelog", Path(__file__).parent / "generate-changelog.py"
)
gc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gc)


def test_version_sort_numeric_not_alphabetical():
    entries = [{"version": "0.2.0"}, {"version": "0.10.0"}, {"version": "0.9.0"}]
    entries.sort(key=lambda v: tuple(int(p) for p in v["version"].split(".")), reverse=True)
    assert [e["version"] for e in entries] == ["0.10.0", "0.9.0", "0.2.0"]


def test_render_version_groups_by_category_then_scope():
    entry = {
        "version": "1.0.0",
        "date": "2026-01-02",
        "entries": [
            {"category": "fix", "scope": "Zebra", "summary": "z fix"},
            {"category": "feat", "scope": "Alpha", "summary": "a feat"},
            {"category": "fix", "scope": "Alpha", "summary": "a fix"},
        ],
    }
    html = gc.render_version(entry)
    assert html.index("New &amp; Improved") < html.index("Fixes")
    # scopes alphabetical within a category
    assert html.index("<h5>Alpha</h5>") < html.index("<h5>Zebra</h5>")
    assert '<h3 id="v100">' in html
    assert "January 2, 2026" in html


def test_render_version_escapes_summary_and_omits_empty_category():
    entry = {
        "version": "1.0.0",
        "date": "2026-01-01",
        "entries": [{"category": "feat", "scope": "General", "summary": "a & b < c"}],
    }
    html = gc.render_version(entry)
    assert "<h4>Fixes</h4>" not in html
    assert "a &amp; b &lt; c" in html


if __name__ == "__main__":
    test_version_sort_numeric_not_alphabetical()
    test_render_version_groups_by_category_then_scope()
    test_render_version_escapes_summary_and_omits_empty_category()
    print("OK")
