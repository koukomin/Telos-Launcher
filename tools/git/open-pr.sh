#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
branch=$(git symbolic-ref --quiet --short HEAD)
bash tools/git/validate-candidate.sh HEAD
git push origin "HEAD:refs/heads/$branch"
gh pr create --head "$branch" "$@"
