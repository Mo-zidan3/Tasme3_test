#!/bin/bash
# T11: replay the committed T11 hypotheses through the REAL tracker (tools:eval) and
# aggregate whole-clip vs app-shaped per-word state counts. Runs IN CI (Gradle is
# sandbox-blocked locally, §0). Output → job log + reports artifact.
set -uo pipefail
cd "$(dirname "$0")/.."
REPORT=build/t11-replay-report.txt
mkdir -p build
{
  echo "# T11 replay — app-shaped vs whole-clip through the REAL RecitationTracker"
  echo "# LABEL: INDICATIVE — studio clips (V-038 provenance); §13 phone gate NOT met"
  echo
} > "$REPORT"
FAILURES=0
for exp in eval/expected/t11/*.json; do
  id=$(basename "$exp" .json)
  for mode in whole app; do
    hyp="eval/hypotheses/t11/${id}-${mode}.json"
    [ -f "$hyp" ] || { echo "MISSING hypothesis $hyp" >> "$REPORT"; FAILURES=$((FAILURES+1)); continue; }
    echo "=== $id [$mode]" >> "$REPORT"
    # :tools:eval:run's working directory is the MODULE dir (tools/eval), not the repo
    # root — ABSOLUTE paths required (runs 45334586098/45339239201: FileNotFoundException).
    ./gradlew -q :tools:eval:run --args="$PWD/$hyp $PWD/$exp" >> "$REPORT" 2>&1 || {
      echo "REPLAY FAILED for $id [$mode]" >> "$REPORT"
      FAILURES=$((FAILURES+1))
    }
  done
done
echo "---- aggregate ----" >> "$REPORT"
grep -E "clip .*: correct=|TOTAL:|TRACKING HIT-RATE" "$REPORT" || true
cat "$REPORT"
# V-044: replay failures are FATAL — a green job over dead replays is not evidence
# (run 45339239201's build went green while all 12 replays failed silently).
if [ "$FAILURES" -ne 0 ]; then
  echo "T11 replay: $FAILURES replay(s) FAILED — failing the step"
  exit 1
fi
