#!/usr/bin/env bash
# Build GitHub release notes for TAG from commits since the previous v* tag.
# Usage: tools/release-notes.sh v1.41.12 [out.md]
set -euo pipefail
TAG="${1:?tag such as v1.41.12}"
OUT="${2:-release-notes.md}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Previous published tag that is an ancestor of HEAD (ignore tags on other histories).
PREV="$(git tag -l 'v*' --merged HEAD | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | grep -v "^${TAG}$" | sort -V | tail -n 1 || true)"
RANGE="HEAD"
if [[ -n "${PREV}" ]]; then
  RANGE="${PREV}..HEAD"
fi

COMMITS="$(git log --pretty=format:'%s' --no-merges "${RANGE}" \
  | { grep -vE '^(Release v|Merge )' || true; } \
  | awk 'NF && !seen[$0]++ { print "- " $0 }')"

{
  echo "## Τι άλλαξε / What changed"
  echo
  if [[ -n "${PREV}" ]]; then
    echo "Since **${PREV}**:"
    echo
  fi
  if [[ -z "${COMMITS}" ]]; then
    echo "- Build and packaging updates for this tag (no extra commit subjects since ${PREV:-the previous tag})."
  else
    printf '%s\n' "${COMMITS}"
  fi
  if [[ -n "${PREV}" ]]; then
    echo
    echo "**Full Changelog**: https://github.com/${GITHUB_REPOSITORY:-koukomin/Telos-Launcher}/compare/${PREV}...${TAG}"
  fi
  echo
  echo "---"
  echo
  cat "$ROOT/.github/release-notes-apk.md"
} > "$OUT"
echo "Wrote $OUT (since ${PREV:-beginning})"
