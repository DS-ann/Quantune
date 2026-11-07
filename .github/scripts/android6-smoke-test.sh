#!/usr/bin/env bash
set -euo pipefail

touch "$RUNNER_TEMP/dsmusic-api23-smoke-started"

APK="app/build/outputs/apk/release/dsmusic.apk"

test -s "$APK"
adb logcat -c
adb install -r "$APK"
adb shell am start -W -n com.dsann.music/.MainActivity
sleep 8
RUNTIME_LOG="$(adb logcat -d -s AndroidRuntime:E)"
printf '%s\n' "$RUNTIME_LOG"
if printf '%s' "$RUNTIME_LOG" | grep -q 'FATAL EXCEPTION'; then
  echo "D.S Music crashed during startup on Android 6 (API 23)." >&2
  exit 1
fi
if ! adb shell ps | grep -q 'com.dsann.music'; then
  echo "D.S Music process was not alive after startup on Android 6." >&2
  exit 1
fi
echo "D.S Music installed and remained running on Android 6 (API 23)."
