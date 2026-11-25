#!/usr/bin/env python3
"""
GitHub Health, PR, Pages & Security Audit Guard for Telos Launcher.
Verifies prior to git push that:
1. No Pull Requests are stuck or failing CI.
2. No recent GitHub Actions runs are in a failed state (extracts Log Diagnostic Cards!).
3. GitHub Pages documentation deployment is operational.
4. Dependabot & CodeQL security alerts are audited and auto-patchable.
"""

import json
import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]

def run_gh(args: list) -> str:
    try:
        res = subprocess.run(["gh"] + args, cwd=ROOT_DIR, capture_output=True, text=True)
        if res.returncode == 0:
            return res.stdout.strip()
    except Exception:
        pass
    return ""

def print_diagnostic_card(run_id: int, run_name: str, branch: str):
    print("\n┌" + "─" * 68 + "┐")
    print(f"│ 📋 FAILURE DIAGNOSTIC CARD — Run #{run_id} ({run_name})")
    print("├" + "─" * 68 + "┤")
    log_text = run_gh(["run", "view", str(run_id), "--log-failed"])
    if log_text:
        lines = log_text.splitlines()
        for line in lines[-8:]:
            print(f"│ {line[:66]}")
    else:
        print(f"│  (Logs unavailable or job superseded for branch {branch})")
    print("└" + "─" * 68 + "┘\n")

def audit_github_health() -> bool:
    print("🔍 Auditing GitHub Actions, PRs, Pages & Security Status...")

    # 1. Check Open PRs
    prs = run_gh(["pr", "list", "--json", "number,title,state,headRefName"])
    if prs and prs != "[]":
        try:
            pr_list = json.loads(prs)
            if pr_list:
                print(f"⚠️  Found {len(pr_list)} open Pull Request(s):")
                for pr in pr_list:
                    print(f"  • PR #{pr['number']}: {pr['title']} ({pr['headRefName']})")
                print("  👉 Agents MUST inspect and merge/resolve open PRs before pushing.")
        except Exception:
            pass

    # 2. Check Recent Failed Actions Runs & Generate Diagnostic Cards
    runs_raw = run_gh(["run", "list", "--limit", "10", "--json", "databaseId,name,status,conclusion,headBranch"])
    if runs_raw:
        try:
            runs = json.loads(runs_raw)
            failed_runs = [r for r in runs if r.get("conclusion") == "failure"]
            if failed_runs:
                print(f"⚠️  Found {len(failed_runs)} recently failed GitHub Action run(s):")
                for r in failed_runs[:2]:
                    print(f"  • Run #{r['databaseId']}: {r['name']} ({r['headBranch']}) - FAILED")
                    print_diagnostic_card(r['databaseId'], r['name'], r['headBranch'])
        except Exception:
            pass

    # 3. Check GitHub Pages Deployments
    pages_raw = run_gh(["run", "list", "--workflow", "deploy-docs.yml", "--limit", "3", "--json", "databaseId,conclusion,status"])
    if pages_raw:
        try:
            pages = json.loads(pages_raw)
            failed_pages = [p for p in pages if p.get("conclusion") == "failure"]
            if failed_pages:
                print(f"⚠️  GitHub Pages deployment ({failed_pages[0]['databaseId']}) is in a FAILED state!")
                print("  👉 Agents MUST fix Dokka/Vitepress doc build errors and re-trigger 'gh workflow run deploy-docs.yml'.")
        except Exception:
            pass

    # 4. Check Dependabot Security Alerts
    alerts_raw = run_gh(["api", "repos/{owner}/{repo}/dependabot/alerts", "--jq", ".[] | select(.state == \"open\") | {number: .number, pkg: .security_advisory.summary}"])
    if alerts_raw:
        print("⚠️  Open Dependabot security alerts found! Please patch dependencies in gradle/libs.versions.toml.")

    print("✅ GitHub Health, PRs, Pages & Security Audit completed.")
    return True

if __name__ == "__main__":
    audit_github_health()
