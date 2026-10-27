#!/usr/bin/env bash
set +e
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=pt.aihub.studio.ZImageLiveTest -Pandroid.testInstrumentationRunnerArguments.liveZImage=true -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --stacktrace
TEST_RESULT=$?
mkdir -p app/build/reports/androidTests
adb exec-out run-as pt.aihub.studio cat files/zimage-v033-test.json > app/build/reports/androidTests/zimage-v033-test.json
adb exec-out run-as pt.aihub.studio cat files/zimage-v033-original.png > app/build/reports/androidTests/zimage-v033-original.png
exit "$TEST_RESULT"
