#!/usr/bin/env bash
set -u

ARTIFACT_DIR="$PWD/anr-artifacts"
WORKSPACE_VALUE="$(printenv GITHUB_WORKSPACE || true)"
API_LEVEL="$(printenv ANDROID_API_LEVEL || true)"
ANDROID_MEMORY_MB="$(printenv ANDROID_MEMORY_MB || true)"
if [[ -z "$ANDROID_MEMORY_MB" ]]; then ANDROID_MEMORY_MB="3072"; fi
LYRICS_TEST_MODES="$(printenv LYRICS_TEST_MODES || true)"
if [[ -n "$WORKSPACE_VALUE" ]]; then ARTIFACT_DIR="$WORKSPACE_VALUE/anr-artifacts"; fi
if [[ -z "$API_LEVEL" ]]; then API_LEVEL="unknown"; fi
if [[ -z "$LYRICS_TEST_MODES" ]]; then LYRICS_TEST_MODES="empty,synced,plain,karaoke"; fi
APK="app/build/outputs/apk/debug/app-x86_64-debug.apk"
mkdir -p "$ARTIFACT_DIR"
OVERALL_RESULT=0

capture_logs() {
  local output_dir="$1"
  mkdir -p "$output_dir"
  timeout 25s adb logcat -b all -d -v threadtime > "$output_dir/logcat-all.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity activities > "$output_dir/activity-stack.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity lastanr > "$output_dir/activity-lastanr.txt" 2>&1 || true
  timeout 15s adb shell dumpsys dropbox --print data_app_anr > "$output_dir/dropbox-app-anr.txt" 2>&1 || true
  timeout 15s adb shell dumpsys dropbox --print data_app_native_crash > "$output_dir/dropbox-native-crash.txt" 2>&1 || true
  # Remove device-side captures first so a failed screencap/pull cannot silently
  # reuse a screenshot or accessibility tree from an earlier mode or emulator run.
  timeout 10s adb shell rm -f /sdcard/quantune-lyrics-window.xml /sdcard/quantune-lyrics-screen.png >/dev/null 2>&1 || true
  timeout 15s adb shell uiautomator dump /sdcard/quantune-lyrics-window.xml >/dev/null 2>&1 || true
  timeout 15s adb pull /sdcard/quantune-lyrics-window.xml "$output_dir/window-hierarchy.xml" >/dev/null 2>&1 || true
  timeout 15s adb shell screencap -p /sdcard/quantune-lyrics-screen.png >/dev/null 2>&1 || true
  timeout 15s adb pull /sdcard/quantune-lyrics-screen.png "$output_dir/screenshot.png" >/dev/null 2>&1 || true

  timeout 15s adb shell run-as com.dsann.music cat files/diagnostics/latest-report.txt \
    > "$output_dir/quantune-diagnostic-report.txt" 2> "$output_dir/diagnostic-report-read-error.txt" || true
  if [[ ! -s "$output_dir/quantune-diagnostic-report.txt" ]]; then
    printf 'No app-generated ANR/error report was present.\n' > "$output_dir/quantune-diagnostic-report.txt"
  fi

  timeout 15s adb root >/dev/null 2>&1 || true
  timeout 30s adb wait-for-device >/dev/null 2>&1 || true
  mkdir -p "$output_dir/system-tombstones" "$output_dir/system-anr-traces"
  timeout 30s adb pull /data/tombstones "$output_dir/system-tombstones" >/dev/null 2>&1 || true
  timeout 30s adb pull /data/anr "$output_dir/system-anr-traces" >/dev/null 2>&1 || true
  timeout 15s adb shell ls -la /data/tombstones > "$output_dir/tombstones-listing.txt" 2>&1 || true
  timeout 15s adb shell ls -la /data/anr > "$output_dir/anr-traces-listing.txt" 2>&1 || true
}

if [[ ! -s "$APK" ]]; then
  echo "Expected emulator APK was not built: $APK" >&2
  exit 2
fi

timeout 90s adb wait-for-device
timeout 30s adb shell settings put global window_animation_scale 0 || true
timeout 30s adb shell settings put global transition_animation_scale 0 || true
timeout 30s adb shell settings put global animator_duration_scale 0 || true
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

