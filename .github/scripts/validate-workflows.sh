#!/usr/bin/env bash
set -euo pipefail

# The retired desktop workflow keeps two explicitly disabled jobs for reference.
legacy=.github/workflows/build-desktop-binaries.yml
shopt -s nullglob
workflows=()
for workflow in .github/workflows/*.yml .github/workflows/*.yaml; do
    if [[ "$workflow" != "$legacy" ]]; then workflows+=("$workflow"); fi
done
actionlint -shellcheck= "${workflows[@]}"
if [[ -f "$legacy" ]]; then
    actionlint -shellcheck= -ignore '^constant expression "false" in condition\.' "$legacy"
fi
for script in .github/scripts/*.sh; do
    bash -n "$script"
done
python3 tools/release-version-test.py
python3 tools/release-apk-check-test.py
