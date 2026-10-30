#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-/workspace/android-tools/sdk}"
TOOLS="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"
KEY="${SONORA_KEYSTORE:-/workspace/android-tools/sonora-test.jks}"
mkdir -p build/classes build/gen
"$TOOLS/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$TOOLS/aapt2" link -o build/resources.apk -I "$PLATFORM" --manifest app/src/main/AndroidManifest.xml --java build/gen build/resources.zip
find app/src/main/java build/gen -name '*.java' > build/sources.txt
java com.sun.tools.javac.Main -source 8 -target 8 -Xlint:deprecation -classpath "$PLATFORM" -d build/classes @build/sources.txt
find build/classes -name '*.class' > build/classes.txt
"$TOOLS/d8" --min-api 26 --lib "$PLATFORM" --output build @build/classes.txt
cp build/resources.apk build/unsigned.apk
python3 - <<'PY'
from zipfile import ZipFile,ZIP_DEFLATED
with ZipFile('build/unsigned.apk','a',compression=ZIP_DEFLATED) as apk:
    apk.write('build/classes.dex','classes.dex')
PY
"$TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f "$KEY" ]; then
    java sun.security.tools.keytool.Main -genkeypair -keystore "$KEY" -storepass android -keypass android -alias sonora-test -dname 'CN=Sonora Offline Test' -keyalg RSA -keysize 2048 -validity 3650
fi
"$TOOLS/apksigner" sign --ks "$KEY" --ks-pass pass:android --key-pass pass:android --ks-key-alias sonora-test --out build/sonora-offline-teste.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose build/sonora-offline-teste.apk
