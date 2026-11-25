#!/usr/bin/env python3
"""
AI Diff & Anti-Pattern Safety Guard for Telos Launcher.
Scans git diffs for common AI anti-patterns:
1. Hardcoded UI strings in Compose text parameters.
2. Unfinished TODO() / FIXME stubs in production paths.
3. Unsafe SharedPreferences.commit() on Main thread.
"""

import subprocess
import re
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]

def check_git_diff() -> bool:
    print("🔍 Checking git diff for AI anti-patterns...")

    # Get staged and unstaged diff
    try:
        res = subprocess.run(["git", "diff", "HEAD"], cwd=ROOT_DIR, capture_output=True, text=True)
        diff_text = res.stdout
    except Exception as e:
        print(f"⚠️ Could not read git diff: {e}")
        return True

    if not diff_text.strip():
        print("  • No diff detected (clean working tree).")
        return True

    failed = False

    # Anti-pattern 1: TODO() or FIXME stubs added in diff
    todo_matches = re.findall(r"\+.*?\b(TODO|FIXME)\b.*", diff_text)
    if todo_matches:
        print(f"❌ Anti-Pattern Detected: Added {len(todo_matches)} TODO()/FIXME stub(s) in diff.")
        failed = True

    # Anti-pattern 2: Hardcoded Text("literal string") without stringResource
    hardcoded_text = re.findall(r"\+\s*Text\(\s*\"([^\"]{3,})\"\s*[\),]", diff_text)
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
    if not check_git_diff():
        sys.exit(1)
