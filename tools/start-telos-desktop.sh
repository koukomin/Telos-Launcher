#!/usr/bin/env bash
set -euo pipefail
java -jar "$(dirname "$0")/telos-linux-desktop.jar" "$@"
