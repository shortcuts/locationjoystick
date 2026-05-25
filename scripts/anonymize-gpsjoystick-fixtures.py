#!/usr/bin/env python3
"""
Anonymize GPS Joystick .db and YAMLA .json fixtures for use as unit test resources.

Reads .db files from the repo root, randomizes all coordinates and user-visible
names in-place (preserving binary structure and counts), then writes the output
to feature/settings/impl/src/test/resources/.

Also reads yamla*.json, all_routes.json, and favorites.json from the repo root,
anonymizes names and coordinates, and writes them to the same output directory.

Usage:
    python3 scripts/anonymize-gpsjoystick-fixtures.py [--seed SEED]

Options:
    --seed SEED   Integer seed for reproducible output (default: random)

Run this script whenever new sample files are added to the repo root.
The anonymized fixtures are committed; the originals are not.

## Binary format reference (Realm/TightDB)
- T-DB magic at byte offset 16.
- Array header: 8 bytes starting with 41 41 41 41 <type> xx xx <count>
    type 0x0C = double array  (count little-endian doubles follow at header+8)
    type 0x0D = string array  (count null-terminated UTF-8 strings follow at header+8,
                                each entry padded to 16-byte boundary within the array)
- Count is stored in byte 7 (the 8th byte) of the header.
- Schema string arrays contain known column names and are left untouched.
- Coordinate pairs: consecutive same-count double blocks whose values are all
  plausible geographic coordinates (|v| <= 180, non-zero, finite).

## YAMLA JSON formats
Two formats are handled:

1. Obfuscated settings format (yamla*.json):
   Single JSON object with keys like "e" (walk speed), "f" (run speed), "g" (bike speed),
   "w" (favorites array of {name, lat, lon}).
   Anonymization: replace "name" values in "w" array; replace lat/lon floats.

2. Plain array formats (all_routes.json, favorites.json):
   - all_routes.json: [{name, points:[{latitude, longitude}]}]
     Coordinates are integer microdegrees (divide by 1e6 for decimal degrees).
   - favorites.json: [{latLng:{latitude, longitude}, name}]
     Same integer microdegree encoding.
   Anonymization: replace name strings; replace integer coordinates.
"""

import argparse
import json
import os
import random
import string
import struct
import sys

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(
    REPO_ROOT,
    "feature", "settings", "impl", "src", "test", "resources",
)

SCHEMA_NAMES = {
    "id", "name", "latitude", "longitude", "altitude", "coordinates",
    "typeId", "address", "sortOrder", "pk_table", "pk_property",
    "parentFolderId", "type", "folderId",
}

# ---------------------------------------------------------------------------
# Parsing helpers
# ---------------------------------------------------------------------------

def find_double_blocks(data: bytes) -> list[tuple[int, int, list[float]]]:
    """Return list of (header_pos, count, doubles) for every 0x0C array."""
    blocks = []
    i = 0
    prefix = bytes([0x41, 0x41, 0x41, 0x41, 0x0C])
    while i <= len(data) - 8:
        if data[i:i+5] == prefix:
            count = data[i + 7]
            if count > 0:
                start = i + 8
                end = start + count * 8
                if end <= len(data):
                    doubles = [
                        struct.unpack_from("<d", data, start + j * 8)[0]
                        for j in range(count)
                    ]
                    blocks.append((i, count, doubles))
            i += 8
        else:
            i += 1
    return blocks


def find_string_arrays(data: bytes) -> list[tuple[int, int, list[str]]]:
    """Return list of (header_pos, count, strings) for every 0x0D array."""
    arrays = []
    i = 0
    prefix = bytes([0x41, 0x41, 0x41, 0x41, 0x0D])
    while i <= len(data) - 8:
        if data[i:i+5] == prefix:
            count = data[i + 7]
            if count > 0:
                pos = i + 8
                strings = []
                for _ in range(count):
                    null_pos = data.find(b"\x00", pos)
                    if null_pos == -1:
                        break
                    strings.append(data[pos:null_pos].decode("utf-8", "replace"))
                    pos = null_pos + 1
                    # Align to 16-byte boundary within the array body
                    consumed = pos - (i + 8)
                    rem = consumed % 16
                    if rem:
                        pos += 16 - rem
                if len(strings) == count:
                    arrays.append((i, count, strings))
            i += 8
        else:
            i += 1
    return arrays


