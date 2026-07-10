#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read_text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def read_json(path: str) -> dict:
    return json.loads(read_text(path))


def extract(pattern: str, text: str, label: str) -> str:
    match = re.search(pattern, text, re.MULTILINE)
    if not match:
        raise ValueError(f"Missing version in {label}")
    return match.group(1)


def main() -> int:
    versions = {
        "AGENTS.md current version": extract(
            r"^\*\*当前版本\*\*:\s*v?([0-9]+\.[0-9]+\.[0-9]+)\s*$",
            read_text("AGENTS.md"),
            "AGENTS.md",
        ),
        "gradle.properties pluginVersion": extract(
            r"^pluginVersion=([0-9]+\.[0-9]+\.[0-9]+)\s*$",
            read_text("gradle.properties"),
            "gradle.properties",
        ),
        "vscode/package.json version": read_json("vscode/package.json")["version"],
        "vscode/package-lock.json version": read_json("vscode/package-lock.json")["version"],
        "vscode/package-lock.json root package version": read_json("vscode/package-lock.json")[
            "packages"
        ][""]["version"],
    }

    expected = next(iter(versions.values()))
    mismatches = {
        label: version
        for label, version in versions.items()
        if version != expected
    }
    if mismatches:
        print("Version mismatch detected:", file=sys.stderr)
        for label, version in versions.items():
            marker = " != " if label in mismatches else " == "
            print(f"  {label}{marker}{version}", file=sys.stderr)
        return 1

    print(f"Version consistency OK: {expected}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
