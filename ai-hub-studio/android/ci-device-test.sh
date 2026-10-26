#!/usr/bin/env bash
# Preserve the test exit code while retaining a screenshot for diagnosis.
set +e
gradle :app:connectedDebugAndroidTest --no-daemon --stacktrace
TEST_RESULT=$?
mkdir -p app/build/reports/androidTests
adb shell input keyevent 4
adb exec-out screencap -p > app/build/reports/androidTests/aihub-screen.png
exit "$TEST_RESULT"