def find_coord_pairs(
    blocks: list[tuple[int, int, list[float]]],
) -> list[tuple[tuple, tuple]]:
    """Return consecutive same-count block pairs whose values look like coordinates."""
    coord_blocks = [
        (pos, count, doubles)
        for pos, count, doubles in blocks
        if count >= 2 and all(abs(v) <= 180.0 and v != 0.0 and (v == v) for v in doubles)
    ]
    pairs = []
    i = 0
    while i < len(coord_blocks) - 1:
        a = coord_blocks[i]
        b = coord_blocks[i + 1]
        if a[1] == b[1]:
            pairs.append((a, b))
            i += 2
        else:
            i += 1
    return pairs


# ---------------------------------------------------------------------------
# Randomisation helpers
# ---------------------------------------------------------------------------

def random_name(length: int) -> str:
    if length <= 0:
        return ""
    chars = random.choices(string.ascii_lowercase, k=length)
    chars[0] = chars[0].upper()
    return "".join(chars)


def random_lat() -> float:
    return random.uniform(-70.0, 70.0)


def random_lon() -> float:
    return random.uniform(-150.0, 150.0)


# ---------------------------------------------------------------------------
# Anonymisation
# ---------------------------------------------------------------------------

def anonymize(data: bytes) -> bytes:
    buf = bytearray(data)

    # --- Randomise non-schema string arrays ---
    for arr_pos, count, strings in find_string_arrays(data):
        if any(s in SCHEMA_NAMES for s in strings):
            continue
        pos = arr_pos + 8
        for s in strings:
            old_bytes = s.encode("utf-8")
            new_name = random_name(len(old_bytes))
            new_bytes = new_name.encode("utf-8")[: len(old_bytes)]
            # Zero out slot, then write new name (may be shorter → trailing nulls)
            buf[pos : pos + len(old_bytes)] = b"\x00" * len(old_bytes)
            buf[pos : pos + len(new_bytes)] = new_bytes
            pos += len(old_bytes) + 1
            consumed = pos - (arr_pos + 8)
            rem = consumed % 16
            if rem:
                pos += 16 - rem

    # --- Randomise coordinate double blocks ---
    dbl_blocks = find_double_blocks(data)
    for (ap, ac, _), (bp, bc, _) in find_coord_pairs(dbl_blocks):
        for i in range(ac):
            struct.pack_into("<d", buf, ap + 8 + i * 8, random_lat())
            struct.pack_into("<d", buf, bp + 8 + i * 8, random_lon())

    return bytes(buf)


# ---------------------------------------------------------------------------
# YAMLA JSON anonymisation
# ---------------------------------------------------------------------------

def random_name_word(length: int = None) -> str:
    """Generate a random lowercase word, optionally of a fixed byte length."""
    if length is None:
        length = random.randint(4, 10)
    if length <= 0:
        return ""
    chars = random.choices(string.ascii_lowercase, k=length)
    chars[0] = chars[0].upper()
    return "".join(chars)


def random_microdegree_lat() -> int:
    """Random latitude as integer microdegrees (±70°)."""
    return int(random.uniform(-70_000_000, 70_000_000))


def random_microdegree_lon() -> int:
    """Random longitude as integer microdegrees (±150°)."""
    return int(random.uniform(-150_000_000, 150_000_000))


def anonymize_yamla_obfuscated(data: dict) -> dict:
    """Anonymize obfuscated YAMLA settings JSON (single object with keys e/f/g/w/…).

    Replaces name strings and lat/lon floats in the favorites array ("w").
    All other keys are preserved as-is.
    """
    result = dict(data)
    if "w" in result and isinstance(result["w"], list):
        new_favorites = []
        for item in result["w"]:
            new_item = dict(item)
            new_item["name"] = random_name_word()
            new_item["lat"] = round(random.uniform(-70.0, 70.0), 6)
            new_item["lon"] = round(random.uniform(-150.0, 150.0), 6)
            new_favorites.append(new_item)
        result["w"] = new_favorites
    return result


