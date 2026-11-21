# tools:eval report — synthetic hypotheses (NOT measured on real audio)
expected words: 19; preset: CONSERVATIVE; model space: uthmani-raw
clip syn_correct_001: correct=19 close=0 wrong=0 missed=0 hinted=0 extra=7 | صحيحة: 19 من 19
clip syn_skip_002: correct=18 close=0 wrong=0 missed=1 hinted=0 extra=6 | صحيحة: 18 من 19
clip syn_wrong_003: correct=18 close=0 wrong=1 missed=0 hinted=0 extra=7 | صحيحة: 18 من 19
TOTAL: correct=55 close=0 wrong=1 missed=1 hinted=0

## v2.0 metrics (SIMULATION — not measured on real audio)
clip syn_correct_001 hit-rate: 100.0%
clip syn_skip_002 hit-rate: 94.7%
clip syn_wrong_003 hit-rate: 94.7%
TRACKING HIT-RATE (simulated): 96.5% (55/57)
WORD-HIGHLIGHT LATENCY (simulated): p50=750ms p95=4700ms [SLO 1500ms] — SIMULATION of the observation stream only; on-device latency (§13.3) is NOT measured here

## Interpretation (V-025 regenerated; honest)
- Regenerated with the app-shaped stream (SegmenterSimulator law: 2500ms trailing partials @
  1000ms cadence + pause-final + 300ms VAD pad) — same law as core:align simulator tests.
- correct=19/19 (was 16/19); MISSED fires for the skipped يلد (was never); WRONG fires for the
  persistent كين substitution (was never). The old report's failures were: (a) generator
  artifact (whole-clip final) AND (b) a real aligner defect (finals aligned against
  pointer−backWords only) — fixed (V-025, lo=0 for finals).
- TRACKING HIT-RATE (simulated): 96.5% — % of correctly-recited words reaching CORRECT/CLOSE.
- WORD-HIGHLIGHT LATENCY (simulated): p50=750ms, p95=4700ms. p50 is partial-driven (word end →
  next partial windowEnd); p95 is dominated by words that only leave PENDING at the segment
  FINAL. This is a SIMULATION of the observation stream (windowEnd − word end); the on-device
  p95 (§13.3, SLO 1500ms) includes decode time and UI rendering and is NOT measured here.
- extras=6..7 per clip: the leading بسم..الرحيم words of overlapping partials counted once each
  per (text,250ms-bucket) — the partial windows re-cover the same words at new start times →
  new buckets → repeated EXTRA stats entries. EXTRA is stats-only; harmless but worth tuning the
  dedup bucket to (text, windowEnd) in Phase 2.
