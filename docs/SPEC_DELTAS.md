# SPEC_DELTAS — v2.0 (normative) vs v1.0

The repo's spec file is **v1.0**. v2.0 **supersedes** v1.0; the deltas below are normative and
were applied starting Phase 0.6/1 (see DECISIONS D-011, VERIFICATION V-015…V-023). Where a
v1.0 §-reference and a delta disagree, the delta wins; where they agree, the v1.0 text stands.

| # | Topic | v2.0 delta (normative) | Where implemented / evidenced |
|---|---|---|---|
| 1 | Comparison space | **Model-native raw Uthmani** everywhere in app + `tools:eval`. `spelling.json` used ONLY in `eval/run_asr.py --standard` for standard-spelling references. Never at app runtime. | D-011 (N() from retrieve.py); EvalMain header "model space: uthmani-raw"; run_asr.py `--standard` flag |
| 2 | Observation identity | `(windowStartMs, windowEndMs, isFinal, modelRev)`; **INDEPENDENT** iff audio overlap <50% of the shorter window OR (one isFinal and windows differ). Re-observing an identical window improves evidence only, never adds a vote. | `Tracker.kt` `identity()/independent()`; TrackerFullTest idempotency tests (V-021) |
| 3 | State precedence | HINTED (terminal) > CORRECT (any interior sim≥0.90, never downgraded) > MISSED (skipped + pointer≥3 beyond + ≥2 independent absence) > WRONG (preset rule) > CLOSE > PENDING; EXTRA stats-only. | `RecitationTrackerImpl.recomputeStates()`; 31 tests (V-021) |
| 4 | Presets | **Confidence-free**: Conservative = ≥2 independent interior; Balanced = single FINAL interior. `confidence` is always null with sherpa-onnx — no code path may depend on it. | `TrackerConfig.Preset`; `isWrongConfirmed()`; AsrWord.confidence nullable, unused |
| 5 | Scheduling | `AsrConfig.partialWindowMs = 2500` (separate from `maxSegmentMs = 8000`). Law: effective partial cadence = `max(partialEveryMs, RTF×W)`; inference non-interruptible; queue depth 1; drop stale results by windowEndMs. | `core:audio` Scheduler (Phase 2, host-side tests) |
| 6 | Timing contract | Session-global ms; words assembled between `▁` tokens; `endMs = lastTokenTs + frameShiftMs`; frame shift **40 ms** (verified V-015); null-timing fallback = token-index interiority (`kEdgeTokens = 2`). | `FRAME_SHIFT_MS = 40`; EvalMain/`run_asr.py` assembly; null-timing tests |
| 7 | Gates | §13.4 applies **per model AND per preset**; tiny active → «الوضع الخفيف» badge; degraded ladder L0–L3. | STATUS Needs-from-Mo; core:session ladder (Phase 2) |
| 8 | Tasmee' summary | «صحيحة: X من Y», **X = CORRECT only**, Y = expected − HINTED (− optional-prefix basmala, §7.6/V-023); CLOSE reported separately. | `TasmeeSummary`; summary tests |
| 9 | Lost timer / EXTRA | Lost timer advances **only while VAD=speech**; EXTRA dedup key `(text, 250ms bucket)`. | `advanceLostTimer()`; EXTRA dedup tests |
| 10 | Budgets | p95 word-highlight latency ≤1.5 s; AAB download ≤80 MB target; arm64-v8a only. | F2/§14; **AAB MEASURED 55,900,663 B ≈ 55.9 MB universal (min=max, bundletool 1.17.0, runs 45061168092/45067719380) — budget MET, estimate retired; measurement merged into the release job (V-037)** |

Extra facts confirmed during Phase 0.6–1 (kept normative):
- Native floor: onnxruntime `.note.android.ident` min API **27** (V-016) → minSdk 27 **APPLIED (D-012/D-015), RATIFIED by Mo, and runtime-PROVEN on-device (loadLibrarySmoke PASS on API 27 + 35, runs 45067719380 etc.)**.
- Tanzil Uthmani (txt-2) basmala placement: 112 surahs prefixed (110 plain + 95/97 with `بِّسْمِ`
  shadda-first bytes), Fatihah 1:1 IS the basmala, At-Tawbah none (V-023).
