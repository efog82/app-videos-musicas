#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${SONORA_ANDROID_SDK:-/workspace/android-sdk}"
TOOLS="$SDK/build-tools/android-15"
PLATFORM="$SDK/platforms/android-35/android.jar"
if [[ ! -f "$PLATFORM" ]]; then PLATFORM="$SDK/platforms/android-35-ext14/android.jar"; fi
if [[ ! -x "$TOOLS/aapt2" ]]; then TOOLS="$SDK/build-tools/35.0.0"; fi
BUILD=".build"
PHASE="${SONORA_PHASE:-$(cat phase.txt)}"
case "$PHASE" in 1|2|3|4|5) ;; *) echo 'SONORA_PHASE must be 1..5' >&2; exit 1 ;; esac
rm -rf "$BUILD/classes"
mkdir -p "$BUILD/classes" "$BUILD/generated/pt/sonora/app" dist
printf 'package pt.sonora.app; final class BuildStage { static final int PHASE = %s; }\n' "$PHASE" > "$BUILD/generated/pt/sonora/app/BuildStage.java"
python3 - "$PHASE" <<'PYBUILD'
import sys
from pathlib import Path
phase=int(sys.argv[1])
s=Path('AndroidManifest.xml').read_text().replace('android:versionCode="60"',f'android:versionCode="{60+phase}"').replace('android:versionName="0.6.0-experimental"',f'android:versionName="0.6.0-phase{phase}-experimental"')
Path('.build/AndroidManifest.xml').write_text(s)
PYBUILD
"$TOOLS/aapt2" compile --dir res -o "$BUILD/resources.zip"
"$TOOLS/aapt2" link -o "$BUILD/base.apk" -I "$PLATFORM" --manifest "$BUILD/AndroidManifest.xml" "$BUILD/resources.zip"
java -jar /workspace/android-tools/ecj.jar -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM:$TOOLS/core-lambda-stubs.jar" -d "$BUILD/classes" src/pt/sonora/app/*.java app/src/main/java/pt/sonora/offline/*.java "$BUILD/generated/pt/sonora/app/BuildStage.java"
find "$BUILD/classes" -name '*.class' -print0 | xargs -0 "$TOOLS/d8" --min-api 26 --lib "$PLATFORM" --output "$BUILD"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD" && zip -q unsigned.apk classes.dex)
"$TOOLS/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
KEY="${SONORA_SIGNING_KEY:-/workspace/android-tools/sonora-development.p12}"
if [[ ! -f "$KEY" ]]; then
 keytool -genkeypair -keystore "$KEY" -storepass android -keypass android -alias sonora -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Sonora experimental' >/dev/null
fi
"$TOOLS/apksigner" sign --ks "$KEY" --ks-key-alias sonora --ks-pass pass:android --out dist/Sonora.apk "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify --verbose dist/Sonora.apk

cp dist/Sonora.apk "dist/Sonora-fase${PHASE}-experimental.apk"