def anonymize_yamla_all_routes(data: list) -> list:
    """Anonymize YAMLA all_routes format: [{name, points:[{latitude, longitude}]}].

    Replaces route names and all integer microdegree coordinates.
    """
    result = []
    for route in data:
        new_route = dict(route)
        new_route["name"] = random_name_word()
        new_points = []
        for pt in route.get("points", []):
            new_points.append({
                "latitude": random_microdegree_lat(),
                "longitude": random_microdegree_lon(),
            })
        new_route["points"] = new_points
        result.append(new_route)
    return result


def anonymize_yamla_favorites(data: list) -> list:
    """Anonymize YAMLA favorites format: [{latLng:{latitude, longitude}, name}].

    Replaces name strings and integer microdegree coordinates.
    """
    result = []
    for item in data:
        new_item = dict(item)
        new_item["name"] = random_name_word()
        new_item["latLng"] = {
            "latitude": random_microdegree_lat(),
            "longitude": random_microdegree_lon(),
        }
        result.append(new_item)
    return result


def process_yamla_json(fname: str, src: str, dst: str) -> None:
    """Detect YAMLA JSON format, anonymize, and write to dst."""
    with open(src, "r", encoding="utf-8") as f:
        data = json.load(f)

    if isinstance(data, dict):
        # Obfuscated settings format
        anon = anonymize_yamla_obfuscated(data)
    elif isinstance(data, list) and data and "points" in data[0]:
        # all_routes format
        anon = anonymize_yamla_all_routes(data)
    elif isinstance(data, list) and data and "latLng" in data[0]:
        # favorites format
        anon = anonymize_yamla_favorites(data)
    elif isinstance(data, list) and not data:
        # Empty list — keep as-is
        anon = data
    else:
        print(f"Skipping (unrecognised YAMLA format): {fname}", file=sys.stderr)
        return

    with open(dst, "w", encoding="utf-8") as f:
        json.dump(anon, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")

    print(f"Written: {os.path.relpath(dst, REPO_ROOT)}  ({os.path.getsize(dst)} bytes)")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducibility")
    args = parser.parse_args()

    if args.seed is not None:
        random.seed(args.seed)
        print(f"Using seed: {args.seed}")

    os.makedirs(OUT_DIR, exist_ok=True)

    # --- GPS Joystick .db files ---
    db_files = sorted(
        f for f in os.listdir(REPO_ROOT)
        if f.startswith("gpsjoystick_") and f.endswith(".db")
    )

    if not db_files:
        print("No gpsjoystick_*.db files found in repo root.", file=sys.stderr)
    else:
        for fname in db_files:
            # Skip files with spaces in their name (e.g. "(1)" duplicates)
            if " " in fname:
                print(f"Skipping (spaces in name): {fname}")
                continue

            src = os.path.join(REPO_ROOT, fname)
            dst = os.path.join(OUT_DIR, fname)

            data = open(src, "rb").read()

            # Basic sanity check
            if data[16:20] != b"T-DB":
                print(f"Skipping (no T-DB header): {fname}", file=sys.stderr)
                continue

            anon = anonymize(data)
            with open(dst, "wb") as f:
                f.write(anon)

            print(f"Written: {os.path.relpath(dst, REPO_ROOT)}  ({len(anon)} bytes)")

    # --- YAMLA .json files ---
    # Matches: yamla*.json, all_routes.json, favorites.json
    json_files = sorted(
        f for f in os.listdir(REPO_ROOT)
        if f.endswith(".json") and (
            f.startswith("yamla") or f in ("all_routes.json", "favorites.json")
        )
    )

    if not json_files:
        print("No YAMLA .json files found in repo root.", file=sys.stderr)
    else:
        for fname in json_files:
            # Skip files with spaces in their name
            if " " in fname:
                print(f"Skipping (spaces in name): {fname}")
                continue

            src = os.path.join(REPO_ROOT, fname)
            dst = os.path.join(OUT_DIR, fname)
            process_yamla_json(fname, src, dst)

    print("Done.")


if __name__ == "__main__":
    main()
