#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
commit=$(git rev-parse --verify "${1:-HEAD}^{commit}")
base=${2:-}
if ! git cat-file -e "$base^{commit}" 2>/dev/null; then
    base=$(git merge-base "$commit" origin/main)
fi
git diff --check "$base" "$commit"

actionlint_path=$(git config --get telos.actionlint || command -v actionlint || true)
if [[ -z "$actionlint_path" ]]; then
    echo 'Install actionlint 1.7.12 or configure telos.actionlint with the executable path.' >&2
    exit 1
fi
export TELOS_ACTIONLINT="$actionlint_path"

# Validate the commit being pushed even when the worktree has unrelated edits.
snapshot=$(mktemp -d)
trap 'rm -rf "$snapshot"' EXIT
git archive "$commit" .github tools | tar -x -C "$snapshot"
(
    cd "$snapshot"
    bash .github/scripts/validate-workflows.sh
)
