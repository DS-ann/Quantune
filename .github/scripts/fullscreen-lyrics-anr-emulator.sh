#!/usr/bin/env bash
set -u

ARTIFACT_DIR="${GITHUB_WORKSPACE:-$PWD}/anr-artifacts"
APK="app/build/outputs/apk/debug/app-x86_64-debug.apk"
mkdir -p "$ARTIFACT_DIR"
RESULT=0
TARGET_ANR_FOUND=0
OVERLAY_MODES_REACHED=0

capture_mode() {
  local mode="$1"
  local dest="$ARTIFACT_DIR/$mode"
  mkdir -p "$dest"

  timeout 25s adb logcat -b all -d -v threadtime > "$dest/logcat-all.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity activities > "$dest/activity-stack.txt" 2>&1 || true
  timeout 15s adb shell dumpsys dropbox --print data_app_anr > "$dest/dropbox-app-anr.txt" 2>&1 || true
  timeout 15s adb shell dumpsys dropbox --print data_app_native_crash > "$dest/dropbox-native-crash.txt" 2>&1 || true
  timeout 15s adb shell uiautomator dump "/sdcard/quantune-$mode-window.xml" >/dev/null 2>&1 || true
  timeout 15s adb pull "/sdcard/quantune-$mode-window.xml" "$dest/window-hierarchy.xml" >/dev/null 2>&1 || true
  timeout 15s adb shell screencap -p "/sdcard/quantune-$mode-screen.png" >/dev/null 2>&1 || true
  timeout 15s adb pull "/sdcard/quantune-$mode-screen.png" "$dest/screenshot.png" >/dev/null 2>&1 || true

  timeout 15s adb shell run-as com.dsann.music cat files/diagnostics/latest-report.txt \
    > "$dest/quantune-diagnostic-report.txt" 2> "$dest/diagnostic-report-read-error.txt" || true
  if [[ ! -s "$dest/quantune-diagnostic-report.txt" ]]; then
    printf 'No app-generated ANR/error report was present for mode=%s.\n' "$mode" \
      > "$dest/quantune-diagnostic-report.txt"
  fi

  if grep -q 'FULLSCREEN_LYRICS_OVERLAY_MEASURED' "$dest/logcat-all.txt"; then
    echo "overlay_measured=1" > "$dest/test-result.txt"
  else
    echo "overlay_measured=0" > "$dest/test-result.txt"
  fi

  if grep -Eqi 'Type: ANR|APP NOT RESPONDING' "$dest/quantune-diagnostic-report.txt"; then
    echo "app_watchdog_anr=1" >> "$dest/test-result.txt"
  else
    echo "app_watchdog_anr=0" >> "$dest/test-result.txt"
  fi

  if grep -q 'Overlay: Full-screen Lyrics' "$dest/quantune-diagnostic-report.txt"; then
    echo "anr_inside_fullscreen_overlay=1" >> "$dest/test-result.txt"
  else
    echo "anr_inside_fullscreen_overlay=0" >> "$dest/test-result.txt"
  fi
}

if [[ ! -s "$APK" ]]; then
  echo "Expected emulator APK was not built: $APK" >&2
  exit 2
fi

timeout 90s adb wait-for-device
timeout 30s adb shell settings put global window_animation_scale 0 || true
timeout 30s adb shell settings put global transition_animation_scale 0 || true
timeout 30s adb shell settings put global animator_duration_scale 0 || true

# This is a 38 MB multidex APK. Android 6 on a TCG-only runner can spend minutes
# in dex2oat, so the install timeout is intentionally generous and its output is
# retained for diagnosis.
INSTALL_LOG="$ARTIFACT_DIR/install-output.txt"
if ! timeout 600s adb install -r "$APK" > "$INSTALL_LOG" 2>&1; then
  echo "Quantune APK installation failed or exceeded the 600-second TCG allowance:" >&2
  cat "$INSTALL_LOG" >&2 || true
  exit 1
fi
cat "$INSTALL_LOG"

PACKAGE_PATH="$(timeout 60s adb shell pm path com.dsann.music 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$PACKAGE_PATH" ]]; then
  echo "adb install reported success, but PackageManager cannot resolve com.dsann.music." >&2
  exit 1
fi
printf '%s\n' "$PACKAGE_PATH" > "$ARTIFACT_DIR/installed-package-path.txt"

