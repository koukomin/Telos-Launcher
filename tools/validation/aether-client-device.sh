#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
apk=$(find app/app/build/outputs/apk/default/debug -name '*.apk' -print -quit)
test -n "$apk"
adb install -r "$apk"
# This is an isolated offline emulator. Retain startup/task transitions for the entire
# process-death scenario instead of losing the root cause to a 1,000-line tail.
adb logcat -G 16M || true
status=0
./gradlew :app:ui:connectedDefaultDebugAndroidTest -Ptelos.abi=x86_64 \
  -Pandroid.testInstrumentationRunnerArguments.class=de.mm20.launcher2.ui.aether.AetherEmbeddedClientTest,de.mm20.launcher2.ui.freeze.IslandIntentContractTest \
  --no-daemon --max-workers=1 --console=plain -Dorg.gradle.parallel=false \
  -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx7g -Dfile.encoding=UTF-8' || status=$?
mkdir -p /tmp/aether-screenshots
adb pull /sdcard/Pictures/TelosAetherValidation /tmp/aether-screenshots/ || true
adb logcat -d -b all > /tmp/aether-client-logcat.txt
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
files = list(Path('app/ui/build/outputs/androidTest-results').rglob('TEST-*.xml'))
cases = [c for p in files for c in ET.parse(p).getroot().iter('testcase')]
for name, expected in [('de.mm20.launcher2.ui.aether.AetherEmbeddedClientTest', 1), ('de.mm20.launcher2.ui.freeze.IslandIntentContractTest', 3)]:
    selected = [c for c in cases if c.get('classname') == name]
    if len(selected) != expected or any(any(c.find(tag) is not None for tag in ['failure', 'error', 'skipped']) for c in selected):
        raise SystemExit(f'{name}: expected {expected} successful tests without skips')
PY
exit "$status"
