#!/usr/bin/env python3
"""
Git Pre-Push Hook Installer for Telos Launcher.
Registers the Git OS Pre-Push Hook to block unsafe pushes.
"""

import os
import stat
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
HOOK_PATH = ROOT_DIR / ".git" / "hooks" / "pre-push"

HOOK_CONTENT = """#!/usr/bin/env bash
# Telos Launcher Git OS Pre-Push Hook
# Automatically executes AI Pre-Push Audit System before allowing any git push operation.

echo "============================================================"
echo "⚓ Git OS Pre-Push Hook: Triggering Telos AI Audit System"
echo "============================================================"

python3 tools/ai-cli.py audit
STATUS=$?

if [ $STATUS -ne 0 ]; then
    echo "============================================================"
    echo "💥 Git Push BLOCKED by OS Pre-Push Hook!"
    echo "   Please fix the reported audit errors before pushing."
    echo "============================================================"
    exit 1
fi

exit 0
"""

def setup_hooks():
    git_dir = ROOT_DIR / ".git"
    if not git_dir.exists():
        print("⚠️ .git directory not found.")
        return

    hooks_dir = git_dir / "hooks"
    hooks_dir.mkdir(parents=True, exist_ok=True)

    HOOK_PATH.write_text(HOOK_CONTENT, encoding="utf-8")
    st = os.stat(HOOK_PATH)
    os.chmod(HOOK_PATH, st.st_mode | stat.S_IEXEC)
    print("✅ Installed Git OS Pre-Push Hook at .git/hooks/pre-push")

if __name__ == "__main__":
    setup_hooks()
