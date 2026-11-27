#!/usr/bin/env bash
set -euo pipefail
source .github/scripts/retry-download.sh
if retry_download 90s gh release download sip-libs --pattern sip-libs.tar.gz --dir "$RUNNER_TEMP" --clobber; then
    tar -xzf "$RUNNER_TEMP/sip-libs.tar.gz" -C sip-native
else
    echo "::warning::SIP native libraries are unavailable. Building without the optional SIP engine."
fi
