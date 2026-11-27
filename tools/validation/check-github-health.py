#!/usr/bin/env python3
"""Summarize open alerts and the current branch's latest CI results."""

import json
import subprocess
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]


def run(*args: str) -> str:
    try:
        result = subprocess.run(args, cwd=ROOT_DIR, capture_output=True, text=True, timeout=20)
    except (OSError, subprocess.TimeoutExpired):
        return ""
    return result.stdout.strip() if result.returncode == 0 else ""


def audit() -> None:
    branch = run("git", "branch", "--show-current")
    if branch:
        raw = run("gh", "run", "list", "--branch", branch, "--limit", "20",
                  "--json", "databaseId,headSha,name,status,conclusion,url")
        runs = json.loads(raw) if raw else []
        if runs:
            latest_head = runs[0]["headSha"]
            latest_by_name = {}
            for item in runs:
                if item["headSha"] == latest_head:
                    latest_by_name.setdefault(item["name"], item)
            failures = [item for item in latest_by_name.values() if item["conclusion"] == "failure"]
            for item in failures[:3]:
                print(f"CI failure on {branch}@{latest_head[:8]}: {item['name']} {item['url']}")
            if failures:
                log = run("gh", "run", "view", str(failures[0]["databaseId"]), "--log-failed")
                diagnostics = list(dict.fromkeys(
                    line.split("\t")[-1].strip() for line in log.splitlines()
                    if "##[error]" in line or "\te: " in line
                ))
                for diagnostic in diagnostics[:3]:
                    print(diagnostic[:300])
    raw = run("gh", "api", "--paginate", "repos/{owner}/{repo}/dependabot/alerts?state=open",
              "--jq", ".[] | [.number, .security_advisory.severity, .dependency.package.name] | @tsv")
    if raw:
        for alert in raw.splitlines()[:3]:
            print(f"Open Dependabot alert: {alert.replace(chr(9), ' ')}")


if __name__ == "__main__":
    audit()
