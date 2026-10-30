#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK=/workspace/android-sdk
T="$SDK/build-tools/android-15"
P="$SDK/platforms/android-35/android.jar"
B=.build/ui-test
mkdir -p "$B/classes"
java -jar /workspace/android-tools/ecj.jar -encoding UTF-8 -source 8 -target 8 -bootclasspath "$P:$T/core-lambda-stubs.jar" -cp .build/classes -d "$B/classes" tests/EditorUiInstrumentation.java
find "$B/classes" -name '*.class' -print0 | xargs -0 "$T/d8" --min-api 26 --lib "$P" --classpath .build/classes --output "$B"
"$T/aapt2" link -o "$B/test.apk" -I "$P" --manifest tests/UiManifest.xml -A tests/assets
(cd "$B" && zip -q test.apk classes.dex)
"$T/zipalign" -f 4 "$B/test.apk" "$B/aligned.apk"
"$T/apksigner" sign --ks /workspace/android-tools/sonora-development.p12 --ks-key-alias sonora --ks-pass pass:android --out "$B/signed.apk" "$B/aligned.apk"
if [[ "${SONORA_UI_BUILD_ONLY:-0}" == "1" ]]; then
 "$T/apksigner" verify "$B/signed.apk"
 echo 'APK de instrumentação compilado e assinado; testes de interface não executados.'
 exit 0
fi
"$SDK/platform-tools/adb" install --no-incremental -r "$B/signed.apk"
"$SDK/platform-tools/adb" shell am instrument -w -r pt.sonora.editor.uitests/pt.sonora.app.EditorUiInstrumentation > "$B/results.txt"
cat "$B/results.txt"
python3 - <<'PY'
from pathlib import Path
s=Path('.build/ui-test/results.txt').read_text()
assert 'PASS:' in s and 'INSTRUMENTATION_CODE: -1' in s and 'FAIL:' not in s,s
PY
