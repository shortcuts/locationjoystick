#!/usr/bin/env python3
"""Self-check for the phone nav in docs/wiki. Run directly: python3 scripts/test_wiki_nav.py"""

import re
from pathlib import Path

WIKI = Path(__file__).resolve().parent.parent / "docs" / "wiki"
CSS = (WIKI / "style.css").read_text(encoding="utf-8")
JS = (WIKI / "wiki-init.js").read_text(encoding="utf-8")


def phone_block():
    start = CSS.rindex("@media(max-width:680px){")
    end = CSS.index("/* ── DocSearch theme ── */", start)
    return CSS[start:end]


def test_header_stays_sticky_on_phones():
    block = phone_block()
    assert re.search(r"\.sidebar\{[^}]*position:sticky", block)
    assert "position:relative" not in block


def test_nav_opens_as_fixed_panel_over_content():
    assert re.search(r"\bnav\{[^}]*position:fixed", phone_block())


def test_escape_closes_nav():
    assert "e.key === 'Escape'" in JS


def test_link_tap_closes_nav():
    assert "closest('a')" in JS


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print(f"ok {name}")