# Run the exact production fullscreen overlay in isolation, for three content
# states. This excludes Explore, the player sheet, service startup and provider
# network fan-out, so each captured stack identifies the renderer path being tested.
for mode in empty synced plain; do
  echo "Starting isolated fullscreen lyrics mode=$mode"

  timeout 30s adb shell am force-stop com.dsann.music >/dev/null 2>&1 || true
  timeout 15s adb shell run-as com.dsann.music rm -f \
    files/diagnostics/latest-report.txt files/diagnostics/latest-report.tmp >/dev/null 2>&1 || true
  timeout 20s adb logcat -c || true

  if ! timeout 90s adb shell am start -W -n com.dsann.music/.FullscreenLyricsDiagnosticActivity \
    --es com.dsann.music.EXTRA_LYRICS_DIAGNOSTIC_MODE "$mode" \
    > "$ARTIFACT_DIR/$mode/activity-start.txt" 2>&1; then
    echo "Could not launch isolated lyrics diagnostic activity for mode=$mode" >&2
    cat "$ARTIFACT_DIR/$mode/activity-start.txt" >&2 2>/dev/null || true
    RESULT=1
    capture_mode "$mode"
    continue
  fi

  measured=0
  for _ in $(seq 1 90); do
    if timeout 12s adb logcat -d -s QuantuneANRTest:I 2>/dev/null \
      | grep -q 'FULLSCREEN_LYRICS_OVERLAY_MEASURED'; then
      measured=1
      break
    fi
    sleep 1
  done

  if [[ "$measured" -eq 1 ]]; then
    OVERLAY_MODES_REACHED=$((OVERLAY_MODES_REACHED + 1))
    # The app watchdog threshold is 5 seconds. Observe for a further 8 seconds
    # after the overlay has actually completed layout, not just after an intent.
    sleep 8
  else
    echo "Fullscreen lyrics overlay never completed layout for mode=$mode" >&2
    RESULT=1
    sleep 2
  fi

  capture_mode "$mode"

  if grep -q 'anr_inside_fullscreen_overlay=1' "$ARTIFACT_DIR/$mode/test-result.txt"; then
    TARGET_ANR_FOUND=1
    RESULT=1
    echo "ANR captured while the fullscreen lyrics overlay was active in mode=$mode" >&2
  fi

  if [[ "$measured" -eq 1 ]] && ! grep -q 'LYRICS' "$ARTIFACT_DIR/$mode/window-hierarchy.xml"; then
    # A watchdog report may replace the overlay before hierarchy capture. The
    # overlay-measured log is the authoritative evidence that its layout completed.
    if ! grep -Eqi 'Type: ANR|APP NOT RESPONDING' "$ARTIFACT_DIR/$mode/quantune-diagnostic-report.txt"; then
      echo "Overlay completed layout but no LYRICS header was visible for mode=$mode" >&2
      RESULT=1
    fi
  fi

  {
    echo "mode=$mode"
    echo "overlay_measured=$measured"
    echo "app_watchdog_anr=$(grep -c 'app_watchdog_anr=1' "$ARTIFACT_DIR/$mode/test-result.txt" || true)"
    echo "anr_inside_fullscreen_overlay=$(grep -c 'anr_inside_fullscreen_overlay=1' "$ARTIFACT_DIR/$mode/test-result.txt" || true)"
  } > "$ARTIFACT_DIR/$mode/summary.txt"
done

# Save a combined logcat and raw framework ANR/native-crash material after all
# isolated cases have run. API 23 may not populate these folders for app watchdog
# reports, but they remain useful for a framework-level ANR or native crash.
timeout 25s adb logcat -b all -d -v threadtime > "$ARTIFACT_DIR/logcat-all.txt" 2>&1 || true
timeout 15s adb root >/dev/null 2>&1 || true
timeout 30s adb wait-for-device >/dev/null 2>&1 || true
mkdir -p "$ARTIFACT_DIR/system-tombstones" "$ARTIFACT_DIR/system-anr-traces"
timeout 30s adb pull /data/tombstones "$ARTIFACT_DIR/system-tombstones" >/dev/null 2>&1 || true
timeout 30s adb pull /data/anr "$ARTIFACT_DIR/system-anr-traces" >/dev/null 2>&1 || true
timeout 15s adb shell ls -la /data/tombstones > "$ARTIFACT_DIR/tombstones-listing.txt" 2>&1 || true
timeout 15s adb shell ls -la /data/anr > "$ARTIFACT_DIR/anr-traces-listing.txt" 2>&1 || true

{
  echo "api_level=23"
  echo "modes_tested=empty,synced,plain"
  echo "overlay_modes_reached=$OVERLAY_MODES_REACHED"
  echo "target_anr_detected=$TARGET_ANR_FOUND"
  echo "result=$RESULT"
} > "$ARTIFACT_DIR/test-result.txt"

exit "$RESULT"
