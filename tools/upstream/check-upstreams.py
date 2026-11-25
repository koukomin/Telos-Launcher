#!/usr/bin/env python3
"""Report upstream candidates for Telos without modifying the checkout."""

import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / ".artifacts" / "UPSTREAM_REGISTRY.json"
REPO = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")


def fetch(repo, route, *, optional=False):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "Telos-Upstream-Sentinel"}
    if token := os.environ.get("GH_TOKEN"):
        headers["Authorization"] = f"Bearer {token}"
    request = Request(f"https://api.github.com/repos/{repo}/{route}", headers=headers)
    try:
        with urlopen(request, timeout=15) as response:
            return json.load(response)
    except HTTPError as error:
        if optional and error.code == 404:
            return None
        raise


def candidate(repo, project):
    if pinned := project.get("current_commit"):
        if not re.fullmatch(r"[0-9a-f]{7,40}", pinned):
            raise ValueError("Invalid pinned commit")
        upstream = fetch(repo, "")
        comparison = fetch(repo, f"compare/{pinned}...{upstream['default_branch']}")
        if comparison["status"] == "diverged":
            return f"{pinned[:12]} diverges from the upstream default branch. Review the pinned base."
        if comparison["ahead_by"]:
            return f"{comparison['ahead_by']} newer commits: {comparison['html_url']}"
        return None

    version = project.get("current_version")
    if not version or version == "unknown":
        return "No pinned baseline. Add one to compare releases."
    release = fetch(repo, "releases/latest", optional=True)
    if release is None:
        return "No published stable release. Consider tracking a reviewed commit."
    if release["tag_name"] != version:
        return f"{version} -> {release['tag_name']}: {release['html_url']}"
    return None


def main():
    projects = json.loads(REGISTRY.read_text(encoding="utf-8"))
    checked = set()
    report = ["## Telos upstream changes", ""]
    failures = 0
    for project in projects.values():
        repo = project["repo"].removesuffix(".git")
        if repo.lower() in checked:
            continue
        checked.add(repo.lower())
        try:
            if not REPO.fullmatch(repo):
                raise ValueError("Invalid repository name")
            if change := candidate(repo, project):
                report.append(f"- {repo} [{project['type']}, {project['path']}]: {change}")
        except (URLError, TimeoutError, ValueError, KeyError) as error:
            report.append(f"- {repo}: check failed ({error})")
            failures += 1

    if len(report) == 2:
        report.append("No changes found for tracked baselines.")
    report.extend(["", "For an integration PR, record the upstream revision, license, Telos changes and validation."])
    result = "\n".join(report)
    print(result)
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(summary).open("a", encoding="utf-8") as output:
            print(result, file=output)
    return int(failures > 0)


if __name__ == "__main__":
    sys.exit(main())
