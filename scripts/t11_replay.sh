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
for exp in eval/expected/t11/*.json; do
  id=$(basename "$exp" .json)
  for mode in whole app; do
    hyp="eval/hypotheses/t11/${id}-${mode}.json"
    [ -f "$hyp" ] || continue
    echo "=== $id [$mode]" >> "$REPORT"
    ./gradlew -q :tools:eval:run --args="$hyp $exp" >> "$REPORT" 2>&1 || \
      echo "REPLAY FAILED for $id [$mode]" >> "$REPORT"
  done
done
echo "---- aggregate ----" >> "$REPORT"
grep -E "correct=|CORRECT|correct" "$REPORT" | tail -40 || true
cat "$REPORT"
