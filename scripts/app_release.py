#!/usr/bin/env python3
"""Writes public/app-version.json from the Android build, so a release cannot
ship an APK whose manifest still names the previous version.

    python -m scripts.app_release --notes "What changed" [--required]

The version code and name come from android/app/build.gradle; the download is
the stable release asset, which the APK update check and the site both use.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "android" / "app" / "build.gradle"
MANIFEST = ROOT / "public" / "app-version.json"
DOWNLOAD = "https://github.com/ayoistooslick/oru/releases/latest/download/oru.apk"


def android_version() -> tuple[int, str]:
    text = GRADLE.read_text(encoding="utf-8")
    code = re.search(r"versionCode\s+(\d+)", text)
    name = re.search(r'versionName\s+"([^"]+)"', text)
    if code is None or name is None:
        raise SystemExit("android/app/build.gradle has no versionCode/versionName")
    return int(code.group(1)), name.group(1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--notes", default="", help="one line about the release")
    parser.add_argument("--required", action="store_true",
                        help="the app blocks until it is updated")
    parser.add_argument("--url", default=DOWNLOAD)
    args = parser.parse_args()

    code, name = android_version()
    previous = {}
    if MANIFEST.exists():
        try:
            previous = json.loads(MANIFEST.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            previous = {}
    notes = args.notes or previous.get("notes", "")

    MANIFEST.write_text(json.dumps({
        "versionCode": code,
        "versionName": name,
        "url": args.url,
        "notes": notes,
        "required": bool(args.required),
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"app-version.json: {name} ({code})")


if __name__ == "__main__":
    main()
