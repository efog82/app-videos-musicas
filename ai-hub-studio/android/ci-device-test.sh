#!/usr/bin/env bash
# Preserve the test exit code while retaining a screenshot for diagnosis.
set +e
PAIR_URL=$(python -c "import json;print(json.load(open('ci-pairing.json'))['url'])")
PAIR_TOKEN=$(python -c "import json;print(json.load(open('ci-pairing.json'))['token'])")
PAIR_PIN=$(python -c "import json;print(json.load(open('ci-pairing.json'))['fingerprint'])")
echo "::add-mask::$PAIR_TOKEN"
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass=pt.aihub.studio.GalleryPickerUiTest,pt.aihub.studio.ZImageLiveTest -Pandroid.testInstrumentationRunnerArguments.liveProviders=true "-Pandroid.testInstrumentationRunnerArguments.pcUrl=$PAIR_URL" "-Pandroid.testInstrumentationRunnerArguments.pcToken=$PAIR_TOKEN" "-Pandroid.testInstrumentationRunnerArguments.pcPin=$PAIR_PIN" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --stacktrace
TEST_RESULT=$?
mkdir -p app/build/reports/androidTests
adb exec-out run-as pt.aihub.studio cat /sdcard/Android/data/pt.aihub.studio/files/live-provider.txt > app/build/reports/androidTests/live-provider.txt
adb exec-out run-as pt.aihub.studio cat /sdcard/Android/data/pt.aihub.studio/files/pc-tls.txt > app/build/reports/androidTests/pc-tls.txt
adb exec-out run-as pt.aihub.studio cat files/v3-video-preview.png > app/build/reports/androidTests/v3-video-preview.png
adb exec-out run-as pt.aihub.studio cat files/wan-v031-test.json > app/build/reports/androidTests/wan-v031-test.json
adb shell input keyevent 4
adb exec-out screencap -p > app/build/reports/androidTests/aihub-screen.png
exit "$TEST_RESULT"
