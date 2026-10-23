#!/usr/bin/env python3
"""Fetch PokeMiners game master and detector assets for the tap-to-walk ring model.

Three steps, each run on its own with a flag:
  default          write the committed fixture from latest/latest.json
  --history        print when each interaction radius changed across previous_game_masters
  --assets         download detector templates into the git-ignored scripts/registration-bench/

The fixture pins the ring radii the app uses. Its unit test fails when they drift.
Run: python3 scripts/pokeminers_fetch.py [--history] [--assets]
"""

import argparse
import json
import re
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / "core/common/src/test/resources/pokeminers/interaction_range_settings.json"
BENCH = ROOT / "scripts/registration-bench/pokeminers"
GAME_MASTERS = "PokeMiners/game_masters"
ASSETS = "PokeMiners/pogo_assets"
TEMPLATE = "MAP_OBJECTS_INTERACTION_RANGE_SETTINGS"
ASSET_FOLDERS = [
    "Images/Loading Screens",
    "Images/Warnings",
    "Images/Pokestops and Gyms",
    "Images/Weather",
    "3D Assets/Characters",
]
IMAGE_SUFFIXES = (".png", ".jpg", ".jpeg", ".webp")
DATE_RE = re.compile(r"(\d{4})\.(\d{2})\.(\d{2})-(\d{2})\.(\d{2})\.(\d{2})$")


def _request(url):
    headers = {"User-Agent": "locationjoystick-pokeminers-fetch"}
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=120) as r:
        return r.read()


def api(path):
    return json.loads(_request(f"https://api.github.com/{path}"))


def head_sha(repo):
    return api(f"repos/{repo}/commits?per_page=1")[0]["sha"]


def raw(repo, sha, path):
    return f"https://raw.githubusercontent.com/{repo}/{sha}/{path}"


def find_settings(entries):
    """Return the interaction-range settings from a game master template list, or None."""
    for entry in entries:
        if entry.get("templateId") == TEMPLATE:
            # Older game masters name the settings key differently (obInteractionRangeSettings).
            return next(v for k, v in entry["data"].items() if k != "templateId")
    return None


def parse_dir_date(name):
    m = DATE_RE.search(name)
    return datetime(*map(int, m.groups())) if m else None


def radius_changes(points):
    """Yield (date, dir, key, old, new) for each radius that differs from the previous known version."""
    known = sorted((p for p in points if p[2] is not None), key=lambda p: p[0])
    previous = {}
    for date, name, settings in known:
        for key, value in settings.items():
            if key in previous and previous[key] != value:
                yield date, name, key, previous[key], value
            previous[key] = value


def write_fixture():
    sha = head_sha(GAME_MASTERS)
    timestamp = int(_request(raw(GAME_MASTERS, sha, "latest/timestamp.txt")).decode().strip())
    settings = find_settings(json.loads(_request(raw(GAME_MASTERS, sha, "latest/latest.json"))))
    if settings is None:
        raise SystemExit(f"{TEMPLATE} missing from latest.json")
    fixture = {
        "source": f"https://github.com/{GAME_MASTERS}",
        "commit": sha,
        "timestamp": timestamp,
        "templateId": TEMPLATE,
        "settings": settings,
    }
    FIXTURE.parent.mkdir(parents=True, exist_ok=True)
    FIXTURE.write_text(json.dumps(fixture, indent=2) + "\n")
    print(f"wrote {FIXTURE.relative_to(ROOT)} (timestamp {timestamp}, commit {sha})")


def walk_history():
    """Read every previous game master once; cache per directory so an interrupted run resumes."""
    sha = head_sha(GAME_MASTERS)
    cache_path = BENCH / "history.json"
    cache = json.loads(cache_path.read_text()) if cache_path.exists() else {}
    dirs = [d["name"] for d in api(f"repos/{GAME_MASTERS}/contents/previous_game_masters?ref={sha}")]
    BENCH.mkdir(parents=True, exist_ok=True)
    for name in dirs:
        if name in cache or parse_dir_date(name) is None:
            continue
        path = f"previous_game_masters/{name}/game_master.json"
        cache[name] = find_settings(json.loads(_request(raw(GAME_MASTERS, sha, path))))
        cache_path.write_text(json.dumps(cache, indent=1))
    points = [(parse_dir_date(n), n, cache[n]) for n in dirs if n in cache and parse_dir_date(n)]
    for date, name, key, old, new in radius_changes(points):
        print(f"{date:%Y-%m-%d} {key}: {old} -> {new} ({name})")


def download_assets():
    sha = head_sha(ASSETS)
    tree = api(f"repos/{ASSETS}/git/trees/{sha}?recursive=1")
    if tree.get("truncated"):
        raise SystemExit(f"{ASSETS} tree truncated; fetch folders one at a time")
    wanted = [
        e["path"]
        for e in tree["tree"]
        if e["type"] == "blob"
        and e["path"].lower().endswith(IMAGE_SUFFIXES)
        and any(e["path"].startswith(f + "/") for f in ASSET_FOLDERS)
    ]
    out_root = BENCH / "assets"
    for path in wanted:
        dest = out_root / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(_request(raw(ASSETS, sha, urllib.parse.quote(path))))
    manifest = {"repo": ASSETS, "commit": sha, "folders": ASSET_FOLDERS, "files": len(wanted)}
    (out_root / "MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"{ASSETS} @ {sha}: {len(wanted)} files under {out_root.relative_to(ROOT)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--history", action="store_true", help="print radius changes across previous game masters")
    parser.add_argument("--assets", action="store_true", help="download detector templates")
    args = parser.parse_args()
    if args.history:
        walk_history()
    elif args.assets:
        download_assets()
    else:
        write_fixture()


if __name__ == "__main__":
    main()