# First exercise the actual user path: MainActivity -> expanded player -> Lyrics action
# -> production fullscreen dialog. This is separate from the renderer-only modes below.
INTEGRATION_DIR="$ARTIFACT_DIR/player-integration"
mkdir -p "$INTEGRATION_DIR"
timeout 30s adb shell am force-stop com.dsann.music >/dev/null 2>&1 || true
timeout 15s adb shell run-as com.dsann.music rm -f \
  files/diagnostics/latest-report.txt files/diagnostics/latest-report.tmp >/dev/null 2>&1 || true
timeout 20s adb logcat -c || true

INTEGRATION_RESULT=0
PLAYER_LAYOUT_READY=0
PLAYER_FIRST_DRAW=0
LYRICS_REQUESTED=0
OVERLAY_MEASURED=0
CONTENT_READY=0
INTEGRATION_ANR=0
INTEGRATION_ANR_STAGE="none"

if ! timeout 90s adb shell am start -W -n com.dsann.music/.MainActivity \
  --ez com.dsann.music.EXTRA_OPEN_FULLSCREEN_LYRICS_ANR_TEST true \
  --ez com.dsann.music.EXTRA_SKIP_DIAGNOSTIC_RECOVERY true \
  > "$INTEGRATION_DIR/activity-start.txt" 2>&1; then
  echo "Could not launch MainActivity player-host lyrics diagnostic." >&2
  INTEGRATION_RESULT=1
fi

if ! grep -q 'Status: ok' "$INTEGRATION_DIR/activity-start.txt" 2>/dev/null; then
  echo "MainActivity diagnostic launch did not report Status: ok." >&2
  INTEGRATION_RESULT=1
fi

for _ in $(seq 1 120); do
  timeout 3s adb logcat -d -s QuantuneANRTest:I > "$INTEGRATION_DIR/test-markers-current.txt" 2>/dev/null || true
  grep -q 'FULL_PLAYER_LAYOUT_READY' "$INTEGRATION_DIR/test-markers-current.txt" && PLAYER_LAYOUT_READY=1
  grep -q 'FULL_PLAYER_FIRST_DRAW_COMPLETED' "$INTEGRATION_DIR/test-markers-current.txt" && PLAYER_FIRST_DRAW=1
  grep -q 'FULLSCREEN_LYRICS_REQUESTED' "$INTEGRATION_DIR/test-markers-current.txt" && LYRICS_REQUESTED=1
  grep -q 'FULLSCREEN_LYRICS_OVERLAY_MEASURED' "$INTEGRATION_DIR/test-markers-current.txt" && OVERLAY_MEASURED=1
  grep -q 'FULLSCREEN_LYRICS_CONTENT_READY' "$INTEGRATION_DIR/test-markers-current.txt" && CONTENT_READY=1

  REPORT_PRESENT="$(timeout 5s adb shell run-as com.dsann.music test -s files/diagnostics/latest-report.txt >/dev/null 2>&1; echo $?)"
  if [[ "$REPORT_PRESENT" -eq 0 ]]; then
    echo "Quantune watchdog report appeared during the MainActivity player-host flow." >&2
    break
  fi
  if [[ "$OVERLAY_MEASURED" -eq 1 && "$CONTENT_READY" -eq 1 ]]; then
    # Keep the real production dialog active beyond the five-second app watchdog.
    sleep 8
    timeout 3s adb logcat -d -s QuantuneANRTest:I > "$INTEGRATION_DIR/test-markers-current.txt" 2>/dev/null || true
    grep -q 'FULL_PLAYER_LAYOUT_READY' "$INTEGRATION_DIR/test-markers-current.txt" && PLAYER_LAYOUT_READY=1
    grep -q 'FULL_PLAYER_FIRST_DRAW_COMPLETED' "$INTEGRATION_DIR/test-markers-current.txt" && PLAYER_FIRST_DRAW=1
    grep -q 'FULLSCREEN_LYRICS_REQUESTED' "$INTEGRATION_DIR/test-markers-current.txt" && LYRICS_REQUESTED=1
    grep -q 'FULLSCREEN_LYRICS_CONTENT_READY' "$INTEGRATION_DIR/test-markers-current.txt" && CONTENT_READY=1
    break
  fi
  sleep 1
