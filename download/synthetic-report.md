# tools:eval report — synthetic hypotheses (NOT measured on real audio)
expected words: 19; preset: CONSERVATIVE; model space: uthmani-raw
clip syn_correct_001: correct=19 close=0 wrong=0 missed=0 hinted=0 extra=0 | صحيحة: 19 من 19
clip syn_skip_002: correct=18 close=0 wrong=0 missed=1 hinted=0 extra=0 | صحيحة: 18 من 19
clip syn_wrong_003: correct=18 close=0 wrong=1 missed=0 hinted=0 extra=0 | صحيحة: 18 من 19
TOTAL: correct=55 close=0 wrong=1 missed=1 hinted=0

## v2.0 metrics (SIMULATION — not measured on real audio)
clip syn_correct_001 hit-rate: 100.0%
clip syn_skip_002 hit-rate: 94.7%
clip syn_wrong_003 hit-rate: 94.7%
TRACKING HIT-RATE (simulated): 96.5% (55/57)
WORD-HIGHLIGHT LATENCY (simulated): p50=550ms p95=950ms [SLO 1500ms] — SIMULATION of the observation stream only; on-device latency (§13.3) is NOT measured here
