#!/usr/bin/env python3
"""
Telos Launcher AI Control Center (CLI)
Unified command-line interface for AI agents working in this repository.
"""

import sys
import os
import json
import argparse
import subprocess
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
CONTEXT_MAP_FILE = ROOT_DIR / ".artifacts" / "CONTEXT_MAP.json"
VERSION_FILE = ROOT_DIR / "version.properties"

def cmd_audit(args):
    """Executes pre-push AI audit system."""
    audit_script = ROOT_DIR / "tools" / "validation" / "ai-audit.py"
    if not audit_script.exists():
        print("❌ Audit script not found.")
        sys.exit(1)
    res = subprocess.run([sys.executable, str(audit_script)], cwd=ROOT_DIR)
    sys.exit(res.returncode)

def cmd_upstream(args):
    """Checks upstream projects for updates."""
    upstream_script = ROOT_DIR / "tools" / "upstream" / "check-upstreams.py"
    if not upstream_script.exists():
        print("❌ Upstream script not found.")
        sys.exit(1)
    res = subprocess.run([sys.executable, str(upstream_script)], cwd=ROOT_DIR)
    sys.exit(res.returncode)

def cmd_locate(args):
    """Queries CONTEXT_MAP.json for feature file locations."""
    if not CONTEXT_MAP_FILE.exists():
        print("❌ CONTEXT_MAP.json not found.")
        sys.exit(1)
    with open(CONTEXT_MAP_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)

    query = args.feature.lower()
    matches = {k: v for k, v in data.items() if query in k.lower()}

    if matches:
        for domain, files in matches.items():
            for role, path in files.items():
                if not (ROOT_DIR / path).is_file():
                    print(f"Invalid context map path: {domain}.{role}: {path}", file=sys.stderr)
                    sys.exit(1)
        print(f"🔍 Located feature domain(s) matching '{query}':\n")
        print(json.dumps(matches, indent=2, ensure_ascii=False))
    else:
        print(f"⚠️  No exact feature domain found for '{query}'. Available domains:")
        for key in data.keys():
            print(f"  • {key}")

def cmd_bump(args):
    """Bumps version.properties and creates fastlane release notes."""
    if not VERSION_FILE.exists():
        print("❌ version.properties not found.")
        sys.exit(1)

    lines = VERSION_FILE.read_text().splitlines()
    version_data = {}
    for line in lines:
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            version_data[k.strip()] = v.strip()

    v_name = version_data.get("versionName", "1.45.87")
    v_code = int(version_data.get("versionCode", "2026112408"))

    parts = v_name.split(".")
    if args.type == "patch":
        parts[-1] = str(int(parts[-1]) + 1)
    elif args.type == "minor":
        parts[-2] = str(int(parts[-2]) + 1)
        parts[-1] = "0"

    new_name = ".".join(parts)
    new_code = v_code + 1

    # Write updated version.properties
    VERSION_FILE.write_text(f"versionName={new_name}\nversionCode={new_code}\n")
    print(f"✅ Bumped version: {v_name} ({v_code}) ➔ {new_name} ({new_code})")

    # Create fastlane notes if message provided
    if args.message:
        for locale in ["en-US", "el-GR"]:
            note_path = ROOT_DIR / "fastlane" / "metadata" / "android" / locale / "changelogs" / f"{new_code}.txt"
            note_path.parent.mkdir(parents=True, exist_ok=True)
            note_path.write_text(f"{args.message}\n", encoding="utf-8")
            print(f"  • Wrote fastlane changelog: {note_path}")

def main():
    parser = argparse.ArgumentParser(description="Telos Launcher AI Control Center (CLI)")
    subparsers = parser.add_subparsers(dest="command", help="Sub-command")

    # audit
    p_audit = subparsers.add_parser("audit", help="Run AI pre-push audit system")
    p_audit.set_defaults(func=cmd_audit)

    # upstream
    p_upstream = subparsers.add_parser("upstream", help="Check upstream projects")
    p_upstream.set_defaults(func=cmd_upstream)

    # locate
    p_locate = subparsers.add_parser("locate", help="Locate feature files in CONTEXT_MAP")
    p_locate.add_argument("feature", type=str, help="Feature domain (e.g. store, files, comms)")
    p_locate.set_defaults(func=cmd_locate)

    # bump
    p_bump = subparsers.add_parser("bump", help="Bump version and generate changelogs")
    p_bump.add_argument("type", choices=["patch", "minor"], help="Bump type")
    p_bump.add_argument("-m", "--message", type=str, help="Changelog message")
    p_bump.set_defaults(func=cmd_bump)

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(0)

    args.func(args)

if __name__ == "__main__":
    main()