done

capture_logs "$INTEGRATION_DIR"

if grep -Eqi 'ANR in com\.dsann\.music|Input dispatching timed out' "$INTEGRATION_DIR/logcat-all.txt"; then
  INTEGRATION_ANR=1
  INTEGRATION_ANR_STAGE="android-framework"
fi
if grep -Eqi 'Type: ANR|APP NOT RESPONDING' "$INTEGRATION_DIR/quantune-diagnostic-report.txt"; then
  INTEGRATION_ANR=1
  if grep -q 'Overlay: Full-screen Lyrics' "$INTEGRATION_DIR/quantune-diagnostic-report.txt"; then
    INTEGRATION_ANR_STAGE="fullscreen-lyrics-overlay"
  elif [[ "$PLAYER_FIRST_DRAW" -eq 0 ]]; then
    INTEGRATION_ANR_STAGE="expanded-player-first-draw"
  elif [[ "$LYRICS_REQUESTED" -eq 0 ]]; then
    INTEGRATION_ANR_STAGE="after-player-first-draw-before-lyrics-request"
  else
    INTEGRATION_ANR_STAGE="fullscreen-lyrics-content-rendering"
  fi
fi

if [[ "$PLAYER_LAYOUT_READY" -ne 1 || "$PLAYER_FIRST_DRAW" -ne 1 || \
      "$LYRICS_REQUESTED" -ne 1 || "$OVERLAY_MEASURED" -ne 1 || "$CONTENT_READY" -ne 1 ]]; then
  echo "MainActivity did not complete the full player -> fullscreen Lyrics flow." >&2
  INTEGRATION_RESULT=1
fi
if [[ "$INTEGRATION_ANR" -eq 1 ]]; then
  echo "ANR captured during MainActivity player-host flow at stage=$INTEGRATION_ANR_STAGE" >&2
  INTEGRATION_RESULT=1
fi

{
  echo "api_level=$API_LEVEL"
  echo "test=MainActivity_full_player_to_fullscreen_lyrics"
  echo "player_layout_ready=$PLAYER_LAYOUT_READY"
  echo "player_first_draw=$PLAYER_FIRST_DRAW"
  echo "lyrics_requested=$LYRICS_REQUESTED"
  echo "overlay_measured=$OVERLAY_MEASURED"
  echo "content_ready=$CONTENT_READY"
  echo "anr_detected=$INTEGRATION_ANR"
  echo "anr_stage=$INTEGRATION_ANR_STAGE"
  echo "result=$INTEGRATION_RESULT"
} > "$INTEGRATION_DIR/test-result.txt"

if [[ "$INTEGRATION_RESULT" -ne 0 ]]; then OVERALL_RESULT=1; fi
# Close the fullscreen Lyrics dialog/diagnostic Activity but keep the app process
# alive. Compose's first-use class initialization is expensive on TCG emulators;
# force-stopping between modes reran it for every mode and generated misleading
# startup ANRs unrelated to the renderer under test.
timeout 15s adb shell input keyevent 4 >/dev/null 2>&1 || true
sleep 1

