#!/usr/bin/env bash
set -u

ARTIFACT_DIR="${GITHUB_WORKSPACE:-$PWD}/anr-artifacts"
APK="app/build/outputs/apk/debug/app-x86_64-debug.apk"
mkdir -p "$ARTIFACT_DIR/tombstones" "$ARTIFACT_DIR/anr-traces"
RESULT=0
OPENED=0
ANR_FOUND=0

capture_logs() {
  timeout 20s adb logcat -b all -d -v threadtime > "$ARTIFACT_DIR/logcat-all.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity lastanr > "$ARTIFACT_DIR/activity-lastanr.txt" 2>&1 || true
  timeout 15s adb shell dumpsys dropbox --print data_app_anr > "$ARTIFACT_DIR/dropbox-app-anr.txt" 2>&1 || true
  timeout 15s adb shell dumpsys dropbox --print data_app_native_crash > "$ARTIFACT_DIR/dropbox-native-crash.txt" 2>&1 || true
  timeout 15s adb shell uiautomator dump /sdcard/quantune-window.xml >/dev/null 2>&1 || true
  timeout 15s adb pull /sdcard/quantune-window.xml "$ARTIFACT_DIR/window-hierarchy.xml" >/dev/null 2>&1 || true
  timeout 15s adb shell screencap -p /sdcard/quantune-screen.png >/dev/null 2>&1 || true
  timeout 15s adb pull /sdcard/quantune-screen.png "$ARTIFACT_DIR/fullscreen-lyrics-screen.png" >/dev/null 2>&1 || true

  timeout 15s adb shell run-as com.dsann.music cat files/diagnostics/latest-report.txt \
    > "$ARTIFACT_DIR/quantune-diagnostic-report.txt" 2> "$ARTIFACT_DIR/diagnostic-report-read-error.txt" || true
  if [[ ! -s "$ARTIFACT_DIR/quantune-diagnostic-report.txt" ]]; then
    printf 'No app-generated ANR/error report was present.\n' > "$ARTIFACT_DIR/quantune-diagnostic-report.txt"
  fi

  # Emulator images are normally userdebug and permit root access to native crash
  # tombstones and framework ANR traces. Keep the test useful if a particular image
  # denies adb root or either directory is empty.
  timeout 15s adb root >/dev/null 2>&1 || true
  timeout 30s adb wait-for-device >/dev/null 2>&1 || true
  timeout 30s adb pull /data/tombstones "$ARTIFACT_DIR/tombstones" >/dev/null 2>&1 || true
  timeout 30s adb pull /data/anr "$ARTIFACT_DIR/anr-traces" >/dev/null 2>&1 || true
  timeout 15s adb shell ls -la /data/tombstones > "$ARTIFACT_DIR/tombstones-listing.txt" 2>&1 || true
  timeout 15s adb shell ls -la /data/anr > "$ARTIFACT_DIR/anr-traces-listing.txt" 2>&1 || true
}

write_result() {
  {
    echo "api_level=23"
    echo "opened=$OPENED"
    echo "anr_detected=$ANR_FOUND"
    echo "result=$RESULT"
  } > "$ARTIFACT_DIR/test-result.txt"
}

if [[ ! -s "$APK" ]]; then
  echo "Expected emulator APK was not built: $APK" >&2
  exit 2
fi

timeout 90s adb wait-for-device
timeout 30s adb shell settings put global window_animation_scale 0 || true
timeout 30s adb shell settings put global transition_animation_scale 0 || true
timeout 30s adb shell settings put global animator_duration_scale 0 || true

# Android 6 on GitHub's TCG-only emulator takes substantially longer to verify
# Quantune's large multidex APK than a hardware-accelerated device. The former
# 60-second limit expired during dex2oat, leaving no installed package to launch.
# Preserve install output and verify pm path before running app-specific commands.
INSTALL_LOG="$ARTIFACT_DIR/install-output.txt"
if ! timeout 600s adb install -r "$APK" > "$INSTALL_LOG" 2>&1; then
  echo "Quantune APK installation failed or exceeded the 600-second TCG allowance:" >&2
  cat "$INSTALL_LOG" >&2 || true
  capture_logs
  RESULT=1
  write_result
  exit "$RESULT"
fi
cat "$INSTALL_LOG"

PACKAGE_PATH="$(timeout 60s adb shell pm path com.dsann.music 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$PACKAGE_PATH" ]]; then
  echo "adb install reported success, but PackageManager cannot resolve com.dsann.music." >&2
  capture_logs
  RESULT=1
  write_result
  exit "$RESULT"
fi
printf '%s\n' "$PACKAGE_PATH" > "$ARTIFACT_DIR/installed-package-path.txt"

for permission in \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.RECORD_AUDIO \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.ACCESS_FINE_LOCATION; do
  timeout 30s adb shell pm grant com.dsann.music "$permission" >/dev/null 2>&1 || true
done

timeout 30s adb logcat -c || true
timeout 30s adb shell am force-stop com.dsann.music || true
if ! timeout 90s adb shell am start -W -n com.dsann.music/.MainActivity \
  --ez com.dsann.music.EXTRA_OPEN_FULLSCREEN_LYRICS_ANR_TEST true \
  --ez com.dsann.music.EXTRA_SKIP_DIAGNOSTIC_RECOVERY true \
  > "$ARTIFACT_DIR/activity-start.txt" 2>&1; then
  echo "Android failed to start Quantune's MainActivity:" >&2
  cat "$ARTIFACT_DIR/activity-start.txt" >&2 || true
  capture_logs
  RESULT=1
  write_result
  exit "$RESULT"
fi

# Wait longer for cold startup under software CPU emulation. Poll the app's own
# marker instead of treating an emulator boot or launcher screenshot as success.
for _ in $(seq 1 180); do
  if timeout 15s adb logcat -d -s QuantuneANRTest:I 2>/dev/null | grep -q 'FULLSCREEN_LYRICS_OPENED'; then
    OPENED=1
    break
  fi
  sleep 1
done

if [[ "$OPENED" -ne 1 ]]; then
  echo "Quantune launched, but the debug test entry point did not reach the fullscreen lyrics request." >&2
  RESULT=1
else
  # Leave the overlay active long enough for Quantune's ANR watchdog and Android's
  # own ANR machinery to capture a blocked main thread, if the issue reproduces.
  sleep 20
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

write_result
exit "$RESULT"
