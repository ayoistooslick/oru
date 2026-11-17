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
DOWNLOADS = {
    "stable": (ROOT / "public" / "app-version.json",
               "https://github.com/ayoistooslick/oru/releases/latest/download/oru.apk"),
    # The beta install reads its own manifest, so a beta phone can never be
    # offered the release build and a release phone can never be offered beta.
    "beta": (ROOT / "public" / "app-version-beta.json",
             "https://github.com/ayoistooslick/oru/releases/download/beta/oru-beta.apk"),
}


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
    parser.add_argument("--channel", choices=sorted(DOWNLOADS), default="stable")
    parser.add_argument("--url", default=None)
    args = parser.parse_args()

    manifest, default_url = DOWNLOADS[args.channel]
    code, name = android_version()
    if args.channel == "beta":
        name = f"{name}-beta"
    previous = {}
    if manifest.exists():
        try:
            previous = json.loads(manifest.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            previous = {}
    notes = args.notes or previous.get("notes", "")

    manifest.write_text(json.dumps({
        "versionCode": code,
        "versionName": name,
        "url": args.url or default_url,
        "notes": notes,
        "required": bool(args.required),
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{manifest.name}: {name} ({code})")


if __name__ == "__main__":
    main()
