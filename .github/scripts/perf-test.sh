#!/usr/bin/env bash
#
# On-device performance gate.
#
# This runs against the real APK on the real (emulated) runtime, so it measures
# what the user actually feels rather than what a JVM microbenchmark can see:
#
#   1. cold start  - `am start -W` TotalTime after the process is killed
#   2. warm start  - relaunch while the process is already resident
#   3. memory      - total PSS of the running process
#   4. liveness    - the app is still the resumed activity and no ANR was raised
#
# The emulator renders with swiftshader on shared CI hardware, which is far
# slower than a phone, so the budgets below are set for that floor. A green run
# here means the app is genuinely light rather than that the runner is fast.
#
# Every budget can be tightened or relaxed through the environment, e.g.
#   COLD_START_BUDGET_MS=6000 bash .github/scripts/perf-test.sh

set -uo pipefail

PACKAGE="${PACKAGE:-com.apertus.music}"
ACTIVITY="${ACTIVITY:-com.apertus.music.MainActivity}"
COMPONENT="$PACKAGE/$ACTIVITY"
OUT_DIR="${OUT_DIR:-perf}"

# Budgets. Milliseconds for the timings, kilobytes for memory.
COLD_START_BUDGET_MS="${COLD_START_BUDGET_MS:-9000}"
WARM_START_BUDGET_MS="${WARM_START_BUDGET_MS:-3000}"
MAX_TOTAL_PSS_KB="${MAX_TOTAL_PSS_KB:-614400}"

# Samples per timing; the best one is kept, which removes scheduler noise from
# the measurement instead of averaging it in.
REPEATS="${REPEATS:-3}"

FAILED=0
ok()  { echo "[ok] $*"; }
bad() { echo "[FAIL] $*"; FAILED=1; }
sec() { echo; echo "== $*"; }

mkdir -p "$OUT_DIR"

# `am start -W` prints "TotalTime: 1234"; take the field, not the label.
total_time_of() {
    adb shell am start -W -n "$COMPONENT" 2>/dev/null \
        | tr -d '\r' \
        | awk -F': *' '/^TotalTime:/ { print $2; exit }'
}

cold_start_sample() {
    adb shell am force-stop "$PACKAGE" >/dev/null 2>&1
    sleep 1
    total_time_of
}

warm_start_sample() {
    adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
    sleep 1
    total_time_of
}

best_of() {
    local sample_fn="$1" best="" value
    for _ in $(seq 1 "$REPEATS"); do
        value="$("$sample_fn")"
        if [ -n "$value" ] && { [ -z "$best" ] || [ "$value" -lt "$best" ]; }; then
            best="$value"
        fi
    done
    printf '%s' "$best"
}

sec "0. Preconditions"
if ! adb shell pm path "$PACKAGE" >/dev/null 2>&1; then
    bad "$PACKAGE is not installed"
    exit 1
fi
ok "installed at $(adb shell pm path "$PACKAGE" | tr -d '\r')"

# Measure the app the way a user actually sees it, with platform animations on.
# The smoke test switches them off for determinism; a performance gate must not,
# or it would be measuring a faster experience than anybody gets.
for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb shell settings put global "$scale" 1.0 >/dev/null 2>&1 || true
done
ok "platform animation scales restored to 1.0 for the measurement"

# One throwaway launch so the first sample is not paying for ART warm-up.
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1
total_time_of >/dev/null
sleep 2

# Drop everything the smoke test and the earlier steps logged, so the crash and
# ANR checks below judge this run only instead of inheriting another step's noise.
adb logcat -b all -c >/dev/null 2>&1 || adb logcat -c >/dev/null 2>&1 || true
ok "log buffers cleared; only this run's events are judged"

sec "1. Cold start (process killed, $REPEATS samples, best kept)"
COLD_MS="$(best_of cold_start_sample)"
echo "cold start: ${COLD_MS:-<no sample>} ms  (budget ${COLD_START_BUDGET_MS} ms)"
if [ -n "$COLD_MS" ] && [ "$COLD_MS" -le "$COLD_START_BUDGET_MS" ]; then
    ok "cold start is within budget"
else
    bad "cold start ${COLD_MS:-<no sample>} ms exceeds the ${COLD_START_BUDGET_MS} ms budget"
fi

sec "2. Warm start (process resident, $REPEATS samples, best kept)"
WARM_MS="$(best_of warm_start_sample)"
echo "warm start: ${WARM_MS:-<no sample>} ms  (budget ${WARM_START_BUDGET_MS} ms)"
if [ -n "$WARM_MS" ] && [ "$WARM_MS" -le "$WARM_START_BUDGET_MS" ]; then
    ok "warm start is within budget"
