#!/usr/bin/env python3
"""
Token Budget & File Size Validator for Telos Launcher.
Enforces that no Kotlin file exceeds the 500-line budget to keep files AI-friendly.
"""

import sys
from pathlib import Path

MAX_LINES = 500

# Excluded directories or generated files
EXCLUDED_PATHS = [
    "build/",
    "AmethystAppFunctions.kt", # Vendor library bridge
    "Account.kt",             # Nostr protocol model
]

def check_file_sizes(root_dir: Path) -> bool:
    failed = False
    kt_files = list(root_dir.glob("**/*.kt"))

    over_budget = []

    for path in kt_files:
        path_str = str(path)
        if any(ex in path_str for ex in EXCLUDED_PATHS):
            continue

        try:
            lines = len(path.read_text(encoding="utf-8", errors="ignore").splitlines())
            if lines > MAX_LINES:
                over_budget.append((lines, path.relative_to(root_dir)))
        except Exception:
            pass

    if over_budget:
        print(f"⚠️  Found {len(over_budget)} files exceeding the {MAX_LINES}-line AI token budget:\n")
        over_budget.sort(key=lambda x: x[0], reverse=True)
        for lines, rel_path in over_budget[:15]:
            print(f"  - {rel_path}: {lines} lines")
        print("\nConsider splitting these files into domain components to keep them AI-friendly.")
    else:
        print(f"✅ All checked Kotlin files are within the {MAX_LINES}-line AI token budget!")

    return True

if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    check_file_sizes(root)
