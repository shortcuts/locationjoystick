#!/usr/bin/env python3
"""Self-check for the release smoke. Run directly: python3 scripts/test_release_smoke.py"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from release_smoke import crashes_of, parse_nodes


def crash_log(process):
    return (
        "E AndroidRuntime: FATAL EXCEPTION: main\n"
        f"E AndroidRuntime: Process: {process}, PID: 1\n"
        "E AndroidRuntime: java.lang.RuntimeException: Cannot create an instance of class d85\n"
        "E AndroidRuntime: \tat gh2.n(r8-map-id:15)\n"
        "E AndroidRuntime: Caused by: java.lang.NoSuchMethodException: d85.<init> []\n"
    )


def test_reports_crash_of_this_app():
    crashes = crashes_of(crash_log("com.locationjoystick.app"))
    assert len(crashes) == 2
    assert "NoSuchMethodException" in crashes[1]


def test_ignores_crash_of_another_app():
    assert crashes_of(crash_log("com.other.app")) == []


def test_ignores_empty_buffer():
    assert crashes_of("--------- beginning of crash\n") == []


def test_centres_nodes_and_keeps_first_text():
    dump = (
        "<hierarchy><node text='Settings' bounds='[0,10][100,30]'>"
        "<node text='' bounds='[0,0][1,1]'/><node text='Settings' bounds='[0,500][100,600]'/>"
        "</node></hierarchy>"
    )
    assert parse_nodes(dump) == {"Settings": (50, 20)}


if __name__ == "__main__":
    for name, test in list(globals().items()):
        if name.startswith("test_"):
            test()
    print("ok")
