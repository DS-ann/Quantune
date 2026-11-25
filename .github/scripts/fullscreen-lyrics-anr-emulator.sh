#!/usr/bin/env bash
set -u

ARTIFACT_DIR="${GITHUB_WORKSPACE:-$PWD}/anr-artifacts"
APK="app/build/outputs/apk/debug/app-x86_64-debug.apk"
mkdir -p "$ARTIFACT_DIR"
RESULT=0
PLAYER_LAYOUT_READY=0
PLAYER_FIRST_DRAW=0
LYRICS_REQUESTED=0
OVERLAY_MEASURED=0
ANR_FOUND=0
ANR_STAGE="none"

capture_logs() {
  timeout 25s adb logcat -b all -d -v threadtime > "$ARTIFACT_DIR/logcat-all.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity activities > "$ARTIFACT_DIR/activity-stack.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity lastanr > "$ARTIFACT_DIR/activity-lastanr.txt" 2>&1 || true
  timeout 15s adb shell dumpsys dropbox --print data_app_anr > "$ARTIFACT_DIR/dropbox-app-anr.txt" 2>&1 || true
  timeout 15s adb shell dumpsys dropbox --print data_app_native_crash > "$ARTIFACT_DIR/dropbox-native-crash.txt" 2>&1 || true
  timeout 15s adb shell uiautomator dump /sdcard/quantune-player-host-window.xml >/dev/null 2>&1 || true
  timeout 15s adb pull /sdcard/quantune-player-host-window.xml "$ARTIFACT_DIR/window-hierarchy.xml" >/dev/null 2>&1 || true
  timeout 15s adb shell screencap -p /sdcard/quantune-player-host-screen.png >/dev/null 2>&1 || true
  timeout 15s adb pull /sdcard/quantune-player-host-screen.png "$ARTIFACT_DIR/screenshot.png" >/dev/null 2>&1 || true

  timeout 15s adb shell run-as com.dsann.music cat files/diagnostics/latest-report.txt \
    > "$ARTIFACT_DIR/quantune-diagnostic-report.txt" 2> "$ARTIFACT_DIR/diagnostic-report-read-error.txt" || true
  if [[ ! -s "$ARTIFACT_DIR/quantune-diagnostic-report.txt" ]]; then
    printf 'No app-generated ANR/error report was present.\n' > "$ARTIFACT_DIR/quantune-diagnostic-report.txt"
  fi

  timeout 15s adb root >/dev/null 2>&1 || true
  timeout 30s adb wait-for-device >/dev/null 2>&1 || true
  mkdir -p "$ARTIFACT_DIR/system-tombstones" "$ARTIFACT_DIR/system-anr-traces"
  timeout 30s adb pull /data/tombstones "$ARTIFACT_DIR/system-tombstones" >/dev/null 2>&1 || true
  timeout 30s adb pull /data/anr "$ARTIFACT_DIR/system-anr-traces" >/dev/null 2>&1 || true
  timeout 15s adb shell ls -la /data/tombstones > "$ARTIFACT_DIR/tombstones-listing.txt" 2>&1 || true
  timeout 15s adb shell ls -la /data/anr > "$ARTIFACT_DIR/anr-traces-listing.txt" 2>&1 || true
}

if [[ ! -s "$APK" ]]; then
  echo "Expected emulator APK was not built: $APK" >&2
  exit 2
fi

timeout 90s adb wait-for-device
timeout 30s adb shell settings put global window_animation_scale 0 || true
timeout 30s adb shell settings put global transition_animation_scale 0 || true
timeout 30s adb shell settings put global animator_duration_scale 0 || true
# Dismiss Android 6's first-use immersive-mode confirmation so screenshots show
# the actual fullscreen player/lyrics UI, not the operating-system tip.
timeout 20s adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true

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

timeout 30s adb shell am force-stop com.dsann.music >/dev/null 2>&1 || true
timeout 15s adb shell run-as com.dsann.music rm -f \
  files/diagnostics/latest-report.txt files/diagnostics/latest-report.tmp >/dev/null 2>&1 || true
timeout 20s adb logcat -c || true

if ! timeout 90s adb shell am start -W -n com.dsann.music/.MainActivity \
  --ez com.dsann.music.EXTRA_OPEN_FULLSCREEN_LYRICS_ANR_TEST true \
  --ez com.dsann.music.EXTRA_SKIP_DIAGNOSTIC_RECOVERY true \
  > "$ARTIFACT_DIR/activity-start.txt" 2>&1; then
  echo "Android failed to launch MainActivity for the expanded-player test." >&2
  cat "$ARTIFACT_DIR/activity-start.txt" >&2 || true
  capture_logs
  RESULT=1
fi

