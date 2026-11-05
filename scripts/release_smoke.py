#!/usr/bin/env python3
"""Install a release APK on a device and open every Home screen, watching for crashes.

The instrumented smoke tests run on a debug build with HiltTestApplication, so they
never run what users install (issue 117). This drives the real artifact from outside.

WARNING: it uninstalls the app first, which deletes its data. Use test phones only.

Usage: release_smoke.py <apk>
"""

import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ElementTree
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_release_hilt import find_build_tool

PACKAGE = "com.locationjoystick.app"
ACTIVITY = f"{PACKAGE}/{PACKAGE}.MainActivity"
HOME_MARKER = "Replay saved routes."
CARDS = ["Map", "Routes", "Favorites", "Capture", "Group Sync", "Settings"]
PERMISSIONS = ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS"]
SETTLE_SECONDS = 3
TIMEOUT_SECONDS = 30


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True, errors="replace").stdout


def adb_checked(*args):
    subprocess.run(["adb", *args], capture_output=True, text=True, check=True)


def parse_nodes(dump):
    """Map each visible text to the centre of its bounds. The first node with a text wins."""
    nodes = {}
    for node in ElementTree.fromstring(dump).iter("node"):
        text = node.get("text")
        if not text or text in nodes:
            continue
        left, top, right, bottom = map(int, node.get("bounds").replace("][", ",").strip("[]").split(","))
        nodes[text] = ((left + right) // 2, (top + bottom) // 2)
    return nodes


def crashes_of(crash_log):
    """Return the summary lines of each crash of this app in `logcat -b crash` output."""
    found, ours = [], False
    for line in crash_log.splitlines():
        if "FATAL EXCEPTION" in line:
            ours = False
        elif f"Process: {PACKAGE}" in line:
            ours = True
        elif ours and ("Caused by:" in line or "Exception:" in line):
            found.append(line.split(": ", 1)[-1])
    return found


def screen():
    adb("shell", "uiautomator", "dump", "/sdcard/release_smoke.xml")
    dump = adb("exec-out", "cat", "/sdcard/release_smoke.xml")
    return parse_nodes(dump) if dump.startswith("<?xml") else {}


def tap(point):
    adb("shell", "input", "tap", str(point[0]), str(point[1]))


def wake():
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    if "isKeyguardShowing=true" in adb("shell", "dumpsys", "window"):
        height = int(adb("shell", "wm", "size").split("x")[-1])
        adb("shell", "input", "swipe", "540", str(height - 300), "540", "300", "300")


def install(apk):
    """Debug-sign a copy, because a local release build is unsigned."""
    with tempfile.TemporaryDirectory() as scratch:
        aligned, signed = Path(scratch, "aligned.apk"), Path(scratch, "signed.apk")
        subprocess.run([find_build_tool("zipalign"), "-f", "-p", "4", apk, aligned], check=True)
        password = "pass:android"
        keystore = Path.home() / ".android/debug.keystore"
        signer = [find_build_tool("apksigner"), "sign", "--ks", keystore, "--ks-pass", password]
        subprocess.run([*signer, "--key-pass", password, "--out", signed, aligned], check=True)
        adb("uninstall", PACKAGE)
        adb_checked("install", signed)
    for permission in PERMISSIONS:
        adb_checked("shell", "pm", "grant", PACKAGE, f"android.permission.{permission}")
    adb_checked("shell", "appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow")
    adb_checked("shell", "appops", "set", PACKAGE, "android:mock_location", "allow")


def relaunch():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", ACTIVITY)


def reach_home():
    """Pass the accessibility disclosure and onboarding, whichever of them shows."""
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        nodes = screen()
        if HOME_MARKER in nodes:
            return nodes
        for step in ("Decline", "Continue", "Start using locationjoystick"):
            if step in nodes:
                tap(nodes[step])
                break
        time.sleep(1)
    sys.exit(f"error: Home did not open. Screen: {sorted(screen())[:10]}")


def open_card(card):
    """Return an error for this card, or None when its screen opened without a crash."""
    relaunch()
    nodes = reach_home()
    if card not in nodes:
        return f"{card}: card not on Home"
    adb("logcat", "-b", "crash", "-c")
    tap(nodes[card])
    time.sleep(SETTLE_SECONDS)
    crashes = crashes_of(adb("logcat", "-d", "-b", "crash"))
    if crashes:
        return f"{card}: {' | '.join(crashes)}"
    if ACTIVITY not in adb("shell", "dumpsys", "window").replace("\n", " "):
        return f"{card}: app left the foreground"
    if HOME_MARKER in screen():
        return f"{card}: screen did not open"
    return None


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    if "\tdevice" not in adb("devices"):
        sys.exit("error: no adb device attached")
    wake()
    install(sys.argv[1])
    failures = [failure for failure in map(open_card, CARDS) if failure]
    if failures:
        print(f"{sys.argv[1]}: {len(failures)} of {len(CARDS)} screens failed:")
        print("\n".join(f"  {failure}" for failure in failures))
        sys.exit(1)
    print(f"{sys.argv[1]}: all {len(CARDS)} screens opened without a crash")


if __name__ == "__main__":
    main()
