#!/usr/bin/env python3
"""Self-check for the Play strip guard. Run directly: python3 scripts/test_check_play_strip.py"""

import io
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_play_strip import leaks

MANIFEST = "base/manifest/AndroidManifest.xml"
CLEAN_MANIFEST = b"\x0a\x07android.permission.INTERNET"
CLEAN_MAPPING = "com.locationjoystick.app.MainActivity -> a.a:\n"


def aab(entries):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return zipfile.ZipFile(buffer)


def clean():
    return {MANIFEST: CLEAN_MANIFEST, "base/dex/classes.dex": b"dex\n035 Lcom/locationjoystick/Foo;"}


def test_clean_artifact_passes():
    assert leaks(aab(clean()), CLEAN_MAPPING) == []


def test_install_packages_permission_fails():
    entries = clean() | {MANIFEST: CLEAN_MANIFEST + b"android.permission.REQUEST_INSTALL_PACKAGES"}
    assert len(leaks(aab(entries), CLEAN_MAPPING)) == 1


def test_mapping_with_reandroid_or_apksig_fails():
    assert len(leaks(aab(clean()), "com.reandroid.arsc.Foo -> a.b:\n")) == 1
    assert len(leaks(aab(clean()), CLEAN_MAPPING + "com.android.apksig.Signer -> a.c:\n")) == 1


def test_entry_name_fails():
    assert len(leaks(aab(clean() | {"base/root/x/reandroid.bin": b""}), CLEAN_MAPPING)) == 1


def test_dex_bytes_fail():
    for needle in (b"Lcom/reandroid/Foo;", b"Lcom/android/apksig/Foo;"):
        entries = clean() | {"base/dex/classes2.dex": b"dex\n035 " + needle}
        assert len(leaks(aab(entries), CLEAN_MAPPING)) == 1


def test_missing_manifest_fails():
    assert len(leaks(aab({"base/dex/classes.dex": b""}), CLEAN_MAPPING)) == 1


if __name__ == "__main__":
    for name, test in list(globals().items()):
        if name.startswith("test_"):
            test()
    print("ok")
