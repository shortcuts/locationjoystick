#!/usr/bin/env python3
"""Self-check for the release Hilt guard. Run directly: python3 scripts/test_check_release_hilt.py"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_release_hilt import dex_references, lost_bindings, obfuscated_names, view_models_in

SETTINGS = "com.app.SettingsViewModel"


def test_finds_view_model_with_arguments_and_visibility():
    source = (
        "package com.app\n\n@HiltViewModel\nclass SettingsViewModel\n\n"
        "@HiltViewModel(assistedFactory = F::class)\ninternal class OtherViewModel\n\nclass Plain\n"
    )
    assert view_models_in(source) == [SETTINGS, "com.app.OtherViewModel"]


def test_finds_view_model_after_another_annotation():
    source = "package com.app\n\n@HiltViewModel\n@OptIn(Api::class)\nclass SettingsViewModel\n"
    assert view_models_in(source) == [SETTINGS]


def test_ignores_import_and_comment_mentions():
    source = "package com.app\n\nimport a.HiltViewModel\n\n/** See @HiltViewModel docs. */\nclass Plain\n"
    assert view_models_in(source) == []


def test_maps_classes_and_skips_members():
    mapping = f"{SETTINGS} -> d85:\n    int count -> a\n    1:2:void run():3:4 -> b\n"
    assert obfuscated_names(mapping) == {SETTINGS: "d85"}


def test_reads_instances_and_strings_from_dexdump():
    lines = [
        '1a01 605a |0086: const-string v1, "d85" // string@5a60',
        "2200 1234 |0010: new-instance v12, La/d85; // type@1234",
    ]
    assert dex_references(lines) == ({"a.d85"}, {"d85"})


def test_bound_view_model_passes():
    assert lost_bindings([SETTINGS], {SETTINGS: "d85"}, {"d85"}, {"d85"}) == []


def test_view_model_without_factory_or_key_is_lost():
    assert lost_bindings([SETTINGS], {SETTINGS: "d85"}, set(), {"d85"}) == [SETTINGS]
    assert lost_bindings([SETTINGS], {SETTINGS: "d85"}, {"d85"}, set()) == [SETTINGS]


def test_view_model_removed_by_r8_is_lost():
    assert lost_bindings([SETTINGS], {}, {"d85"}, {"d85"}) == [SETTINGS]


if __name__ == "__main__":
    for name, test in list(globals().items()):
        if name.startswith("test_"):
            test()
    print("ok")