else
    bad "warm start ${WARM_MS:-<no sample>} ms exceeds the ${WARM_START_BUDGET_MS} ms budget"
fi

sec "3. Resident memory"
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1
total_time_of >/dev/null
sleep 5
MEMINFO="$(adb shell dumpsys meminfo "$PACKAGE" | tr -d '\r')"
printf '%s\n' "$MEMINFO" > "$OUT_DIR/meminfo.txt"
PSS_KB="$(printf '%s\n' "$MEMINFO" \
    | awk '/TOTAL PSS/ { for (i = 1; i <= NF; i++) if ($i ~ /^PSS:/) { v = $(i + 1); gsub(/,/, "", v); print v; exit } }')"
echo "total PSS: ${PSS_KB:-<not reported>} kB  (budget ${MAX_TOTAL_PSS_KB} kB)"
if [ -n "$PSS_KB" ] && [ "$PSS_KB" -le "$MAX_TOTAL_PSS_KB" ]; then
    ok "resident memory is within budget"
else
    bad "total PSS ${PSS_KB:-<not reported>} kB exceeds the ${MAX_TOTAL_PSS_KB} kB budget"
fi

sec "4. Liveness"
RESUMED="$(adb shell dumpsys activity activities | tr -d '\r' \
    | awk '/topResumedActivity|ResumedActivity/ { print; exit }')"
echo "resumed: ${RESUMED:-<none>}"
case "$RESUMED" in
    *"$PACKAGE"*) ok "the app is still the resumed activity" ;;
    *) bad "the app is not the resumed activity after the launch loops" ;;
esac

APP_PID="$(adb shell pidof -s "$PACKAGE" 2>/dev/null | tr -d '\r')"
echo "app pid: ${APP_PID:-<none>}"

# Two dumps, because the evidence lives in different places: the ActivityManager
# writes "ANR in <package>" under its own pid, while the app's crash trace is
# written under the app's pid. Grepping the whole buffer for a bare
# "Application Not Responding" would also match an unrelated system process and
# fail the gate for something the app did not do.
adb logcat -d -v brief > "$OUT_DIR/logcat-full.txt" 2>/dev/null || true
if [ -n "$APP_PID" ]; then
    adb logcat -d -v brief --pid="$APP_PID" > "$OUT_DIR/logcat.txt" 2>/dev/null || true
else
    cp "$OUT_DIR/logcat-full.txt" "$OUT_DIR/logcat.txt" 2>/dev/null || true
fi

if grep -qiE "ANR in ${PACKAGE}|Application Not Responding: ${PACKAGE}" "$OUT_DIR/logcat-full.txt"; then
    bad "$PACKAGE was reported as not responding"
else
    ok "no ANR recorded for $PACKAGE"
fi
if grep -qF "FATAL EXCEPTION" "$OUT_DIR/logcat.txt"; then
    bad "a FATAL EXCEPTION appeared in $PACKAGE during the run"
else
    ok "no fatal exception in $PACKAGE during the run"
fi

sec "5. Frame timing (informational, not gated)"
adb shell dumpsys gfxinfo "$PACKAGE" | tr -d '\r' > "$OUT_DIR/gfxinfo.txt" 2>/dev/null || true
grep -E "Total frames rendered|Janky frames|90th percentile|95th percentile|99th percentile|HISTOGRAM" \
    "$OUT_DIR/gfxinfo.txt" || echo "(no frame stats reported)"

sec "Result"
{
    echo "package=$PACKAGE"
    echo "activity=$ACTIVITY"
    echo "cold_start_ms=${COLD_MS:-}"
    echo "cold_start_budget_ms=$COLD_START_BUDGET_MS"
    echo "warm_start_ms=${WARM_MS:-}"
    echo "warm_start_budget_ms=$WARM_START_BUDGET_MS"
    echo "total_pss_kb=${PSS_KB:-}"
    echo "max_total_pss_kb=$MAX_TOTAL_PSS_KB"
    echo "result=$([ "$FAILED" -eq 0 ] && echo PASS || echo FAIL)"
} | tee "$OUT_DIR/summary.txt"

echo
if [ "$FAILED" -eq 0 ]; then
    echo "PERFORMANCE GATE PASSED: cold ${COLD_MS} ms, warm ${WARM_MS} ms, PSS ${PSS_KB} kB."
else
    echo "PERFORMANCE GATE FAILED." >&2
fi

exit "$FAILED"
