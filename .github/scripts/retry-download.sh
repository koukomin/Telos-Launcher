#!/usr/bin/env bash

# Run only idempotent downloads. Propagate permanent failures immediately.
retry_download() {
    local deadline=$1 log status attempt
    shift
    log=$(mktemp)
    for attempt in 1 2 3; do
        if timeout --kill-after=10s "$deadline" "$@" >"$log" 2>&1; then
            cat "$log"
            rm -f "$log"
            return 0
        else
            status=$?
        fi
        cat "$log"
        if [[ $attempt == 3 ]] || {
            [[ $status != 124 ]] &&
                ! grep -Eiq 'timed? out|connection reset|temporary failure|unexpected EOF|HTTP[ /]+(429|50[0234])|network is unreachable' "$log"
        }; then
            rm -f "$log"
            return "$status"
        fi
        sleep "$((attempt * 5))"
    done
}
