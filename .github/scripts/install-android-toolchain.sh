#!/usr/bin/env bash
set -euo pipefail
source .github/scripts/retry-download.sh

mapfile -t packages < <(python3 - <<'PY'
import tomllib
from pathlib import Path

telos = tomllib.loads(Path("gradle/libs.versions.toml").read_text())["versions"]
amethyst = tomllib.loads(Path("libs/amethyst/gradle/libs.versions.toml").read_text())["versions"]
print(f'platforms;android-{telos["compileSdk"]}.{telos["compileSdkMinor"]}')
print(f'platforms;android-{amethyst["android-compileSdk"]}.0')
print("ndk;27.0.12077973")
PY
)
[[ ${#packages[@]} == 3 ]]
retry_download 6m sdkmanager --install "${packages[@]}" </dev/null
