#!/usr/bin/env bash
set -euo pipefail
log=$(mktemp)
trap 'rm -f "$log"' EXIT
set +e
bash gradlew --console=plain "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
set -e
if [[ $status != 0 && -n ${GITHUB_STEP_SUMMARY:-} ]]; then
    {
        printf '### Android build failed (exit %s)\n\n' "$status"
        printf 'Commit `%s` · run %s\n\n' "${GITHUB_SHA:-unknown}" "${GITHUB_RUN_ID:-local}"
        grep -m 12 -E '^(e: |FAILURE:|\* What went wrong:|Execution failed for task|Could not resolve|> Task .* FAILED)' "$log" |
            sed 's/^/    /' || true
    } >> "$GITHUB_STEP_SUMMARY"
fi
if [[ $status != 0 ]]; then
    mapfile -t diagnostics < <(grep -m 12 '^e: ' "$log")
    if [[ ${#diagnostics[@]} == 0 ]]; then
        mapfile -t diagnostics < <(awk '/^\* What went wrong:$/ { if (getline) print; exit }' "$log")
    fi
    for diagnostic in "${diagnostics[@]}"; do
        diagnostic=${diagnostic//%/%25}
        diagnostic=${diagnostic//$'\r'/%0D}
        printf '::error title=Android build failure::%s\n' "$diagnostic"
    done
fi
exit "$status"
