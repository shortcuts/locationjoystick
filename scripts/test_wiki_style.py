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


def mobile_rules():
    marker = "@media(max-width:680px){"
    rules = {}
    position = CSS.find(marker)
    while position != -1:
        start = position + len(marker)
        depth, end = 1, start
        while depth:
            depth += {"{": 1, "}": -1}.get(CSS[end], 0)
            end += 1
        block = re.sub(r"/\*.*?\*/", "", CSS[start:end], flags=re.S)
        for selector_list, declarations in re.findall(r"([^{}]+)\{([^{}]*)\}", block):
            selector = " ".join(selector_list.split())
            rules.setdefault(selector, []).append(declarations)
        position = CSS.find(marker, end)
    return rules


def min_height_px(declarations):
    found = re.findall(r"min-height:([\d.]+)px", declarations)
    return float(found[-1]) if found else 0.0


def test_mobile_nav_and_toc_links_are_tall_enough_to_tap():
    rules = mobile_rules()
    for selector in ("nav a", ".nav-outline a", ".page-toc a"):
        heights = [min_height_px(d) for d in rules.get(selector, [])]
        assert max(heights, default=0.0) >= 48, f"{selector} min-height at 680px: {heights}"


DOCSEARCH_THEME_VARS = [
    "--docsearch-primary-color", "--docsearch-secondary-text-color", "--docsearch-text-color",
    "--docsearch-highlight-color", "--docsearch-focus-color", "--docsearch-muted-color",
    "--docsearch-muted-color-darker", "--docsearch-subtle-color", "--docsearch-soft-primary-color",
    "--docsearch-background-color", "--docsearch-container-background", "--docsearch-icon-color",
    "--docsearch-logo-color", "--docsearch-error-color", "--docsearch-success-color",
    "--docsearch-modal-background", "--docsearch-modal-shadow", "--docsearch-searchbox-background",
    "--docsearch-searchbox-focus-background", "--docsearch-hit-color", "--docsearch-hit-highlight-color",
    "--docsearch-hit-background", "--docsearch-hit-shadow", "--docsearch-key-background",
    "--docsearch-key-color", "--docsearch-key-pressed-shadow", "--docsearch-footer-background",
    "--docsearch-footer-shadow", "--docsearch-search-button-background",
    "--docsearch-search-button-text-color", "--docsearch-dropdown-menu-background",
    "--docsearch-dropdown-menu-item-hover-background", "--docsearch-border-radius",
]


def test_docsearch_theme_vars_are_set_on_body_not_root():
    css = re.sub(r"/\*.*?\*/", "", CSS, flags=re.S)
    match = re.search(r"([^{}]+)\{([^{}]*--docsearch-primary-color:[^{}]*)\}", css)
    assert match, "no DocSearch theme block"
    selector, body = match.group(1).strip(), match.group(2)
    assert selector == "body", f"DocSearch :root values load after this file and win: {selector!r}"
    missing = [name for name in DOCSEARCH_THEME_VARS if f"{name}:" not in body]
    assert not missing, f"DocSearch theme vars not set: {missing}"


if __name__ == "__main__":
    test_item_headings_are_not_smaller_than_body()
    test_code_blocks_wrap_long_urls_on_phones()
    test_shot_floats_beside_text_from_1000px()
    test_right_margin_shot_only_from_1320px()
    test_text_column_stays_wide_beside_shot_1000_to_1319px()
    test_mobile_nav_and_toc_links_are_tall_enough_to_tap()
    test_docsearch_theme_vars_are_set_on_body_not_root()
    print("ok")
