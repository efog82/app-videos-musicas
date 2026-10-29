#!/usr/bin/env bash
# A separate Android 15 run exercises the actual Photo Picker and themed icon.
set +e
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=pt.aihub.studio.GalleryPickerUiTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --stacktrace
PICKER_RESULT=$?
mkdir -p app/build/reports/androidTests/picker
for name in picker-v032-test.json picker-v032-hierarchy.xml picker-v032-screen.png icon-v032.png; do
  adb exec-out run-as pt.aihub.studio cat "files/$name" > "app/build/reports/androidTests/picker/$name"
done
exit "$PICKER_RESULT"
