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


def media_bodies(query):
    target = re.sub(r"\s", "", query)
    bodies = []
    for match in re.finditer(r"@media([^{]*)\{", CSS):
        if re.sub(r"\s", "", match.group(1)) != target:
            continue
        depth, i = 1, match.end()
        while depth:
            depth += {"{": 1, "}": -1}.get(CSS[i], 0)
            i += 1
        bodies.append(CSS[match.end():i - 1])
    return bodies


def has_shot_rule(body):
    found = re.search(r"\.has-shot\{([^}]*)\}", body)
    return found.group(1) if found else ""


GRID_QUERY = "(min-width:1000px)"
TEXT_BAND_QUERY = "(min-width:1000px) and (max-width:1319px)"
WIDE_QUERY = "(min-width:1320px)"


def test_shot_floats_beside_text_from_1000px():
    bodies = media_bodies(GRID_QUERY)
    assert bodies, "no @media(min-width:1000px) block"
    rules = [has_shot_rule(b) for b in bodies]
    assert any("grid-template-columns:minmax(0,1fr) 200px" in r for r in rules), rules


def test_right_margin_shot_only_from_1320px():
    assert "margin-right:-232px" in has_shot_rule(media_bodies(WIDE_QUERY)[0])
    bodies = media_bodies(GRID_QUERY)
    assert bodies, "no @media(min-width:1000px) block"
    for body in bodies:
        assert "margin-right" not in has_shot_rule(body), body


def test_text_column_stays_wide_beside_shot_1000_to_1319px():
    bodies = media_bodies(TEXT_BAND_QUERY)
    assert bodies, "no @media(min-width:1000px) and (max-width:1319px) block"
    assert any(re.search(r"main\{[^}]*max-width:800px", b) for b in bodies)


if __name__ == "__main__":
    test_item_headings_are_not_smaller_than_body()
    test_code_blocks_wrap_long_urls_on_phones()
    test_shot_floats_beside_text_from_1000px()
    test_right_margin_shot_only_from_1320px()
    test_text_column_stays_wide_beside_shot_1000_to_1319px()
    print("ok")
