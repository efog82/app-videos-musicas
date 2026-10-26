#!/usr/bin/env bash
# Preserve the test exit code while retaining a screenshot for diagnosis.
set +e
PAIR_URL=$(python -c "import json;print(json.load(open('ci-pairing.json'))['url'])")
PAIR_TOKEN=$(python -c "import json;print(json.load(open('ci-pairing.json'))['token'])")
PAIR_PIN=$(python -c "import json;print(json.load(open('ci-pairing.json'))['fingerprint'])")
echo "::add-mask::$PAIR_TOKEN"
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.liveProviders=true "-Pandroid.testInstrumentationRunnerArguments.pcUrl=$PAIR_URL" "-Pandroid.testInstrumentationRunnerArguments.pcToken=$PAIR_TOKEN" "-Pandroid.testInstrumentationRunnerArguments.pcPin=$PAIR_PIN" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --stacktrace
TEST_RESULT=$?
mkdir -p app/build/reports/androidTests
adb pull /sdcard/Android/data/pt.aihub.studio/files/live-provider.txt app/build/reports/androidTests/live-provider.txt
adb pull /sdcard/Android/data/pt.aihub.studio/files/pc-tls.txt app/build/reports/androidTests/pc-tls.txt
adb shell input keyevent 4
adb exec-out screencap -p > app/build/reports/androidTests/aihub-screen.png
exit "$TEST_RESULT"
