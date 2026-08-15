#!/usr/bin/env python3
import sys
import zipfile
from pathlib import Path


REQUIRED_FILES = {
    "Install-AutoSwitchIME.ps1",
    "README.md",
    "autoswitchime_bridge.lua",
    "autoswitchime_ipc.dll",
    "diagnose.cmd",
    "ime-diag.exe",
    "install.cmd",
    "uninstall.cmd",
}
REQUIRED_EXPORTS = (
    b"autoswitchime_publish\0",
    b"ime_rime_state_status\0",
    b"ime_rime_state_wait\0",
    b"ime_caps_message_state\0",
)


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "Usage: check-rime-package.py <package.zip> <ime_sys.dll> <ime-diag.exe>",
            file=sys.stderr,
        )
        return 2

    package_path, dll_path, diag_path = map(Path, sys.argv[1:])
    with zipfile.ZipFile(package_path) as archive:
        names = set(archive.namelist())
        missing = sorted(REQUIRED_FILES - names)
        if missing:
            print("Rime package missing files: " + ", ".join(missing), file=sys.stderr)
            return 1

        dll_data = archive.read("autoswitchime_ipc.dll")
        if dll_data != dll_path.read_bytes():
            print("Rime package DLL differs from native build", file=sys.stderr)
            return 1
        if archive.read("ime-diag.exe") != diag_path.read_bytes():
            print("Rime package diagnostic differs from native build", file=sys.stderr)
            return 1
        installer = archive.read("Install-AutoSwitchIME.ps1")
        if b"WeaselDeployer.exe" not in installer:
            print("Rime installer does not invoke WeaselDeployer", file=sys.stderr)
            return 1
        if b"'/quit'" not in installer:
            print("Rime installer does not stop Weasel before updating the DLL", file=sys.stderr)
            return 1

    missing_exports = [
        export.rstrip(b"\0").decode("ascii")
        for export in REQUIRED_EXPORTS
        if export not in dll_data
    ]
    if missing_exports:
        print("Rime DLL missing exports: " + ", ".join(missing_exports), file=sys.stderr)
        return 1

    print("Rime package files and native binaries OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
