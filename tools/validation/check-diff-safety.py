#!/usr/bin/env python3
"""
AI Diff & Anti-Pattern Safety Guard for Telos Launcher.
Scans git diffs for common AI anti-patterns:
1. Hardcoded UI strings in Compose text parameters.
2. Unfinished TODO() / FIXME stubs in production paths.
"""

import argparse
import subprocess
import re
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]

def check_git_diff(base: str | None = None, head: str = "HEAD") -> bool:
    revision = [base, head] if base else ["HEAD"]
    result = subprocess.run(
        ["git", "diff", "--no-ext-diff", *revision, "--", "*.kt", "*.kts", "*.java"],
        cwd=ROOT_DIR, capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(result.stderr.strip(), file=sys.stderr)
        return False

    added_lines = "\n".join(
        line for line in result.stdout.splitlines()
        if line.startswith("+") and not line.startswith("+++")
    )
    if not added_lines:
        print("No added lines to audit.")
        return True

    failed = False

    # Anti-pattern 1: TODO() or FIXME stubs added in diff
    todo_matches = re.findall(r"(?m)^\+.*?\b(TODO|FIXME)\s*\(", added_lines)
    if todo_matches:
        print(f"❌ Anti-Pattern Detected: Added {len(todo_matches)} TODO()/FIXME stub(s) in diff.")
        failed = True

    # Anti-pattern 2: Hardcoded Text("literal string") without stringResource
    hardcoded_text = re.findall(r"(?m)^\+\s*Text\(\s*\"([^\"]{3,})\"\s*[\),]", added_lines)
    if hardcoded_text:
        # Filter out numbers, URLs or symbols
        ignored = ["http", "0x", "123", "ABC", "Google", "Telos"]
        suspicious = [s for s in hardcoded_text if not any(ig in s for ig in ignored)]
        if suspicious:
            print(f"⚠️ Warning: Found potential hardcoded string(s) in Compose Text(): {suspicious[:3]}")

    if not failed:
        print("✅ Git diff safety check PASSED!")

    return not failed

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Check added lines in a Git diff.")
    parser.add_argument("--base", help="Compare a committed base to --head.")
    parser.add_argument("--head", default="HEAD")
    args = parser.parse_args()
    sys.exit(0 if check_git_diff(args.base, args.head) else 1)
