#!/usr/bin/env python3
"""Self-check for tap_smoke.py. Run directly: python3 scripts/test_tap_smoke.py"""

import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).parent
spec = importlib.util.spec_from_file_location("tap_smoke", HERE / "tap_smoke.py")
ts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ts)
INF = float("inf")


def test_row_passes_when_p90_is_exactly_15m():
    assert ts.row_verdict([1.0] * 10 + [15.0] * 2, rejected=0, ambiguous=0) == ("PASS", "")


def test_row_fails_when_p90_is_over_15m():
    assert ts.row_verdict([1.0] * 10 + [15.1] * 2, rejected=0, ambiguous=0) == ("FAIL", "p90_over_15m")


def test_row_passes_at_20_percent_rejected_and_fails_at_21():
    assert ts.row_verdict([1.0] * 12, rejected=3, ambiguous=0) == ("PASS", "")
    assert ts.row_verdict([1.0] * 79, rejected=21, ambiguous=0) == ("FAIL", "rejected_over_20pct")


def test_row_skips_ambiguous_taps_and_counts_them_in_no_rate():
    assert ts.row_verdict([1.0] * 12, rejected=0, ambiguous=5) == ("PASS", "")


def test_low_ncc_tap_is_a_miss_that_fails_the_row():
    assert ts.tap_error_m(0.39, 2.0) == INF
    assert ts.tap_error_m(0.4, 2.0) == 2.0
    assert ts.row_verdict([1.0] * 10 + [INF] * 2, rejected=0, ambiguous=0) == ("FAIL", "p90_over_15m")


def test_row_fails_when_fewer_than_12_scored():
    assert ts.row_verdict([1.0] * 11, rejected=0, ambiguous=0) == ("FAIL", "insufficient_scored")


def test_suite_bar_is_19_of_20():
    rows = [{"verdict": "PASS"}] * 19 + [{"verdict": "FAIL"}]
    assert ts.suite_verdict(rows) == (19, 20, True)
    rows = [{"verdict": "PASS"}] * 18 + [{"verdict": "FAIL"}] * 2
    assert ts.suite_verdict(rows) == (18, 20, False)


def test_suite_fails_when_an_extra_scenario_row_fails():
    rows = [{"verdict": "PASS", "scenario": "normal"}] * 20 + [{"verdict": "FAIL", "scenario": "twist"}]
    assert ts.suite_verdict(rows) == (20, 20, False)


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


def test_bench_step_is_bounded_by_timeout():
    # A hung bench step must not stall the gate (a run once sat ~50 min on one location).
    import time
    import tempfile
    hang = Path(tempfile.mkdtemp()) / "hang.py"
    hang.write_text("import time\ntime.sleep(4)\n")
    ts.BENCH = hang
    ts.STEP_TIMEOUT_S = {"walkcap": 0.5}
    t0 = time.time()
    ok, tail = ts._bench(sys.executable, None, "walkcap")
    assert time.time() - t0 < 2.0
    assert not ok and "timed out" in tail


def test_prior_fallbacks_counts_the_bench_fallback_lines():
    log = ("feet anchor: detected row 0.91h is outside the prior -> prior fallback\n"
           "feet anchor: no figure found -> prior fallback\n"
           "feet pixel detected: 540 1400\n")
    assert ts.prior_fallbacks(log) == 2
    assert ts.prior_fallbacks("feet pixel detected: 540 1400\n") == 0


def _pref_entry(key, value):
    """One DataStore PreferencesMap entry holding a string value (lengths stay under 128 bytes)."""
    k, v = key.encode(), value.encode()
    inner = bytes([0x2A, len(v)]) + v
    body = bytes([0x0A, len(k)]) + k + bytes([0x12, len(inner)]) + inner
    return bytes([0x0A, len(body)]) + body


def test_drop_pref_removes_only_the_named_entry():
    data = _pref_entry("a", "1") + _pref_entry(ts.TAP_CALIBRATION_KEY, "2") + _pref_entry("b", "3")
    assert ts.drop_pref(data, ts.TAP_CALIBRATION_KEY) == _pref_entry("a", "1") + _pref_entry("b", "3")
    try:
        ts.drop_pref(_pref_entry("a", "1"), ts.TAP_CALIBRATION_KEY)
    except RuntimeError:
        pass
    else:
        raise AssertionError("missing key must raise")


def test_report_line_has_scenario_and_rate_columns():
    row = {"index": 1, "name": "X", "lat": 1.0, "lon": 2.0, "kind": "dense", "device": "P", "resolution": "1x2",
           "density": "420", "scenario": "normal", "scored": 12, "rejected": 1, "ambiguous": 2, "unverified": 1,
           "p50": 6.0, "p90": 9.0, "reaim_m": 3.5, "verdict": "PASS", "reason": "", "prior_fallback": 3}
    line = ts.render_report([row]).splitlines()[2]
    assert line == ("| 1 | X | 1.0 | 2.0 | dense | P | 1x2 | 420 | normal | 12 | 1 | 2 | 1 | 3 | 6.0 | 9.0 | 3.5 "
                    "| PASS |  |")


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for t in tests:
        t()
    print(f"ok: {len(tests)} checks")
