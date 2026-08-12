#!/usr/bin/env python3
import sys
import zipfile


REQUIRED_FILES = {
    "README.md",
    "rime_ice.custom.yaml",
    "rimevim_bridge.lua",
}


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: check-lua-package.py <lua-package.zip>", file=sys.stderr)
        return 2

    with zipfile.ZipFile(sys.argv[1]) as archive:
        names = set(archive.namelist())

    missing = sorted(REQUIRED_FILES - names)
    if missing:
        print("Lua package missing files: " + ", ".join(missing), file=sys.stderr)
        return 1

    print("Lua package files OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
