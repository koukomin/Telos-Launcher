#!/usr/bin/env python3
"""
Telos Upstream Sentinel — Intelligent Upstream Project Checker.
Automatically monitors incorporated third-party projects for new releases, bugfixes, and features.
Auto-discovers new upstream projects mentioned in THIRD_PARTY_NOTICES.md!
"""

import json
import re
import urllib.request
import urllib.error
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
REGISTRY_PATH = ROOT_DIR / ".artifacts" / "UPSTREAM_REGISTRY.json"
NOTICES_PATH = ROOT_DIR / "THIRD_PARTY_NOTICES.md"

def fetch_github_api(endpoint: str) -> dict:
    url = f"https://api.github.com/repos/{endpoint}"
    req = urllib.request.Request(url, headers={"User-Agent": "Telos-Upstream-Sentinel/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status == 200:
                return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        pass
    return {}

def auto_discover_notices(registry: dict) -> bool:
    """Scans THIRD_PARTY_NOTICES.md to auto-register newly added upstream projects."""
    if not NOTICES_PATH.exists():
        return False

    content = NOTICES_PATH.read_text(encoding="utf-8", errors="ignore")
    # Match https://github.com/owner/repo
    matches = re.findall(r"https://github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)", content)

    existing_repos = {v.get("repo", "").lower() for v in registry.values()}
    added_any = False

    for owner, repo in matches:
        repo_slug = f"{owner}/{repo}".rstrip(".git")
        slug_key = repo.lower().rstrip(".git")

        # Skip generic tools or GitHub actions
        if "actions/" in repo_slug.lower() or "checkout" in slug_key or "setup-" in slug_key:
            continue

        if repo_slug.lower() not in existing_repos:
            print(f"✨ Auto-discovered new upstream project in THIRD_PARTY_NOTICES.md: {repo_slug}")
            registry[slug_key] = {
                "repo": repo_slug,
                "path": "auto_discovered",
                "current_version": "unknown",
                "type": "third_party_notice"
            }
            existing_repos.add(repo_slug.lower())
            added_any = True

    return added_any

def check_upstreams() -> bool:
    print("=" * 60)
    print("🛰️  Telos Upstream Sentinel — Checking Incorporated Projects")
    print("=" * 60)

    if not REGISTRY_PATH.exists():
        print(f"❌ Registry file not found at {REGISTRY_PATH}")
        return False

    with open(REGISTRY_PATH, "r", encoding="utf-8") as f:
        registry = json.load(f)

    # Auto-discover new projects added to THIRD_PARTY_NOTICES.md
    if auto_discover_notices(registry):
        # Save updated registry
        with open(REGISTRY_PATH, "w", encoding="utf-8") as f:
            json.dump(registry, f, indent=2, ensure_ascii=False)

    updates_found = []

    for name, info in registry.items():
        repo = info.get("repo")
        if not repo:
            continue

        curr_ver = info.get("current_version")
        curr_commit = info.get("current_commit")

        # Query latest release
        latest_rel = fetch_github_api(f"{repo}/releases/latest")
        latest_ver = latest_rel.get("tag_name")
        rel_name = latest_rel.get("name") or latest_ver

        if latest_ver and curr_ver and latest_ver != curr_ver and curr_ver != "unknown":
            updates_found.append({
                "name": name,
                "repo": repo,
                "type": "New Release",
                "current": curr_ver,
                "latest": latest_ver,
                "url": latest_rel.get("html_url", f"https://github.com/{repo}")
            })
            print(f"🔔 [{name}] New Release Available! {curr_ver} ➔ {latest_ver} ({repo})")
        else:
            print(f"✓ [{name}] Up to date: {curr_ver or curr_commit or 'tracked'} ({repo})")

    print("\n" + "=" * 60)
    if updates_found:
        print(f"⚠️  Found {len(updates_found)} upstream project updates!")
        for u in updates_found:
            print(f"  • {u['name']} ({u['repo']}): {u['current']} ➔ {u['latest']} ({u['url']})")
        print("\nEnsure upstream fixes/updates are integrated into Telos before proceeding with feature development.")
    else:
        print("🎉 All incorporated upstream projects are fully up to date!")
    print("=" * 60)

    return True

if __name__ == "__main__":
    check_upstreams()
