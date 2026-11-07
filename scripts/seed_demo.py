"""Reset ORU to the demo catalogue, from a terminal.

    python -m scripts.seed_demo --yes

The work itself lives in app/demo_catalogue.py so a deploy can run the same
code through the maintenance route.
"""

from __future__ import annotations

import argparse

from app.demo_catalogue import reset_and_seed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--yes", action="store_true",
                        help="actually delete the current catalogue")
    args = parser.parse_args()
    if not args.yes:
        print("This deletes every song and artist page. Re-run with --yes.")
        raise SystemExit(2)
    print(reset_and_seed(force=True))


if __name__ == "__main__":
    main()
