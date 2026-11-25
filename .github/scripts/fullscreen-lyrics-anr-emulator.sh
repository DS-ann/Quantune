#!/usr/bin/env bash
set -u

ARTIFACT_DIR="${GITHUB_WORKSPACE:-$PWD}/anr-artifacts"
APK="app/build/outputs/apk/debug/app-x86_64-debug.apk"
mkdir -p "$ARTIFACT_DIR/tombstones" "$ARTIFACT_DIR/anr-traces"
RESULT=0
OPENED=0
ANR_FOUND=0

capture_logs() {
  adb logcat -b all -d -v threadtime > "$ARTIFACT_DIR/logcat-all.txt" 2>&1 || true
  adb shell dumpsys activity lastanr > "$ARTIFACT_DIR/activity-lastanr.txt" 2>&1 || true
  adb shell dumpsys dropbox --print data_app_anr > "$ARTIFACT_DIR/dropbox-app-anr.txt" 2>&1 || true
  adb shell dumpsys dropbox --print data_app_native_crash > "$ARTIFACT_DIR/dropbox-native-crash.txt" 2>&1 || true
  adb shell uiautomator dump /sdcard/quantune-window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/quantune-window.xml "$ARTIFACT_DIR/window-hierarchy.xml" >/dev/null 2>&1 || true
  adb shell screencap -p /sdcard/quantune-screen.png >/dev/null 2>&1 || true
  adb pull /sdcard/quantune-screen.png "$ARTIFACT_DIR/fullscreen-lyrics-screen.png" >/dev/null 2>&1 || true

  adb shell run-as com.dsann.music cat files/diagnostics/latest-report.txt \
    > "$ARTIFACT_DIR/quantune-diagnostic-report.txt" 2> "$ARTIFACT_DIR/diagnostic-report-read-error.txt" || true
  if [[ ! -s "$ARTIFACT_DIR/quantune-diagnostic-report.txt" ]]; then
    printf 'No app-generated ANR/error report was present.\n' > "$ARTIFACT_DIR/quantune-diagnostic-report.txt"
  fi

  # Emulator images are normally userdebug and permit root access to native crash
  # tombstones and framework ANR traces. Keep the test useful if a particular image
  # denies adb root or either directory is empty.
  adb root >/dev/null 2>&1 || true
  adb wait-for-device >/dev/null 2>&1 || true
  adb pull /data/tombstones "$ARTIFACT_DIR/tombstones" >/dev/null 2>&1 || true
  adb pull /data/anr "$ARTIFACT_DIR/anr-traces" >/dev/null 2>&1 || true
  adb shell ls -la /data/tombstones > "$ARTIFACT_DIR/tombstones-listing.txt" 2>&1 || true
  adb shell ls -la /data/anr > "$ARTIFACT_DIR/anr-traces-listing.txt" 2>&1 || true
}

if [[ ! -s "$APK" ]]; then
  echo "Expected emulator APK was not built: $APK" >&2
  exit 2
fi

adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb install -r "$APK"

for permission in \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.RECORD_AUDIO \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.ACCESS_FINE_LOCATION; do
  adb shell pm grant com.dsann.music "$permission" >/dev/null 2>&1 || true
done

adb logcat -c
adb shell am force-stop com.dsann.music || true
adb shell am start -W -n com.dsann.music/.MainActivity \
  --ez com.dsann.music.EXTRA_OPEN_FULLSCREEN_LYRICS_ANR_TEST true \
  --ez com.dsann.music.EXTRA_SKIP_DIAGNOSTIC_RECOVERY true \
  > "$ARTIFACT_DIR/activity-start.txt" 2>&1 || RESULT=1

# Wait for the debug-only entry point to seed a test song and request the real
# FullPlayerScreen fullscreen lyrics dialog.
for _ in $(seq 1 60); do
  if adb logcat -d -s QuantuneANRTest:I 2>/dev/null | grep -q 'FULLSCREEN_LYRICS_OPENED'; then
    OPENED=1
    break
  fi
  if ! adb shell pidof com.dsann.music >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if [[ "$OPENED" -ne 1 ]]; then
  echo "The test entry point did not reach the fullscreen lyrics request." >&2
  RESULT=1
else
  # Leave the overlay active long enough for Quantune's ANR watchdog and Android's
  # own ANR machinery to capture a blocked main thread, if the issue reproduces.
  sleep 15
fi

capture_logs

if grep -Eqi 'ANR in com\.dsann\.music|Input dispatching timed out' "$ARTIFACT_DIR/logcat-all.txt"; then
  ANR_FOUND=1
fi
if grep -Eqi 'Type: ANR|APP NOT RESPONDING' "$ARTIFACT_DIR/quantune-diagnostic-report.txt"; then
  ANR_FOUND=1
fi
if [[ "$ANR_FOUND" -eq 1 ]]; then
  echo "Fullscreen lyrics ANR reproduced. Diagnostic files were captured." >&2
  RESULT=1
fi

if [[ "$OPENED" -eq 1 ]] && ! grep -q 'LYRICS' "$ARTIFACT_DIR/window-hierarchy.xml"; then
  echo "The app requested fullscreen lyrics, but the visible UI hierarchy did not contain the LYRICS header." >&2
  RESULT=1
fi

{
  echo "api_level=23"
  echo "opened=$OPENED"
  echo "anr_detected=$ANR_FOUND"
  echo "result=$RESULT"
} > "$ARTIFACT_DIR/test-result.txt"

exit "$RESULT"
