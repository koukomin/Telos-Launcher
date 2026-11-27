#!/usr/bin/env python3
"""Report oversized Kotlin files in the current change."""

import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
MAX_LINES = 500


def main(paths: list[str]) -> None:
    if not paths:
        result = subprocess.run(
            ["git", "diff", "--name-only", "-z", "HEAD", "--", "*.kt"],
            cwd=ROOT_DIR, check=True, capture_output=True, text=True,
        )
        paths = result.stdout.rstrip("\0").split("\0") if result.stdout else []
    oversized = []
    for name in sorted(set(paths)):
        path = ROOT_DIR / name
        if path.suffix != ".kt" or not path.is_file():
            continue
        with path.open(encoding="utf-8") as source:
            line_count = sum(1 for _ in source)
        if line_count > MAX_LINES:
            oversized.append((line_count, name))

    if oversized:
        print(f"{len(oversized)} changed Kotlin files exceed {MAX_LINES} lines.")
        for line_count, name in sorted(oversized, reverse=True)[:3]:
            print(f"  {name}: {line_count} lines")


if __name__ == "__main__":
    main(sys.argv[1:])
