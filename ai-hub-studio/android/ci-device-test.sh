#!/usr/bin/env bash
# Preserve the test exit code while retaining a screenshot for diagnosis.
set +e
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.liveProviders=true --no-daemon --stacktrace
TEST_RESULT=$?
mkdir -p app/build/reports/androidTests
adb pull /sdcard/Android/data/pt.aihub.studio/files/live-provider.txt app/build/reports/androidTests/live-provider.txt
adb shell input keyevent 4
adb exec-out screencap -p > app/build/reports/androidTests/aihub-screen.png
exit "$TEST_RESULT"
