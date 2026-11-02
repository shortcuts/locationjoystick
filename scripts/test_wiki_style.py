#!/usr/bin/env python3
"""Self-check for docs/wiki/style.css. Run directly: python3 scripts/test_wiki_style.py"""

import re
from pathlib import Path

CSS = (Path(__file__).parent.parent / "docs" / "wiki" / "style.css").read_text()
ROOT_PX = 16.0


def css_px(value):
    number = float(re.match(r"[\d.]+", value).group())
    if value.endswith("rem"):
        return number * ROOT_PX
    if value.endswith("px"):
        return number
    raise ValueError(f"unhandled unit: {value}")


def body_px():
    body = re.search(r"body\{[^}]*?font-size:([\d.]+(?:px|rem))", CSS, re.S)
    return css_px(body.group(1))


def heading_sizes(selectors):
    stripped = re.sub(r"/\*.*?\*/", "", CSS, flags=re.S)
    sizes = []
    for selector_list, block in re.findall(r"([^{}]+)\{([^{}]*)\}", stripped):
        names = {s.strip() for s in selector_list.split(",")}
        if names & selectors:
            found = re.findall(r"font-size:([\d.]+(?:px|rem))", block)
            if found:
                sizes.append(css_px(found[-1]))
    return sizes


def test_item_headings_are_not_smaller_than_body():
    body = body_px()
    sizes = heading_sizes({"h4", "h3.item", "h4.item"})
    assert sizes, "no h4 or item heading font-size rule found"
    assert all(size >= body for size in sizes), f"body {body}px, heading sizes {sizes}px"


def test_code_blocks_wrap_long_urls_on_phones():
    start = CSS.rindex("@media(max-width:680px){")
    end = CSS.index("/* ── DocSearch theme ── */", start)
    rule = re.search(r"(?<![\w-])pre\{([^}]*)\}", CSS[start:end])
    assert rule, "no pre rule in the phone media block"
    assert "white-space:pre-wrap" in rule.group(1), rule.group(1)
    assert "overflow-wrap:anywhere" in rule.group(1), rule.group(1)


if __name__ == "__main__":
    test_item_headings_are_not_smaller_than_body()
    test_code_blocks_wrap_long_urls_on_phones()
    print("ok")
