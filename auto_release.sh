#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(python3 - <<'PY'
import re
text = open("app/app/build.gradle.kts", encoding="utf-8").read()
m = re.search(r'versionName = .*?"([0-9]+\.[0-9]+\.[0-9]+)"', text)
if not m:
    raise SystemExit("versionName not found in app/app/build.gradle.kts")
print(m.group(1))
PY
)
TAG="v${VERSION}"

git add .
if ! git diff --cached --quiet; then
  git commit -m "Release ${TAG}"
fi
git push origin HEAD

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Tag ${TAG} already exists locally."
else
  git tag -a "$TAG" -m "Telos ${TAG}"
fi
git push origin "$TAG"

gh repo set-default koukomin/Telos-Launcher >/dev/null 2>&1 || true
chmod +x tools/release-notes.sh
tools/release-notes.sh "$TAG" /tmp/telos-release-notes.md
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "GitHub release ${TAG} already exists; refreshing notes."
  gh release edit "$TAG" --title "Telos ${TAG}" --notes-file /tmp/telos-release-notes.md
else
  gh release create "$TAG" --title "Telos ${TAG}" --notes-file /tmp/telos-release-notes.md
fi
echo "Release: https://github.com/koukomin/Telos-Launcher/releases/tag/${TAG}"
