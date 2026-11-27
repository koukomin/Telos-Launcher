#!/usr/bin/env python3
"""Enable the repository's checked-in Git hooks in this clone."""

from pathlib import Path
import subprocess


root = Path(__file__).resolve().parents[2]
command = ["git", "-C", str(root), "config"]
existing = subprocess.run(command + ["--get", "core.hooksPath"], capture_output=True, text=True).stdout.strip()
if existing and existing != "tools/git":
    raise SystemExit(f"Existing core.hooksPath is {existing!r}. Add tools/git/pre-push to that hook to keep both checks.")
subprocess.run(command + ["--local", "core.hooksPath", "tools/git"], check=True)
print("Enabled Telos pre-push checks for this clone.")
