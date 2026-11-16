"""Empty the song catalogue of the configured database, keeping accounts.

    python -m scripts.purge_songs --yes

The work lives in app/song_purge.py so the deployed maintenance route deletes
exactly the same rows and files.
"""

from __future__ import annotations

import argparse

from app.song_purge import purge_songs


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--yes", action="store_true",
                        help="actually delete every song")
    args = parser.parse_args()
    if not args.yes:
        print("This deletes every song and its audio. Re-run with --yes.")
        raise SystemExit(2)
    print(purge_songs())


if __name__ == "__main__":
    main()
