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


def read_plugin_files(path: Path, version: str) -> tuple[bytes, dict[str, bytes]]:
    if path.suffix == ".xml":
        icon_dir = path.parent
        return path.read_bytes(), {
            name: (icon_dir / name).read_bytes()
            for name in ("pluginIcon.svg", "pluginIcon_dark.svg")
        }

    with zipfile.ZipFile(path) as plugin_zip:
        jar_suffix = f"/lib/intellij-{version}.jar"
        plugin_jars = [name for name in plugin_zip.namelist() if name.endswith(jar_suffix)]
        if len(plugin_jars) != 1:
            raise ValueError(
                f"Expected one {jar_suffix} in {path}, found {len(plugin_jars)}"
            )
        with zipfile.ZipFile(io.BytesIO(plugin_zip.read(plugin_jars[0]))) as plugin_jar:
            return plugin_jar.read("META-INF/plugin.xml"), {
                name: plugin_jar.read(f"META-INF/{name}")
                for name in ("pluginIcon.svg", "pluginIcon_dark.svg")
            }


def validate_plugin_icon(name: str, content: bytes) -> list[str]:
    errors = []
    try:
        root = ElementTree.fromstring(content)
    except ElementTree.ParseError as error:
        return [f"{name} is not valid SVG XML: {error}"]

    if root.get("width") != "40" or root.get("height") != "40":
        errors.append(
            f"{name} must be 40x40, got {root.get('width')}x{root.get('height')}"
        )
    if len(content) > 3 * 1024:
        errors.append(f"{name} exceeds 3 KiB: {len(content)} bytes")
    return errors


def main() -> int:
    version = gradle_property("pluginVersion")
    since_build = gradle_property("pluginSinceBuild")
    default_path = ROOT / "packages" / f"AutoSwitchIME-IntelliJ-{version}.zip"
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else default_path

    try:
        plugin_xml, icons = read_plugin_files(path, version)
        root = ElementTree.fromstring(plugin_xml)
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
    for name, content in icons.items():
        errors.extend(validate_plugin_icon(name, content))

    if errors:
        print("IntelliJ plugin metadata check failed:", file=sys.stderr)
        for error in errors:
            print(f"  {error}", file=sys.stderr)
        return 1

    print(
        "IntelliJ plugin compatibility OK: "
        f"version {version}, since-build {since_build}, no upper bound, icons included"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
