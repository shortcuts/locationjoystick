#!/usr/bin/env python3
"""Self-check for tap_smoke.py. Run directly: python3 scripts/test_tap_smoke.py"""

import importlib.util
import json
from pathlib import Path

HERE = Path(__file__).parent
spec = importlib.util.spec_from_file_location("tap_smoke", HERE / "tap_smoke.py")
ts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ts)


def trials(passes, fails=0, unverified=0, ambiguous=0):
    rows = [{"verdict": "pass", "error_m": 2.0 + i} for i in range(passes)]
    rows += [{"verdict": "fail", "error_m": 20.0 + i} for i in range(fails)]
    rows += [{"verdict": "unverified", "error_m": 5.0} for _ in range(unverified)]
    rows += [{"verdict": "ambiguous", "error_m": None} for _ in range(ambiguous)]
    return rows


def test_location_passes_with_11_of_12():
    assert ts.location_verdict(trials(11, fails=1), True) == ("PASS", "")
    assert ts.location_verdict(trials(12, ambiguous=5), True) == ("PASS", "")


def test_location_fails_with_10_of_12():
    assert ts.location_verdict(trials(10, fails=2), True) == ("FAIL", "below_bar")
    assert ts.location_verdict(trials(11, unverified=1), True) == ("PASS", "")
    assert ts.location_verdict(trials(10, unverified=2), True) == ("FAIL", "below_bar")


def test_location_fails_when_too_few_scored():
    assert ts.location_verdict(trials(11), True) == ("FAIL", "insufficient_scored")


def test_location_fails_without_calibration():
    assert ts.location_verdict(trials(12), False) == ("FAIL", "no_calibration")
    assert ts.location_verdict([], False) == ("FAIL", "no_calibration")


def test_suite_bar_is_19_of_20():
    rows = [{"verdict": "PASS"}] * 19 + [{"verdict": "FAIL"}]
    assert ts.suite_verdict(rows) == (19, 20, True)
    rows = [{"verdict": "PASS"}] * 18 + [{"verdict": "FAIL"}] * 2
    assert ts.suite_verdict(rows) == (18, 20, False)


def test_percentile_matches_bench_index_rule():
    errs = [1.0, 2.0, 3.0, 4.0]
    assert ts.percentile(errs, 0.5) == 3.0  # int(0.5 * 4) = 2
    assert ts.percentile(errs, 0.9) == 4.0  # min(3, int(3.6)) = 3
    assert ts.percentile([], 0.5) != ts.percentile([], 0.5)  # NaN


def test_summary_line():
    assert ts.summary_line(19, 20, True) == "TAP SMOKE: 19/20 locations pass (bar 19/20) -> PASS"
    assert ts.summary_line(18, 20, False) == "TAP SMOKE: 18/20 locations pass (bar 19/20) -> FAIL"


def test_location_file_is_20_unique_in_range():
    locs = ts.load_locations(HERE / "tap_smoke_locations.json")
    assert len(locs) == 20
    assert len({(l["lat"], l["lon"]) for l in locs}) == 20
    assert all(-90 <= l["lat"] <= 90 and -180 <= l["lon"] <= 180 for l in locs)
    assert all(l["name"] and l["kind"] for l in locs)


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for t in tests:
        t()
    print(f"ok: {len(tests)} checks")