if ! grep -q 'Status: ok' "$ARTIFACT_DIR/activity-start.txt" 2>/dev/null; then
  echo "MainActivity launch did not report Status: ok." >&2
  capture_logs
  RESULT=1
fi

# The app logs these milestones from the actual measured expanded player and
# the actual production FullScreenLyricsOverlay. The watchdog report is also
# checked in case the player tree blocks before either milestone can be reached.
for _ in $(seq 1 60); do
  timeout 3s adb logcat -d -s QuantuneANRTest:I > "$ARTIFACT_DIR/test-markers-current.txt" 2>/dev/null || true
  if grep -q 'FULL_PLAYER_LAYOUT_READY' "$ARTIFACT_DIR/test-markers-current.txt"; then
    PLAYER_LAYOUT_READY=1
  fi
  if grep -q 'FULL_PLAYER_FIRST_DRAW_COMPLETED' "$ARTIFACT_DIR/test-markers-current.txt"; then
    PLAYER_FIRST_DRAW=1
  fi
  if grep -q 'FULLSCREEN_LYRICS_REQUESTED' "$ARTIFACT_DIR/test-markers-current.txt"; then
    LYRICS_REQUESTED=1
  fi
  if grep -q 'FULLSCREEN_LYRICS_OVERLAY_MEASURED' "$ARTIFACT_DIR/test-markers-current.txt"; then
    OVERLAY_MEASURED=1
    break
  fi

  REPORT_PRESENT="$(timeout 5s adb shell run-as com.dsann.music test -s files/diagnostics/latest-report.txt >/dev/null 2>&1; echo $?)"
  if [[ "$REPORT_PRESENT" -eq 0 ]]; then
    echo "Quantune's watchdog report appeared before fullscreen lyrics completed layout." >&2
    break
  fi
  sleep 1
done

if [[ "$OVERLAY_MEASURED" -eq 1 ]]; then
  # Leave the production overlay active beyond the watchdog's five-second window.
  sleep 8
else
  echo "The expanded-player-to-lyrics flow did not reach the overlay layout marker." >&2
  RESULT=1
fi

capture_logs

if grep -Eqi 'ANR in com\.dsann\.music|Input dispatching timed out' "$ARTIFACT_DIR/logcat-all.txt"; then
  ANR_FOUND=1
  ANR_STAGE="android-framework"
fi
if grep -Eqi 'Type: ANR|APP NOT RESPONDING' "$ARTIFACT_DIR/quantune-diagnostic-report.txt"; then
  ANR_FOUND=1
  if grep -q 'Overlay: Full-screen Lyrics' "$ARTIFACT_DIR/quantune-diagnostic-report.txt"; then
    ANR_STAGE="fullscreen-lyrics-overlay"
  elif [[ "$PLAYER_FIRST_DRAW" -eq 0 ]]; then
    ANR_STAGE="expanded-player-first-draw"
  elif [[ "$LYRICS_REQUESTED" -eq 0 ]]; then
    ANR_STAGE="after-player-first-draw-before-lyrics-request"
  elif grep -q 'Full Player' "$ARTIFACT_DIR/quantune-diagnostic-report.txt"; then
    ANR_STAGE="lyrics-transition-before-overlay"
  else
    ANR_STAGE="main-activity-or-player-startup"
  fi
fi

if [[ "$ANR_FOUND" -eq 1 ]]; then
  echo "Quantune ANR captured during expanded-player/fullscreen-lyrics integration: $ANR_STAGE" >&2
  RESULT=1
fi
if [[ "$PLAYER_LAYOUT_READY" -ne 1 ]]; then
  echo "The expanded player never reported a completed layout." >&2
  RESULT=1
fi
if [[ "$PLAYER_FIRST_DRAW" -ne 1 ]]; then
  echo "The expanded player never completed its first content draw." >&2
  RESULT=1
fi
if [[ "$LYRICS_REQUESTED" -ne 1 ]]; then
  echo "Fullscreen lyrics were not requested after the expanded player's first draw." >&2
  RESULT=1
fi
if [[ "$OVERLAY_MEASURED" -ne 1 ]]; then
  echo "The fullscreen lyrics overlay did not complete its first layout." >&2
  RESULT=1
fi

{
  echo "api_level=23"
  echo "test=expanded_player_then_fullscreen_lyrics"
  echo "player_layout_ready=$PLAYER_LAYOUT_READY"
  echo "player_first_draw_completed=$PLAYER_FIRST_DRAW"
  echo "lyrics_requested=$LYRICS_REQUESTED"
  echo "overlay_measured=$OVERLAY_MEASURED"
  echo "anr_detected=$ANR_FOUND"
  echo "anr_stage=$ANR_STAGE"
  echo "result=$RESULT"
} > "$ARTIFACT_DIR/test-result.txt"

exit "$RESULT"
