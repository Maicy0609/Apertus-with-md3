#!/usr/bin/env bash
#
# Apertus Android launch smoke test.
#
# Runs inside the GitHub Actions emulator job. It installs the freshly built
# debug APK, launches it the way a user does -- through the LAUNCHER intent, so
# Android resolves the activity class from AndroidManifest.xml -- and then fails
# the job if the app cannot be resolved, dies, or logs a fatal exception.
#
# This is the regression gate for the "APK opens then immediately exits" bug:
# a relative android:name (".MainActivity") resolves against the AGP namespace
# (app.apertus) instead of the Kotlin package (app), producing a
# ClassNotFoundException at activity instantiation.

set -uo pipefail

PACKAGE="app.apertus"
EXPECTED_ACTIVITY="app.apertus/app.MainActivity"
OUT_DIR="smoke"
LOG="$OUT_DIR/logcat.txt"
CRASH_LOG="$OUT_DIR/crash-buffer.txt"

mkdir -p "$OUT_DIR"

APK=$(find apk -name '*.apk' -print -quit)
if [ -z "$APK" ]; then
  echo "::error title=APK::no .apk found under apk/"
  exit 1
fi
echo "== APK: $APK"

adb wait-for-device
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1

echo "== Installing"
if ! adb install -r -t "$APK"; then
  echo "::error title=Install::adb install failed"
  exit 1
fi

adb shell am force-stop "$PACKAGE" >/dev/null 2>&1

echo "== Launcher activity resolved from AndroidManifest.xml"
RESOLVED=$(adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PACKAGE" 2>/dev/null \
  | tr -d '\r' | grep -v '^[[:space:]]*$' | tail -n1)
echo "resolved: '$RESOLVED'"
echo "expected: '$EXPECTED_ACTIVITY'"

START_TS=$(adb shell date +"%m-%d %H:%M:%S.000" | tr -d '\r')
adb shell log -t APERTUS_SMOKE "launch marker" >/dev/null 2>&1

echo "== Launching via LAUNCHER intent (monkey)"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 2>&1 | tail -n 6

echo "== Waiting 20s for the first frames"
sleep 20

echo "== Resumed activity"
adb shell dumpsys activity activities 2>/dev/null | grep -iE 'ResumedActivity' | head -n 3

echo "== Process state"
PID=$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | tr -d '\n')
echo "pid: '${PID:-<none>}'"

# Time-sliced main log: only what happened after we launched.
adb logcat -d -v threadtime -t "$START_TS" > "$LOG" 2>&1 || adb logcat -d -v threadtime > "$LOG" 2>&1
# Dedicated crash buffer - contains only crash traces, survives logcat -c.
adb logcat -b crash -d -v threadtime > "$CRASH_LOG" 2>&1 || true

echo "== main log: $(wc -l < "$LOG") lines | crash buffer: $(wc -l < "$CRASH_LOG") lines"
echo "----------------- app-related log -----------------"
grep -E "app\.apertus|AndroidRuntime|ActivityTaskManager|APERTUS_SMOKE" "$LOG" | tail -n 60 || true
echo "----------------- crash buffer -------------------"
tail -n 60 "$CRASH_LOG" || true
echo "-------------------------------------------------"

FAILED=0

if [ "$RESOLVED" != "$EXPECTED_ACTIVITY" ]; then
  echo "::error title=Manifest::launcher resolves to '$RESOLVED', expected '$EXPECTED_ACTIVITY'"
  FAILED=1
fi

if grep -qE "FATAL EXCEPTION" "$LOG"; then
  echo "::error title=Crash::FATAL EXCEPTION in logcat"
  grep -n -A 45 "FATAL EXCEPTION" "$LOG" | head -n 140
  FAILED=1
fi

if grep -q "app\.apertus" "$CRASH_LOG"; then
  echo "::error title=Crash::app.apertus appears in the Android crash buffer"
  grep -n -A 45 "app\.apertus" "$CRASH_LOG" | head -n 140
  FAILED=1
fi

if grep -qE "ClassNotFoundException|NoClassDefFoundError|Unable to instantiate activity|had failed to initialize" "$LOG"; then
  echo "::error title=Startup::startup class / dispatcher failure in logcat"
  grep -nE "ClassNotFoundException|NoClassDefFoundError|Unable to instantiate activity|had failed to initialize" "$LOG" | head -n 20
  FAILED=1
fi

if [ -z "$PID" ]; then
  echo "::error title=Process::$PACKAGE is not running 20s after launch"
  FAILED=1
fi

if [ "$FAILED" -eq 0 ]; then
  echo "SMOKE TEST PASSED: $PACKAGE resolved to $RESOLVED, launched, and is still running (pid $PID)."
fi

exit "$FAILED"
