#!/usr/bin/env python3
"""
Pre-Push AI Audit Script for Telos Launcher.
Executes token budget check, git diff safety, Koin DI audit, GitHub health (PRs, Actions, Pages),
release notes validation, and core unit tests in a single command before git push operations.
"""

import argparse
import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]

def run_command(description: str, cmd: list, *, show_summary: bool = False) -> bool:
    result = subprocess.run(cmd, cwd=ROOT_DIR, capture_output=True, text=True)
    print(f"{description}: {'PASS' if result.returncode == 0 else 'FAIL'}")
    if result.returncode:
        lines = (result.stdout + "\n" + result.stderr).strip().splitlines()
        errors = [line for line in lines if line.startswith("e: ") or "::error" in line]
        print("\n".join((*errors[:3], *lines[-18:])))
    elif show_summary and result.stdout.strip():
        print(result.stdout.strip())
    return result.returncode == 0


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT_DIR, text=True).rstrip("\n")


def pushed_ranges(remote: str) -> list[tuple[str, str]]:
    ranges = []
    for line in sys.stdin:
        local_ref, local_sha, _, remote_sha = line.split()
        if not local_ref.startswith("refs/heads/"):
            continue
        if set(remote_sha) == {"0"}:
            default = subprocess.run(
                ["git", "symbolic-ref", f"refs/remotes/{remote}/HEAD"],
                cwd=ROOT_DIR, capture_output=True, text=True,
            ).stdout.strip() or f"refs/remotes/{remote}/main"
            remote_sha = git("merge-base", default, local_sha)
        ranges.append((remote_sha, local_sha))
    return ranges


def main():
    parser = argparse.ArgumentParser(description="Run Telos development checks.")
    parser.add_argument("--pre-push", action="store_true")
    parser.add_argument("--remote", default="origin")
    args = parser.parse_args()

    success = True
    validation = ROOT_DIR / "tools" / "validation"
    if args.pre_push:
        paths = set()
        for base, head in pushed_ranges(args.remote):
            paths.update(git("diff", "--name-only", "-z", base, head, "--").split("\0"))
            success &= run_command(
                "Committed diff safety",
                [sys.executable, str(validation / "check-diff-safety.py"), "--base", base, "--head", head],
                show_summary=True,
            )
        if paths:
            success &= run_command(
                "Changed Kotlin file sizes",
                [sys.executable, str(validation / "check-file-sizes.py"), *sorted(paths - {""})],
                show_summary=True,
            )
    else:
        success &= run_command(
            "Working tree diff safety",
            [sys.executable, str(validation / "check-diff-safety.py")],
            show_summary=True,
        )
        success &= run_command(
            "Changed Kotlin file sizes",
            [sys.executable, str(validation / "check-file-sizes.py")],
            show_summary=True,
        )

    # 3. Koin DI Graph Audit
    koin_script = ROOT_DIR / "tools" / "validation" / "check-koin-graph.py"
    if koin_script.exists():
        if not run_command("Auditing Koin Dependency Injection Graph", [sys.executable, str(koin_script)]):
            success = False

    # 4. GitHub Health, PRs, Pages & Security Audit
    gh_health_script = ROOT_DIR / "tools" / "validation" / "check-github-health.py"
    if gh_health_script.exists():
        if not run_command("Auditing GitHub Actions, PRs, Pages & Security Status", [sys.executable, str(gh_health_script)], show_summary=True):
            success = False

    # 5. Fastlane release notes check
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

    # 6. Core Unit Tests
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
