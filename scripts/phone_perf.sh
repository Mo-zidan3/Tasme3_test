#!/usr/bin/env bash
# H4 / Q7: REAL-HARDWARE perf probe for Mo's phone (docs/PHONE_TEST.md).
#
# Measures, on the connected device, with the releaseSmoke build (R8 ON, debug-signed):
#   1. cold start        — `am start -W` TotalTime, 3 runs (run 1 = true cold after force-stop)
#   2. instrumented      — NativeSmokeInstrumentedTest: model load, decode RTF, peak RSS
#                          (the same fixture CI uses; the emulator numbers were INDICATIVE)
#   3. R8 decode proof   — ReleaseSmokeInstrumentedTest: "decoded N words (R8 build)"
#   4. live 60 s session — peak RSS via dumpsys meminfo + battery drain via batterystats
#
# Writes eval/reports/phone-<device>.md — REAL HARDWARE numbers; Q7 closes only when that
# file exists and is pasted back. Run from the repo root, phone connected via USB (or
# wireless adb), the app installed (docs/PHONE_TEST.md steps 1-4).
set -uo pipefail

PKG="com.iqraa.tasmee"
ACTIVITY="${PKG}/.MainActivity"
OUTDIR="eval/reports"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p "$OUTDIR"

say() { printf '%s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || fail "adb not found — install platform-tools (https://developer.android.com/tools/releases/platform-tools)"
DEVICE="$(adb get-serialno 2>/dev/null)"
[ -n "$DEVICE" ] && [ "$DEVICE" != "unknown" ] || fail "no device connected — enable USB debugging and accept the prompt on the phone"

MODEL="$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')" || MODEL="device"
ANDROID="$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')" || ANDROID="?"
SAFE_MODEL="$(printf '%s' "$MODEL" | tr -c 'A-Za-z0-9_.-' '_' | sed 's/_*$//;s/^_*//')"
[ -n "$SAFE_MODEL" ] || SAFE_MODEL="device"
REPORT="$OUTDIR/phone-${SAFE_MODEL}.md"

say "device: ${MODEL} (Android ${ANDROID}, serial ${DEVICE})"
say "report: ${REPORT}"

# The instrumented probes need BOTH the app APK and its androidTest APK. If Mo downloaded
# the CI phone-kit artifact, this script installs them; passing the paths is optional.
if [ -f phone-kit/app-release-smoke.apk ]; then
  say "installing phone-kit/app-release-smoke.apk + androidTest APK …"
  adb install -r phone-kit/app-release-smoke.apk >/dev/null || fail "adb install of the app APK failed"
  adb install -r phone-kit/app-release-smoke-androidTest.apk >/dev/null || fail "adb install of the androidTest APK failed"
fi

INSTR="$(adb shell pm list instrumentation 2>/dev/null | tr -d '\r' | grep "target=${PKG}" | head -1 | sed 's/^instrumentation://;s/ (target=.*)$//')"
[ -n "$INSTR" ] || fail "instrumentation for ${PKG} not found — install the androidTest APK from the CI phone-kit artifact"
say "instrumentation: ${INSTR}"

{
  echo "# H4 / Q7 — REAL HARDWARE perf report"
  echo ""
  echo "- device: ${MODEL} (Android ${ANDROID}, serial ${DEVICE})"
  echo "- build: releaseSmoke (R8/minify ON, debug-signed) — [$(date -u '+%Y-%m-%d %H:%M UTC')]"
  echo "- label: REAL HARDWARE (not emulator INDICATIVE)"
  echo ""

  echo "## 1. Cold start (am start -W, 3 runs — run 1 is after force-stop)"
  for i in 1 2 3; do
    adb shell am force-stop "$PKG" >/dev/null 2>&1
    sleep 2
    LINE="$(adb shell am start -W -n "$ACTIVITY" 2>/dev/null | tr -d '\r' | grep -E 'TotalTime|WaitTime' | tr '\n' ' ')"
    echo "- run ${i}: ${LINE:-NO-RESULT}"
  done
  adb shell am force-stop "$PKG" >/dev/null 2>&1
  echo ""

  say "running instrumented probes (a few minutes — the model loads twice) …"
  echo "## 2. Instrumented probes (REAL HARDWARE)"
  adb logcat -c 2>/dev/null
  if adb shell am instrument -w -e class com.iqraa.app.NativeSmokeInstrumentedTest "$INSTR" >/dev/null 2>&1; then
    PERF="$(adb logcat -d -s IqraaPerfSmoke 2>/dev/null | tr -d '\r' | grep 'PERF-SMOKE' | tail -1)"
    echo "- \`${PERF:-NO-RESULT}\`"
  else
    echo "- NativeSmokeInstrumentedTest FAILED — see the full logcat; keep this file"
  fi
  adb logcat -c 2>/dev/null
  if adb shell am instrument -w -e class com.iqraa.app.ReleaseSmokeInstrumentedTest "$INSTR" >/dev/null 2>&1; then
    SMOKE="$(adb logcat -d -s IqraaReleaseSmoke 2>/dev/null | tr -d '\r' | grep 'decoded' | tail -1)"
    echo "- \`${SMOKE:-NO-RESULT}\` — the R8 build decodes on REAL hardware (§8.2)"
  else
    echo "- ReleaseSmokeInstrumentedTest FAILED — keep this file for debugging"
  fi
  echo ""

  echo "## 3. Live 60-second recitation session"
  echo ""
  say ""
  say "================================================================"
  say "  On the phone: open the app, start a recitation, and RECITE"
  say "  normally for ~60 seconds. The script samples RSS meanwhile."
  say "================================================================"
  adb shell am start -W -n "$ACTIVITY" >/dev/null 2>&1
  echo "Press ENTER when you are READY to start reciting (60 s):"
  read -r _
  BATT_BEFORE="$(adb shell dumpsys battery 2>/dev/null | tr -d '\r' | grep -E 'level' | head -1)"
  adb shell dumpsys batterystats --reset >/dev/null 2>&1
  PEAK=0
  for t in $(seq 1 30); do
    PSS="$(adb shell dumpsys meminfo "$PKG" 2>/dev/null | tr -d '\r' | grep -m1 'TOTAL PSS' | grep -oE '[0-9]+' | head -1 || true)"
    [ -n "$PSS" ] && [ "$PSS" -gt "$PEAK" ] 2>/dev/null && PEAK="$PSS"
    sleep 2
  done
  BATT_AFTER="$(adb shell dumpsys battery 2>/dev/null | tr -d '\r' | grep -E 'level' | head -1)"
  BSTATS="$(adb shell dumpsys batterystats --charged "$PKG" 2>/dev/null | tr -d '\r' | grep -m1 -E 'Discharge step durations|Estimated power use' || true)"
  echo "- peak RSS (TOTAL PSS): $([ "$PEAK" -gt 0 ] 2>/dev/null && echo "${PEAK} KB" || echo 'NO-SAMPLE')"
  echo "- battery before: ${BATT_BEFORE:-NO-RESULT} / after: ${BATT_AFTER:-NO-RESULT}"
  echo "- batterystats: ${BSTATS:-below-resolution (60 s is short; a level delta may be 0)}"
  echo ""

  echo "## Q7 verdict"
  echo "- Q7 closes when this file is pasted back (REAL HARDWARE numbers recorded above)."
  echo "- Compare RTF above against the §8.5 ladder: RTF ≤ 0.5 → L0 (كامل) is expected on any modern phone."
} | tee "$REPORT"

say ""
say "done — report written to ${REPORT}"
say "paste/commit that file back to close Q7 (docs/PHONE_TEST.md H4)."
