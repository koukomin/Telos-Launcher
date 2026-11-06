#!/usr/bin/env bash
# Preserve screenshot evidence even when a UI assertion fails; keep the original test exit code.
set -euo pipefail
cd "$(dirname "$0")/../.."
ui_test_status=0
bash ./gradlew :app:ui:connectedDefaultDebugAndroidTest --no-daemon --max-workers=1 \
  -Dorg.gradle.parallel=false -Dorg.gradle.jvmargs=-Xmx2048m --stacktrace || ui_test_status=$?
mkdir -p app/ui/build/ui-screenshots
# Some AGP/UTP versions return zero for a crashed instrumentation process.
# Preserve its actual crash report before emulator teardown and inspect JUnit results.
adb logcat -b crash -d > app/ui/build/ui-screenshots/instrumentation-crash.txt || true
python3 - <<'CHECK_RESULTS' || ui_test_status=$?
from pathlib import Path
import xml.etree.ElementTree as ET
reports = list(Path('app/ui/build/outputs/androidTest-results/connected').rglob('TEST-*.xml'))
assert reports, 'No instrumented test reports produced'
for report in reports:
    root = ET.parse(report).getroot()
    assert int(root.get('tests', '0')) > 0, f'No tests: {report}'
    assert not list(root.iter('failure')) and not list(root.iter('error')), report.read_text()
CHECK_RESULTS
ui_capture_status=0
adb pull /sdcard/Pictures/TelosUiValidation/. app/ui/build/ui-screenshots/ || ui_capture_status=$?
if [[ "$ui_test_status" -ne 0 ]]; then exit "$ui_test_status"; fi
exit "$ui_capture_status"
