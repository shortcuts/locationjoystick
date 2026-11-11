#!/usr/bin/env python3
"""Fail a Play AAB that carries GitHub-only code.

Play restricts REQUEST_INSTALL_PACKAGES (and REQUEST_DELETE_PACKAGES goes with it) and rejects apps that repackage themselves, so the
APK-signing libraries and that permission must live in app/src/github only. R8 renames the
dex classes of a release build, so the mapping file is read as well as the dex bytes.

Usage: check_play_strip.py <aab> [mapping.txt]
"""

import re
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_MAPPING = REPO / "app/build/outputs/mapping/playRelease/mapping.txt"
MANIFEST = "base/manifest/AndroidManifest.xml"
PERMISSIONS = (b"android.permission.REQUEST_INSTALL_PACKAGES", b"android.permission.REQUEST_DELETE_PACKAGES")
MAPPED_LIBRARY = re.compile(r"^com\.(reandroid|android\.apksig)\.", re.MULTILINE)
DEX_ENTRY = re.compile(r"(^|/)classes\d*\.dex$")
DEX_LIBRARIES = (b"com/reandroid", b"com/android/apksig")


def leaks(archive, mapping):
    found = []
    names = archive.namelist()
    if MANIFEST not in names:
        found.append(f"{MANIFEST} is missing")
    else:
        manifest = archive.read(MANIFEST)
        found += [f"{MANIFEST} declares {permission.decode()}" for permission in PERMISSIONS if permission in manifest]
    if MAPPED_LIBRARY.search(mapping):
        found.append("mapping.txt keeps a com.reandroid or com.android.apksig class")
    found += [f"entry {name}" for name in names if "reandroid" in name or "apksig" in name]
    for name in filter(DEX_ENTRY.search, names):
        data = archive.read(name)
        found += [f"{name} contains {needle.decode()}" for needle in DEX_LIBRARIES if needle in data]
    return found


def main():
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    artifact = sys.argv[1]
    mapping = Path(sys.argv[2]) if len(sys.argv) == 3 else DEFAULT_MAPPING
    with zipfile.ZipFile(artifact) as archive:
        found = leaks(archive, mapping.read_text(encoding="utf-8"))
    if found:
        print(f"{artifact}: GitHub-only code in the Play build:")
        print("\n".join(f"  {leak}" for leak in found))
        sys.exit(1)
    print(f"no GitHub-only code in {artifact}")


if __name__ == "__main__":
    main()
