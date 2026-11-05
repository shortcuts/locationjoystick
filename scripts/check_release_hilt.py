#!/usr/bin/env python3
"""Fail a release APK or AAB that lost a @HiltViewModel binding.

KSP can silently skip a module on CI. The build stays green, and the screen crashes at
runtime because Hilt falls back to a no-arg constructor (issue 117). Debug builds and
unit tests never run the shipped artifact, so this reads the artifact itself.

Usage: check_release_hilt.py <apk-or-aab> [mapping.txt]
"""

import re
import subprocess
import sys
import tempfile
import zipfile
from os import environ
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_MAPPING = REPO / "app/build/outputs/mapping/release/mapping.txt"
DEX_ENTRY = re.compile(r"(^|/)classes\d*\.dex$")
# Lazy span: other annotations or modifiers can sit between @HiltViewModel and "class".
VIEW_MODEL = re.compile(r"^\s*@HiltViewModel\b.*?\bclass\s+(\w+)", re.MULTILINE | re.DOTALL)
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
MAPPED_CLASS = re.compile(r"^(\S+) -> (\S+):$", re.MULTILINE)
NEW_INSTANCE = re.compile(r"new-instance v\d+, L([^;]+);")
CONST_STRING = re.compile(r'const-string(?:/jumbo)? v\d+, "([^"]*)"')


def view_models_in(source):
    package = PACKAGE.search(source)
    if not package:
        return []
    return [f"{package.group(1)}.{name}" for name in VIEW_MODEL.findall(source)]


def obfuscated_names(mapping):
    return dict(MAPPED_CLASS.findall(mapping))


def dex_references(lines):
    instantiated, strings = set(), set()
    for line in lines:
        if match := NEW_INSTANCE.search(line):
            instantiated.add(match.group(1).replace("/", "."))
        elif match := CONST_STRING.search(line):
            strings.add(match.group(1))
    return instantiated, strings


def lost_bindings(view_models, names, instantiated, strings):
    """A bound ViewModel has a Hilt map key and a factory that constructs it."""
    lost = []
    for view_model in view_models:
        name = names.get(view_model)
        if name not in instantiated or name not in strings:
            lost.append(view_model)
    return lost


def source_view_models():
    files = subprocess.run(
        ["git", "ls-files", "*/src/main/kotlin/*.kt"],
        cwd=REPO,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.split()
    found = []
    for file in files:
        found += view_models_in((REPO / file).read_text(encoding="utf-8"))
    return found


def find_build_tool(name):
    # Same order as Gradle: local.properties wins over a possibly stale environment.
    properties = REPO / "local.properties"
    configured = properties.read_text(encoding="utf-8") if properties.exists() else ""
    match = re.search(r"^sdk\.dir=(.+)$", configured, re.MULTILINE)
    sdk = match.group(1) if match else environ.get("ANDROID_HOME") or environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        sys.exit(f"error: set ANDROID_HOME to locate {name}")
    tools = sorted(
        Path(sdk, "build-tools").glob(f"*/{name}"),
        key=lambda path: [int(part) for part in re.findall(r"\d+", path.parent.name)],
    )
    if not tools:
        sys.exit(f"error: no {name} under {sdk}/build-tools")
    return tools[-1]


def artifact_references(artifact):
    dexdump = find_build_tool("dexdump")
    instantiated, strings = set(), set()
    with zipfile.ZipFile(artifact) as archive, tempfile.TemporaryDirectory() as scratch:
        entries = [name for name in archive.namelist() if DEX_ENTRY.search(name)]
        if not entries:
            sys.exit(f"error: no dex in {artifact}")
        for entry in entries:
            dex = archive.extract(entry, scratch)
            dump = subprocess.Popen(
                [dexdump, "-d", dex],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                errors="replace",
            )
            new, found = dex_references(dump.stdout)
            instantiated |= new
            strings |= found
            if dump.wait() != 0:
                sys.exit(f"error: dexdump failed on {entry}")
    return instantiated, strings


def main():
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    artifact = sys.argv[1]
    mapping = Path(sys.argv[2]) if len(sys.argv) == 3 else DEFAULT_MAPPING
    view_models = source_view_models()
    if not view_models:
        sys.exit("error: found no @HiltViewModel in the source")
    instantiated, strings = artifact_references(artifact)
    names = obfuscated_names(mapping.read_text(encoding="utf-8"))
    lost = lost_bindings(view_models, names, instantiated, strings)
    if lost:
        print(f"{artifact}: lost {len(lost)} of {len(view_models)} Hilt ViewModel bindings:")
        print("\n".join(f"  {view_model}" for view_model in lost))
        sys.exit(1)
    print(f"{artifact}: all {len(view_models)} Hilt ViewModel bindings present")


if __name__ == "__main__":
    main()
