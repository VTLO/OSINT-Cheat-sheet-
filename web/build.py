#!/usr/bin/env python3
"""Builds the VTLOsint PC web app into web/dist and downloads/VTLOsint-web.zip.

The tool list is generated from README.md and awesome-article.md with the same
rules as the Android app's CheatSheet parser, so both list exactly the same tools.

    python3 web/build.py
"""
import json
import pathlib
import re
import shutil
import zipfile

WEB = pathlib.Path(__file__).resolve().parent
REPO = WEB.parent
SRC = WEB / "src"
DIST = WEB / "dist"
FONTS = REPO / "android" / "app" / "src" / "main" / "res" / "font"
ICON = REPO / "android" / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
LICENSES = REPO / "android" / "app" / "src" / "main" / "assets" / "licenses"
ZIP = REPO / "downloads" / "VTLOsint-web.zip"

HEADING = re.compile(r"^#\s+(.+?)\s*$")
LINK_ITEM = re.compile(r"^\s*[-*]\s*\[([^\]]+)]\(([^)\s]+)\)(.*)$")
SCHEME = re.compile(r"^[a-z][a-z0-9+.-]*://", re.I)


def normalize_url(url):
    url = url.strip()
    return url if SCHEME.match(url) else "https://" + url


def parse(markdown, prefix=""):
    sections, current, last_tool, previous_was_tool, seen = [], None, None, False, set()
    for raw in markdown.splitlines():
        line = raw.strip()
        heading = HEADING.match(raw)
        if heading:
            current = {"title": prefix + heading.group(1).strip(), "tools": [], "notes": ""}
            sections.append(current)
            seen, previous_was_tool = set(), False
            continue
        if current is None or line.startswith("[!["):
            continue
        if not line:
            previous_was_tool = False
            continue
        link = LINK_ITEM.match(raw)
        if link:
            url = normalize_url(link.group(2))
            if url.lower() in seen:
                previous_was_tool = False
                continue
            seen.add(url.lower())
            description = re.sub(r"^[-–:]\s*", "", link.group(3).strip()).strip()
            last_tool = {"name": link.group(1).strip(), "url": url, "description": description}
            current["tools"].append(last_tool)
            previous_was_tool = True
            continue
        quote = line.startswith(">")
        text = line[1:].strip() if quote else line
        if not text:
            continue
        if previous_was_tool and not quote:
            last_tool["description"] = (last_tool["description"] + " " + text).strip()
            continue
        previous_was_tool = False
        current["notes"] += text + "\n"
    return [s for s in sections if s["tools"] or s["notes"]]


def main():
    sections = parse((REPO / "README.md").read_text(encoding="utf-8"))
    sections += parse((REPO / "awesome-article.md").read_text(encoding="utf-8"), "Articles · ")

    if DIST.exists():
        shutil.rmtree(DIST)
    shutil.copytree(SRC, DIST)
    (DIST / "fonts").mkdir()
    for name in ("vt323.ttf", "montserrat_bold.ttf", "montserrat_medium.ttf"):
        shutil.copy(FONTS / name, DIST / "fonts" / name)
    for lic in LICENSES.glob("*.txt"):
        shutil.copy(lic, DIST / "fonts" / lic.name)
    shutil.copy(ICON, DIST / "icon.png")
    data = json.dumps({"sections": sections}, ensure_ascii=False, separators=(",", ":"))
    # A script (not JSON fetched at runtime) so index.html also works opened straight from disk.
    (DIST / "data.js").write_text("window.VTLO_DATA=" + data + ";\n", encoding="utf-8")

    ZIP.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(DIST.rglob("*")):
            if f.is_file():
                z.write(f, pathlib.Path("VTLOsint-web") / f.relative_to(DIST))

    tools = {t["url"] for s in sections for t in s["tools"]}
    print(f"{len(tools)} tools in {len(sections)} sections -> {DIST.relative_to(REPO)}, {ZIP.relative_to(REPO)}")


if __name__ == "__main__":
    main()
