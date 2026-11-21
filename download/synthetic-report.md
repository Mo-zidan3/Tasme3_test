# tools:eval report — synthetic hypotheses (NOT measured on real audio)
expected words: 19; preset: CONSERVATIVE; model space: uthmani-raw
clip syn_correct_001: correct=16 close=0 wrong=0 missed=0 hinted=0 extra=3 | صحيحة: 16 من 19
clip syn_skip_002: correct=15 close=1 wrong=0 missed=0 hinted=0 extra=3 | صحيحة: 15 من 19
clip syn_wrong_003: correct=1 close=1 wrong=0 missed=0 hinted=0 extra=0 | صحيحة: 1 من 19
TOTAL: correct=32 close=2 wrong=0 missed=0 hinted=0

## Interpretation (honest, §0.5)
- All numbers are from SYNTHETIC hypotheses — NOT measured on real audio.
- syn_correct_001: 16/19 CORRECT; the DP's semi-global trailing-free end classified the last 3 words
  as EXTRA (recorded in stats only). This exposes a harness-realism gap: whole-clip finals vs the
  app's overlapping partial+final windows. Not a product bug; revisit with real run_asr.py clips.
- syn_skip_002: MISSED did not fire — absence confirmation needs ≥2 independent observations of the
  skip (§9.3); a single final clip gives one. Real partial streams confirm skips repeatedly.
- syn_wrong_003: WRONG did not fire — synthetic windows (800 ms) are shorter than 2×boundaryMs (600 ms),
  so the substituted word is never interior. Real partial windows (partialWindowMs=2500) leave interior
  room. Fix the generator later; the tracker's WRONG path is covered by unit tests on realistic windows.
