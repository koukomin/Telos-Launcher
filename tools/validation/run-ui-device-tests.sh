#!/usr/bin/env bash
# Preserve screenshot evidence even when a UI assertion fails; keep the original test exit code.
set -euo pipefail
cd "$(dirname "$0")/../.."
ui_test_status=0
bash ./gradlew :app:ui:connectedDefaultDebugAndroidTest --no-daemon --max-workers=1 \
  -Dorg.gradle.parallel=false -Dorg.gradle.jvmargs=-Xmx2048m --stacktrace || ui_test_status=$?
mkdir -p app/ui/build/ui-screenshots
ui_capture_status=0
adb pull /sdcard/Pictures/TelosUiValidation/. app/ui/build/ui-screenshots/ || ui_capture_status=$?
if [[ "$ui_test_status" -ne 0 ]]; then exit "$ui_test_status"; fi
exit "$ui_capture_status"
