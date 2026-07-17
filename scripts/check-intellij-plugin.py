#!/usr/bin/env python3
import io
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]


def gradle_property(name: str) -> str:
    text = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    match = re.search(rf"^{re.escape(name)}=(.+?)\s*$", text, re.MULTILINE)
    if not match:
        raise ValueError(f"Missing {name} in gradle.properties")
    return match.group(1)


def read_plugin_xml(path: Path, version: str) -> bytes:
    if path.suffix == ".xml":
        return path.read_bytes()

    with zipfile.ZipFile(path) as plugin_zip:
        jar_suffix = f"/lib/intellij-{version}.jar"
        plugin_jars = [name for name in plugin_zip.namelist() if name.endswith(jar_suffix)]
        if len(plugin_jars) != 1:
            raise ValueError(
                f"Expected one {jar_suffix} in {path}, found {len(plugin_jars)}"
            )
        with zipfile.ZipFile(io.BytesIO(plugin_zip.read(plugin_jars[0]))) as plugin_jar:
            return plugin_jar.read("META-INF/plugin.xml")


def main() -> int:
    version = gradle_property("pluginVersion")
    since_build = gradle_property("pluginSinceBuild")
    default_path = ROOT / "packages" / f"AutoSwitchIME-IntelliJ-{version}.zip"
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else default_path

    try:
        root = ElementTree.fromstring(read_plugin_xml(path, version))
    except (FileNotFoundError, KeyError, ValueError, zipfile.BadZipFile) as error:
        print(f"Failed to inspect IntelliJ plugin metadata: {error}", file=sys.stderr)
        return 1

    actual_version = root.findtext("version")
    idea_version = root.find("idea-version")
    actual_since_build = (
        idea_version.get("since-build") if idea_version is not None else None
    )
    until_build = idea_version.get("until-build") if idea_version is not None else None

    errors = []
    if actual_version != version:
        errors.append(f"version is {actual_version!r}, expected {version!r}")
    if actual_since_build != since_build:
        errors.append(
            f"since-build is {actual_since_build!r}, expected {since_build!r}"
        )
    if until_build is not None:
        errors.append(f"until-build must be absent, got {until_build!r}")

    if errors:
        print("IntelliJ plugin metadata check failed:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1

    print(
        "IntelliJ plugin compatibility OK: "
        f"version {version}, since-build {since_build}, no upper bound"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
