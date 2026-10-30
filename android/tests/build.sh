#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_SDK_ROOT:-/workspace/android-tools/sdk}"
TOOLS="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"
mkdir -p build/tests/classes
"$TOOLS/aapt2" link -o build/tests/resources.apk -I "$PLATFORM" --manifest tests/AndroidManifest.xml
java com.sun.tools.javac.Main -source 8 -target 8 -classpath "$PLATFORM:build/classes" -d build/tests/classes tests/SonoraTests.java
find build/tests/classes -name '*.class' > build/tests/classes.txt
"$TOOLS/d8" --min-api 26 --lib "$PLATFORM" --classpath build/classes --output build/tests @build/tests/classes.txt
cp build/tests/resources.apk build/tests/unsigned.apk
python3 - <<'PY'
from zipfile import ZipFile
from pathlib import Path
with ZipFile('build/tests/unsigned.apk','a') as apk:
    apk.write('build/tests/classes.dex','classes.dex')
    for file in Path('tests/assets').glob('*.mp4'):
        apk.write(file,'assets/'+file.name)
PY
"$TOOLS/zipalign" -f 4 build/tests/unsigned.apk build/tests/aligned.apk
"$TOOLS/apksigner" sign --ks "${SONORA_KEYSTORE:-/workspace/android-tools/sonora-test.jks}" --ks-pass pass:android --out build/tests/sonora-tests.apk build/tests/aligned.apk
