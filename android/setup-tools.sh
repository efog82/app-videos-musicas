#!/usr/bin/env bash
set -euo pipefail
mkdir -p /workspace/android-tools /workspace/android-sdk/platforms /workspace/android-sdk/build-tools
fetch() {
 local url="$1" destination="$2" expected="$3"
 if [[ ! -f "$destination" ]] || ! printf '%s  %s\n' "$expected" "$destination" | sha1sum -c - >/dev/null 2>&1; then
   curl --fail --location --retry 2 "$url" -o "$destination"
 fi
 printf '%s  %s\n' "$expected" "$destination" | sha1sum -c -
}
# Checksums from Google's HTTPS repository2-1.xml and Maven Central artifact metadata.
fetch https://dl.google.com/android/repository/platform-35_r02.zip /workspace/android-tools/platform.zip 0bb560a90a7a2cbd0dd8348224d518b638fe7949
fetch https://dl.google.com/android/repository/build-tools_r35_linux.zip /workspace/android-tools/build-tools.zip 2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc
fetch https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar /workspace/android-tools/ecj.jar 86b2e4ab1d6aef91cb21f4d2ec364afb75d05b2c
fetch https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar /workspace/android-tools/json.jar 0ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a
unzip -qo /workspace/android-tools/platform.zip -d /workspace/android-sdk/platforms
unzip -qo /workspace/android-tools/build-tools.zip -d /workspace/android-sdk/build-tools
