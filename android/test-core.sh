#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p .build/tests
java -jar /workspace/android-tools/ecj.jar -encoding UTF-8 -source 8 -target 8 -bootclasspath /workspace/android-sdk/platforms/android-35/android.jar:/workspace/android-sdk/build-tools/android-15/core-lambda-stubs.jar -cp /workspace/android-tools/json.jar -d .build/tests src/pt/sonora/app/Project.java src/pt/sonora/app/AudioMath.java src/pt/sonora/app/Subtitles.java src/pt/sonora/app/Spectrum.java tests/CoreTests.java
java -cp .build/tests:/workspace/android-tools/json.jar pt.sonora.app.CoreTests
