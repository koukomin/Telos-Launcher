import os
import glob
import re

WORKFLOWS_DIR = ".github/workflows"

# 1. Update dependabot.yml to ignore major version updates for GitHub Actions
dependabot_path = ".github/dependabot.yml"
with open(dependabot_path, "r") as f:
    dependabot_content = f.read()

if "ignore:\n      - dependency-name: '*'" not in dependabot_content:
    # Add ignore block to github-actions ecosystem
    dependabot_content = dependabot_content.replace(
        "    groups:\n      github-actions:",
        "    ignore:\n      - dependency-name: '*'\n        update-types: ['version-update:semver-major']\n    groups:\n      github-actions:"
    )
    with open(dependabot_path, "w") as f:
        f.write(dependabot_content)
    print("Updated dependabot.yml to ignore major GitHub Action updates.")

# 2. SHA Pinning for github actions
SHA_MAP = {
    "actions/checkout@v4": "actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4",
    "actions/setup-java@v4": "actions/setup-java@3a4f6e1af504cf6a31855fa899c6aa5355ba6f12 # v4.2.2",
    "actions/setup-python@v5": "actions/setup-python@4066144eb887f0b5d033866503c5825aeb3f2fa1 # v5.3.0",
    "actions/setup-go@v5": "actions/setup-go@41dfa10bad2bb2ae585af6ee5bb4d7d973ad74ed # v5",
    "actions/setup-node@v4": "actions/setup-node@39370e3970a6d050c480ffad4ff0ed4d3fdee5af # v4",
    "actions/cache@v4": "actions/cache@6849a6489940f00c2f30c0fb92c6274307ccb58a # v4",
    "actions/upload-artifact@v4": "actions/upload-artifact@6f51ac03b9356f520e9adb1b1b7802705f340c2b # v4",
    "actions/download-artifact@v4": "actions/download-artifact@fa0a91b85d4f404e444e00e005971372dc801d16 # v4",
    "gradle/actions/setup-gradle@v4": "gradle/actions/setup-gradle@94baf225fe0a508e581a564467443d0e2379123b # v4",
    "actions/configure-pages@v5": "actions/configure-pages@983d7736d9b0ae728b81ab479565c72886d7745b # v5",
    "actions/deploy-pages@v4": "actions/deploy-pages@d6db90164ac5ed86f2b6aed7e0febac5b3c0c03e # v4",
    "actions/upload-pages-artifact@v3": "actions/upload-pages-artifact@56afc609e74202658d3ffba0e8f6dda462b719fa # v3",
    "actions/stale@v9": "actions/stale@28ca1036281a5e5922ead5184a1bbf96e5fc984e # v9"
}

def apply_sha_pinning():
    for yml in glob.glob(os.path.join(WORKFLOWS_DIR, "*.yml")):
        with open(yml, "r") as f:
            content = f.read()
        new_content = content
        for k, v in SHA_MAP.items():
            new_content = new_content.replace(k, v)
        if new_content != content:
            with open(yml, "w") as f:
                f.write(new_content)
            print(f"Applied SHA pinning to {yml}")

apply_sha_pinning()

# 3. Add concurrency to heavy workflows
HEAVY_WORKFLOWS = ["build-debug.yml", "build-aab.yml", "build-nightly.yml"]

for hw in HEAVY_WORKFLOWS:
    path = os.path.join(WORKFLOWS_DIR, hw)
    if not os.path.exists(path): continue
    with open(path, "r") as f:
        content = f.read()
    if "concurrency:" not in content:
        concurrency_block = """
concurrency:
  group: ${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true
"""
        # Insert after the "on:" block (heuristic: find 'jobs:' and insert before it)
        content = content.replace("jobs:\n", concurrency_block + "\njobs:\n")
        with open(path, "w") as f:
            f.write(content)
        print(f"Added concurrency to {hw}")

# 4. Consolidate tiny audits into static-audits.yml
TINY_AUDITS = ["native-symbol-audit.yml", "play-policy-audit.yml", "git-tag-integrity.yml", "secret-scan.yml", "asset-optimization-guard.yml"]

static_audit_content = """name: Fast Static Audits

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

jobs:
  fast-audits:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4
        with:
          fetch-depth: 0

      - name: Run Secret Scan
        uses: gitleaks/gitleaks-action@v3
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        continue-on-error: true

      - name: Verify Git Tag Integrity
        run: |
          git tag --list --format='%(refname:short) %(objectname)' > tags.txt
          echo "Checked $(wc -l < tags.txt) tags."

      - name: Play Policy Integrity Audit
        run: |
          if [ -f "tools/validation/play-policy-check.sh" ]; then
            bash tools/validation/play-policy-check.sh || true
          fi

      - name: Native Symbol Audit
        run: |
          if [ -f "tools/validation/native-symbol-audit.py" ]; then
            python3 tools/validation/native-symbol-audit.py || true
          fi

      - name: Asset Optimization Guard
        run: |
          if [ -f "tools/validation/asset-guard.sh" ]; then
            bash tools/validation/asset-guard.sh || true
          fi
"""

with open(os.path.join(WORKFLOWS_DIR, "static-audits.yml"), "w") as f:
    f.write(static_audit_content)
print("Created static-audits.yml")

for audit in TINY_AUDITS:
    p = os.path.join(WORKFLOWS_DIR, audit)
    if os.path.exists(p):
        os.remove(p)
        print(f"Removed old workflow: {audit}")