# Exercise every isolated production fullscreen renderer as well. These cases
# separate lyrics parsing/painting from the normal MainActivity/player host.
for MODE in $(printf '%s' "$LYRICS_TEST_MODES" | tr ',' ' '); do
  MODE_DIR="$ARTIFACT_DIR/$MODE"
  mkdir -p "$MODE_DIR"
  echo "Testing production fullscreen lyrics overlay mode=$MODE api=$API_LEVEL"

    timeout 15s adb shell run-as com.dsann.music rm -f \
    files/diagnostics/latest-report.txt files/diagnostics/latest-report.tmp >/dev/null 2>&1 || true
  timeout 20s adb logcat -c || true

  BASE_LAYOUT_READY=0
  LYRICS_REQUESTED=0
  OVERLAY_MEASURED=0
  CONTENT_READY=0
  SYNCED_PREPARED=0
  KARAOKE_TEXT_LAYOUT=0
  KARAOKE_PLAYBACK_ADVANCED=0
  ANR_FOUND=0
  ANR_STAGE="none"
  MODE_RESULT=0

  if ! timeout 90s adb shell am start -W \
    -n com.dsann.music/.FullscreenLyricsDiagnosticActivity \
    --es com.dsann.music.EXTRA_LYRICS_DIAGNOSTIC_MODE "$MODE" \
    > "$MODE_DIR/activity-start.txt" 2>&1; then
    echo "Android failed to launch the isolated fullscreen lyrics diagnostic Activity ($MODE)." >&2
    MODE_RESULT=1
  fi

  if ! grep -q 'Status: ok' "$MODE_DIR/activity-start.txt" 2>/dev/null; then
    echo "Diagnostic Activity launch did not report Status: ok ($MODE)." >&2
    MODE_RESULT=1
  fi

  for _ in $(seq 1 45); do
    timeout 3s adb logcat -d -s QuantuneANRTest:I > "$MODE_DIR/test-markers-current.txt" 2>/dev/null || true
    grep -q 'DIAGNOSTIC_BASE_LAYOUT_READY' "$MODE_DIR/test-markers-current.txt" && BASE_LAYOUT_READY=1
    grep -q 'FULLSCREEN_LYRICS_REQUESTED' "$MODE_DIR/test-markers-current.txt" && LYRICS_REQUESTED=1
    grep -q 'FULLSCREEN_LYRICS_OVERLAY_MEASURED' "$MODE_DIR/test-markers-current.txt" && OVERLAY_MEASURED=1
    grep -q 'FULLSCREEN_LYRICS_CONTENT_READY' "$MODE_DIR/test-markers-current.txt" && CONTENT_READY=1
    grep -q 'SYNCED_LYRICS_PREPARED' "$MODE_DIR/test-markers-current.txt" && SYNCED_PREPARED=1
    grep -q 'KARAOKE_TEXT_LAYOUT_READY' "$MODE_DIR/test-markers-current.txt" && KARAOKE_TEXT_LAYOUT=1
    grep -q 'KARAOKE_PLAYBACK_ADVANCED' "$MODE_DIR/test-markers-current.txt" && KARAOKE_PLAYBACK_ADVANCED=1

    REPORT_PRESENT="$(timeout 5s adb shell run-as com.dsann.music test -s files/diagnostics/latest-report.txt >/dev/null 2>&1; echo $?)"
    if [[ "$REPORT_PRESENT" -eq 0 ]]; then
      echo "Quantune watchdog report appeared before mode=$MODE completed." >&2
      break
    fi
    if [[ "$OVERLAY_MEASURED" -eq 1 && "$CONTENT_READY" -eq 1 ]]; then
      # Keep the actual overlay active beyond the app watchdog's five-second threshold.
      sleep 8
      timeout 3s adb logcat -d -s QuantuneANRTest:I > "$MODE_DIR/test-markers-current.txt" 2>/dev/null || true
      grep -q 'SYNCED_LYRICS_PREPARED' "$MODE_DIR/test-markers-current.txt" && SYNCED_PREPARED=1
      grep -q 'KARAOKE_TEXT_LAYOUT_READY' "$MODE_DIR/test-markers-current.txt" && KARAOKE_TEXT_LAYOUT=1
      grep -q 'KARAOKE_PLAYBACK_ADVANCED' "$MODE_DIR/test-markers-current.txt" && KARAOKE_PLAYBACK_ADVANCED=1
      break
    fi
    sleep 1
  done

  capture_logs "$MODE_DIR"

  if grep -Eqi 'ANR in com\.dsann\.music|Input dispatching timed out' "$MODE_DIR/logcat-all.txt"; then
    ANR_FOUND=1
    ANR_STAGE="android-framework"
  fi
  if grep -Eqi 'Type: ANR|APP NOT RESPONDING' "$MODE_DIR/quantune-diagnostic-report.txt"; then
    ANR_FOUND=1
    if grep -q 'Overlay: Full-screen Lyrics' "$MODE_DIR/quantune-diagnostic-report.txt"; then
      ANR_STAGE="fullscreen-lyrics-overlay"
    elif [[ "$OVERLAY_MEASURED" -eq 0 ]]; then
      ANR_STAGE="fullscreen-lyrics-first-layout"
    elif [[ "$CONTENT_READY" -eq 0 ]]; then
      ANR_STAGE="fullscreen-lyrics-content-gate"
    else
      ANR_STAGE="lyrics-content-rendering-$MODE"
    fi
  fi

  if [[ "$BASE_LAYOUT_READY" -ne 1 || "$LYRICS_REQUESTED" -ne 1 || "$OVERLAY_MEASURED" -ne 1 || "$CONTENT_READY" -ne 1 ]]; then
    echo "Fullscreen lyrics did not reach all required overlay milestones for mode=$MODE." >&2
    MODE_RESULT=1
  fi
  if [[ "$MODE" == "synced" || "$MODE" == "karaoke" ]]; then
    if [[ "$SYNCED_PREPARED" -ne 1 ]]; then
      echo "Synced lyric data did not finish preparation for mode=$MODE." >&2
      MODE_RESULT=1
    fi
  fi
  if [[ "$MODE" == "karaoke" && "$KARAOKE_TEXT_LAYOUT" -ne 1 ]]; then
    echo "Karaoke text never completed its first TextLayoutResult." >&2
    MODE_RESULT=1
  fi
  if [[ "$MODE" == "karaoke" && "$KARAOKE_PLAYBACK_ADVANCED" -ne 1 ]]; then
    echo "Karaoke playback position did not advance through live renderer frames." >&2
    MODE_RESULT=1
  fi
  if [[ "$ANR_FOUND" -eq 1 ]]; then
    echo "Quantune ANR captured during isolated fullscreen lyrics mode=$MODE stage=$ANR_STAGE" >&2
    MODE_RESULT=1
  fi

  {
    echo "api_level=$API_LEVEL"
    echo "android_memory_mb=$ANDROID_MEMORY_MB"
    echo "test=isolated_production_fullscreen_lyrics"
    echo "mode=$MODE"
    echo "base_layout_ready=$BASE_LAYOUT_READY"
    echo "lyrics_requested=$LYRICS_REQUESTED"
    echo "overlay_measured=$OVERLAY_MEASURED"
    echo "content_ready=$CONTENT_READY"
    echo "synced_lyrics_prepared=$SYNCED_PREPARED"
    echo "karaoke_text_layout=$KARAOKE_TEXT_LAYOUT"
    echo "karaoke_playback_advanced=$KARAOKE_PLAYBACK_ADVANCED"
    echo "anr_detected=$ANR_FOUND"
    echo "anr_stage=$ANR_STAGE"
    echo "result=$MODE_RESULT"
  } > "$MODE_DIR/test-result.txt"

  if [[ "$MODE_RESULT" -ne 0 ]]; then OVERALL_RESULT=1; fi
  # Close the fullscreen Lyrics dialog/diagnostic Activity but keep the app process
# alive. Compose's first-use class initialization is expensive on TCG emulators;
# force-stopping between modes reran it for every mode and generated misleading
# startup ANRs unrelated to the renderer under test.
timeout 15s adb shell input keyevent 4 >/dev/null 2>&1 || true
sleep 1
done

{
  echo "api_level=$API_LEVEL"
  echo "test=MainActivity_player_integration_plus_isolated_fullscreen_lyrics_all_modes"
  echo "player_integration=$INTEGRATION_RESULT"
  echo "result=$OVERALL_RESULT"
  for MODE in $(printf '%s' "$LYRICS_TEST_MODES" | tr ',' ' '); do
    echo "mode_$MODE=$(grep '^result=' "$ARTIFACT_DIR/$MODE/test-result.txt" 2>/dev/null | cut -d= -f2 || echo missing)"
  done
} > "$ARTIFACT_DIR/test-result.txt"

# Clean up the emulator after every mode has been tested in one warmed process.
timeout 30s adb shell am force-stop com.dsann.music >/dev/null 2>&1 || true
exit "$OVERALL_RESULT"
