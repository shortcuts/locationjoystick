#!/usr/bin/env python3
"""Self-check for pokeminers_fetch.py. Run directly: python3 scripts/test_pokeminers_fetch.py"""

import importlib.util
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).parent
spec = importlib.util.spec_from_file_location("pokeminers_fetch", HERE / "pokeminers_fetch.py")
pf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pf)


def settings(interaction, pulse=50.0):
    return {"interactionRangeMeters": interaction, "whitePulseRadiusMeters": pulse}


def test_find_settings_reads_the_interaction_template():
    entries = [
        {"templateId": "OTHER", "data": {}},
        {"templateId": pf.TEMPLATE, "data": {"mapObjectsInteractionRangeSettings": settings(80.0)}},
    ]
    assert pf.find_settings(entries) == settings(80.0)


def test_find_settings_reads_the_older_settings_key():
    entries = [{"templateId": pf.TEMPLATE, "data": {"obInteractionRangeSettings": settings(40.0)}}]
    assert pf.find_settings(entries) == settings(40.0)


def test_find_settings_returns_none_when_template_missing():
    assert pf.find_settings([{"templateId": "OTHER", "data": {}}]) is None


def test_parse_dir_date_reads_the_release_stamp():
    name = "gm_v2_apk0327_2024.09.04-10.44.08"
    assert pf.parse_dir_date(name) == datetime(2024, 9, 4, 10, 44, 8)


def test_parse_dir_date_rejects_other_names():
    assert pf.parse_dir_date("latest") is None


def test_radius_changes_reports_only_the_change_with_its_date():
    points = [
        (datetime(2019, 1, 1), "old", settings(40.0)),
        (datetime(2020, 1, 1), "mid", settings(40.0)),
        (datetime(2021, 1, 1), "new", settings(80.0)),
        (datetime(2022, 1, 1), "skipped", None),
    ]
    assert list(pf.radius_changes(points)) == [
        (datetime(2021, 1, 1), "new", "interactionRangeMeters", 40.0, 80.0),
    ]


def test_radius_changes_sorts_by_date_before_comparing():
    points = [
        (datetime(2021, 1, 1), "new", settings(80.0)),
        (datetime(2019, 1, 1), "old", settings(40.0)),
    ]
    assert list(pf.radius_changes(points)) == [
        (datetime(2021, 1, 1), "new", "interactionRangeMeters", 40.0, 80.0),
    ]


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print(f"ok {name}")
