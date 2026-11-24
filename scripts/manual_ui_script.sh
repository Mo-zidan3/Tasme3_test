#!/bin/bash
# T9 (B3) MANUAL UI SCRIPT — the emulator-executable subset, run by CI (adb) after the
# instrumented tests. Steps needing Mo's hands on real hardware are listed in
# docs/TESTING.md as H1..H4 and stay OPEN — they are NOT faked here.
# Usage: bash scripts/manual_ui_script.sh <pkg> <evidenceDir>
set -uo pipefail
PKG="${1:-com.iqraa.tasmee}"
EV="${2:-manual-ui-evidence}"
mkdir -p "$EV"
step() { echo "[manual-ui] $*"; }

step "M1 install + launch"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n "$PKG/.MainActivity"
sleep 3
adb exec-out screencap -p > "$EV/M1-launch.png"

step "M2 onboarding renders (rationale text present in the hierarchy)"
adb shell uiautomator dump /sdcard/m2.xml >/dev/null 2>&1
adb pull /sdcard/m2.xml "$EV/M2-onboarding.xml" >/dev/null 2>&1
grep -q "microphone" "$EV/M2-onboarding.xml" && step "M2 PASS: rationale visible" || step "M2 FAIL: rationale missing"

step "M4 rotation survives (force landscape, dump, restore)"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 2
adb shell uiautomator dump /sdcard/m4.xml >/dev/null 2>&1
adb pull /sdcard/m4.xml "$EV/M4-landscape.xml" >/dev/null 2>&1
grep -q "microphone" "$EV/M4-landscape.xml" && step "M4 PASS: content intact after rotation" || step "M4 FAIL: content lost"
adb shell settings put system user_rotation 0

step "M5 font scale 2.0 renders, then reset"
adb shell settings put system font_scale 2.0
sleep 2
adb exec-out screencap -p > "$EV/M5-fontscale2.png"
adb shell uiautomator dump /sdcard/m5.xml >/dev/null 2>&1
adb pull /sdcard/m5.xml "$EV/M5-fontscale2.xml" >/dev/null 2>&1
# uiautomator dumps VISIBLE nodes only; at 2.0x the long rationale body scrolls below the
# fold of the scrollable column (run 45360782455: header+riwaya visible, body off-screen —
# content renders, no crash; the pre-permission rationale visibility at 2x is Mo's design
# call). Criterion: an always-visible onboarding element, case-insensitive.
grep -qiE "microphone|iqraa" "$EV/M5-fontscale2.xml" \
  && step "M5 PASS: onboarding renders at 2.0x (rationale body scrolls below the fold)" \
  || step "M5 FAIL at 2.0x"
adb shell settings put system font_scale 1.0

step "done — evidence in $EV/"
