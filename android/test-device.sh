#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK=/workspace/android-sdk
T="$SDK/build-tools/android-15"
P="$SDK/platforms/android-35/android.jar"
mkdir -p .build/device-test/classes
java -jar /workspace/android-tools/ecj.jar -encoding UTF-8 -source 8 -target 8 -bootclasspath "$P:$T/core-lambda-stubs.jar" -cp .build/classes -d .build/device-test/classes tests/EditorInstrumentation.java
find .build/device-test/classes -name '*.class' -print0 | xargs -0 "$T/d8" --min-api 26 --lib "$P" --classpath .build/classes --output .build/device-test
"$T/aapt2" link -o .build/device-test/test.apk -I "$P" --manifest tests/EditorManifest.xml -A tests/assets
(cd .build/device-test && zip -q test.apk classes.dex)
"$T/zipalign" -f 4 .build/device-test/test.apk .build/device-test/aligned.apk
"$T/apksigner" sign --ks /workspace/android-tools/sonora-development.p12 --ks-key-alias sonora --ks-pass pass:android --out .build/device-test/signed.apk .build/device-test/aligned.apk
"$SDK/platform-tools/adb" install --no-incremental -r dist/Sonora.apk
"$SDK/platform-tools/adb" install --no-incremental -r .build/device-test/signed.apk
"$SDK/platform-tools/adb" shell am instrument -w -r pt.sonora.editor.tests/pt.sonora.app.EditorInstrumentation > .build/device-test/results.txt
cat .build/device-test/results.txt
python3 - <<'PY'
from pathlib import Path
s=Path('.build/device-test/results.txt').read_text()
assert 'PASS: ' in s and 'INSTRUMENTATION_CODE: -1' in s and 'FAIL' not in s, 'Android instrumentation failed; inspect results above'
PY
