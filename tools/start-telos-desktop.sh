#!/usr/bin/env bash
# Launch Telos Desktop UberJar (Linux host). Prefers versioned telos-desktop-linux-*.jar.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR=""
for candidate in "$DIR"/telos-desktop-linux-*.jar "$DIR"/telos-linux-desktop.jar; do
  if [[ -f "$candidate" ]]; then
    JAR="$candidate"
    break
  fi
done
if [[ -z "$JAR" ]]; then
  echo "No Telos Desktop jar next to this script (expected telos-desktop-linux-*.jar)." >&2
  exit 1
fi
exec java -jar "$JAR" "$@"
