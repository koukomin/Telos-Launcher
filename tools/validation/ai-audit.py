#!/usr/bin/env python3
"""
Pre-Push AI Audit Script for Telos Launcher.
Executes token budget check, release notes validation, and core unit tests
in a single command before git push operations.
"""

import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]

def run_command(description: str, cmd: list) -> bool:
    print(f"\n🔍 [AI Audit] {description}...")
    try:
        res = subprocess.run(cmd, cwd=ROOT_DIR, capture_output=True, text=True)
        if res.returncode == 0:
            print(f"✅ {description} PASSED")
            if res.stdout.strip():
                print(res.stdout.strip())
            return True
        else:
            print(f"❌ {description} FAILED!")
            if res.stdout.strip():
                print(res.stdout.strip())
            if res.stderr.strip():
                print(res.stderr.strip())
            return False
    except Exception as e:
        print(f"❌ {description} Error: {e}")
        return False

def main():
    print("=" * 60)
    print("🚀 Telos Launcher Pre-Push AI Audit System")
    print("=" * 60)

    success = True

    # 1. File size / Token budget check
    file_size_script = ROOT_DIR / "tools" / "validation" / "check-file-sizes.py"
    if file_size_script.exists():
        if not run_command("Checking File Sizes & Token Budget", [sys.executable, str(file_size_script)]):
            success = False

    # 2. Fastlane release notes check
    validator_code = """
import os, sys
from pathlib import Path
version = dict(line.split('=', 1) for line in Path('version.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
code = version['versionCode']
assert code.isdigit(), 'Release versionCode must be numeric'
for locale in ['en-US', 'el-GR']:
    path = Path('fastlane/metadata/android') / locale / 'changelogs' / (code + '.txt')
    assert path.exists(), f'Missing changelog: {path}'
    text = path.read_text().strip()
    assert text and len(text) <= 500, f'{path}: expected 1–500 characters'
    print(f'Verified {locale} release changelog: {version["versionName"]} ({code})')
"""
    if not run_command("Validating Release Metadata", [sys.executable, "-c", validator_code]):
        success = False

    # 3. Core Unit Tests
    gradlew = ROOT_DIR / "gradlew"
    if gradlew.exists():
        if not run_command("Running Core Unit Tests", [str(gradlew), ":core:base:test", "--no-daemon", "--no-configuration-cache"]):
            success = False

    print("\n" + "=" * 60)
    if success:
        print("🎉 All AI Pre-Push Audits Passed! Ready to push.")
        print("=" * 60)
        sys.exit(0)
    else:
        print("💥 AI Pre-Push Audit Failed! Please fix the errors before pushing.")
        print("=" * 60)
        sys.exit(1)

if __name__ == "__main__":
    main()
