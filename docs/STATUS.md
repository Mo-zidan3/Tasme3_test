# STATUS — per phase

Last updated: 2026-11-23 (V-041/PR-1 in flight: Mo's three verified aligner defects fixed —
refrain anchoring + forward tie-break, per-word SET independence, .env hygiene; NO threshold
or default changed (D-020 documents two deviations where the prompt's literal rule could not
keep the frozen V-025/V-031 tests green); all new results SYNTHETIC (simulator), the §13
phone-mic gate (≥60 clips/≥3 speakers/phone mic/FA≤5%) remains NOT met, WRONG still renders
CLOSE). V-040 history follows:) T1-T5 CLOSED — identity com.iqraa.tasmee on green CI;
docs swept (T2); detekt ON MAIN via PR with ratchet ceiling = the measured 200 findings
(T3, D-017 closed — Mo ratchets from here); Mo's clips committed as authorization-of-record
+ ONE 10.05 s 54:10 CI fixture + PROVENANCE (T4: accuracy test now PASSES on-device both
floors; harness INDICATIVE — studio clips, phone gate NOT met); T5 perf smoke INDICATIVE
(API27 load 311–375 ms, RTF 0.021–0.029; peakRSS honestly not-yet-measured). Phase 3 first
slice LANDED + CI-green (V-040, run 45080772312): core:data Room v1 + DataStore keys,
designsystem §10.2 encoding, SessionLifecycle §10.4 + tests, ar/en strings, screens F1-F6,
MainActivity+NavHost. Arabic strings UNREVIEWED until Mo signs off.
V-037c history follows:) run 45067719380 — ALL FIVE JOBS GREEN with S2 CLOSED ON BOTH
API FLOORS: timing decode PASS on API 27 (0.597s) AND API 35 (0.598s) via androidTest-APK
assets (scoped storage hides shell-pushed files on Android 15); loadLibrary PASS both floors
(minSdk=27 runtime proof); coverage gate REAL numbers (quran 93.1%, align 91.0% ≥85%); ktlint
0 out-of-baseline; AAB measured 55.9 MB ≤80 MB budget MET; all six notices canonical-SHA gates
OK; accuracy clip remains the loud §0 skip. Commit B (detekt) HELD: sandbox Maven 429 blocks
local ./gradlew help verification — D-017 open for Mo)

## Phase 0 — Scaffold: **DONE (E1 CLOSED)**
E1 is CLOSED (V-030/V-033/V-037): CI executes on GitHub Actions; `assembleRelease` with R8
succeeds in the release job (mapping.txt guard green); the local sandbox limits (no gh, no
emulator, R8 OOM at 4 GB) are bypassed by using CI as the verifier. Everything else verified green:
- 13-module Gradle scaffold per §6.1 (module count corrected this phase from "15" — V-018).
- `assembleDebug` GREEN (all modules; APK 87.75 MB debug, measured V-018); unit tests GREEN.
- Merged manifest: RECORD_AUDIO only — no INTERNET / ACCESS_NETWORK_STATE (V-011).
- CI workflow encodes: reproducible asset pipeline (fetch_native + generate_notices + fetch_models),
  debug+release builds, tests, **release-minify guard** (fails without R8 mapping.txt),
  merged-manifest offline check, license report. **EXECUTED green** (runs 45064204429,
  45066011428, 45067719380 — all five jobs).
- Docs skeleton per §17 complete.

## Phase 0.5 — Spike: **DONE (S2 CLOSED on-device, V-037c)**
S2's on-device half is CLOSED: the timing-decode instrumented test PASSES on API 27 (0.597 s)
and API 35 (0.598 s) in run 45067719380 — real sherpa JNI decode on-device, seconds-scale
timestamps, 40 ms frame grid. The accuracy-clip half remains §0-gated (Mo's clips land with
T4). Previously listed partial state:
Closed: S1 (models pinned+SHA-256), S2 **compile-time only** (kotlin-api vendored from tag v2.0.2,
arm64 jniLibs extracted, API surface = Q1-CLOSED), S3 (silero_vad pinned), S4 (Tanzil: 6,236/114
verified; license notice extracted byte-for-byte), S5 (Amiri Quran 1.003 + OFL; host cmap coverage
FULL — 0 missing codepoints, V-017).
Not closed on host: decode fixture on **API 26 emulator** (impossible in sandbox; no emulator)
→ moves to Phase 2 first on-device gate. Q8 timing CLOSED on host (V-015: seconds, 40 ms frame
shift — used for core:align constants).
S6 baseline GGML: **BLOCKED** — HF 429 rate-limits throughout (V-012); retried again this session
with exponential backoff, still blocked. Harness-only; not on the Phase 1 critical path.

## Phase 0.6 — Gate closure: **DONE (of what the sandbox permits) / T1 = BLOCKED**
- T2 ✅ Q8 closed on host (V-015). `FRAME_SHIFT_MS = 40` constant + tests.
- T3 ✅ V-016: both .so LOAD-align 0x4000 (16 KB-page safe); NEEDED ⇒ two .so sufficient;
  **effective native minSdk = 27 (onnxruntime) > declared 26** — ESCALATED to Mo (options:
  raise minSdk to 27; or keep 26 with documented load-failure risk on API 26).
- T4 ✅ full license texts in assets/notices + verbatim Tanzil block + model LICENSE copyright
  ("Copyright (c) 2026 Abdou") + official Fadheli citation (V-019).
- T5 ✅ fetch_native.py + jniLibs gitignored + module count reconciled to 13 (V-018).
- T6 ✅ size table measured from the APK artifact (V-018): debug 87.75 MB; AAB download
  **estimated 77–80 MB — at the ≤80 MB budget edge** (estimate only: no release/AAB build
  exists — E1). Escalation to Mo now: options per §14 (PAD / drop tiny / accept with ADR).
- T7 ✅ V-017: basmala = 111 surahs prefixed into ayah 1, Al-Fatihah 1:1 IS the basmala,
  At-Tawbah absent; font coverage complete (0/61 codepoints missing).
- T8 ✅ STATUS rewritten (this file) + toolchain-risk decision requested (below).
- T1 ❌ **BLOCKED in sandbox** (no gh/remote): Phase 0 keeps the DONE WITH EXCEPTION label;
  **"gates green" is NOT claimed for CI or minified release.**

- **CI RUN 45061168092 (V-037, 2026-11-22)**: instrumented API 27 + 35 GREEN — the V-035
  KVM repair WORKED (/dev/kvm present on ubuntu-24.04; emulator boots ~37 s; tests run).
  loadLibrary PASS on API 27 AND 35 (minSdk=27 native floor proven on-device — D-012
  ratification now has runtime evidence). AAB size MEASURED 55.9 MB universal (≤80 MB target
  — met with margin; the 77–80 MB estimate is retired). Notices canonical-SHA gate GREEN.
  Timing decode test SKIPPED — fixture-visibility defect (device cannot see the runner's
  repo checkout) — fixed in 15cf4ab (adb push to the app's external-files dir + repoFile
  probes it); run 45061936775 verifies. build RED at coverage gate: Gradle 9 jacoco produced
  no XML (xml.required default) — fixed in 15cf4ab; the gate correctly hard-failed instead of
  faking numbers. release gate step crashed on a CSV-parse bug AFTER the measurement — fixed
  in 15cf4ab; measurement recorded in V-037.

## Needs from Mo (§18 + Phase 0.6)
1. **Trigger CI**: push the repo to GitHub (Actions workflow is ready) — closes E1, S2 (emulator
   decode), the AAB size measurement, and the R8 keep-rules gate in ONE run (docs/CI.md).
2. **A 5–10 s clip you record yourself, with written permission to commit it as a test fixture**
   (Mo owns the rights → clean, permanent, CI-usable) — permanently enables the on-device decode
   test. No gated/third-party audio is ever committed.
2. **Toolchain risk decision [new]**: AGP 9.5.2 / Kotlin 2.4.21 / compileSdk 37.1 / KSP2 is
   bleeding-edge and already deviated once from the documented KGP-classpath path (D-001).
   Freeze explicitly, or approve a documented Plan B (AGP 8.x line / compileSdk 36) to be
   triggered on the next toolchain blocker.
3. **minSdk decision [new]**: native floor is 27 (V-016) vs declared 26. Recommend raise to 27.
4. **Size budget decision [new]**: AAB estimate 77–80 MB vs ≤80 MB target (V-018) — PAD, drop
   tiny (−15.6 MB), or accept with ADR.
5. Archive location for exact shipped model files + license texts (asked in Phase 0; still open).
6. Recordings per §13.1 (≥50 first-look; ≥60 held-out correct + ≥25 planted-error; ≥3 speakers)
   — if absent, Phase 2 ends indicative and WRONG ships as CLOSE (§13.4 default).
7. ~~Final applicationId (Play rejects com.example.*)~~ DONE (T1): com.iqraa.tasmee applied + CI-verified.
8. Test devices (≥1 low-end 3–4 GB + ≥1 flagship) → docs/DEVICES.md.
9. Ratification of `[MO]` items: presets §9.5, summary §9.6, Amiri default §7.7,
   tiny shipping policy §8.1, and the CC-BY-3.0 attribution interpretation (§7.1).
10. Arabic string review; store copy review; release keystore; code license (Apache-2.0 default).

## Phase 1.5 (hardening) + Phase 2 host-side — 2026-11-21 (this session)
- **CI RUN 45035800627 (V-035, 2026-11-22)**: secrets-scan/build/release GREEN (unit tests,
  lint, manifest-offline, classpath, ABI floor all pass; assembleRelease+bundleRelease with
  R8 mapping.txt; release APK arm64-v8a only; release-outputs uploaded). E1's
  "never-built-minified-release" exception is CLOSED. size job RED = artifact RACE (CI
  defect): job deleted, bundletool 1.17.0 (sha-pinned) merged into release after
  bundleRelease — measurement, ≤80 MB warn / >85 MB fail, report to step summary. AAB size
  still UNKNOWN. instrumented 27+35 RED = no /dev/kvm on ubuntu-26.04 ("Software CPU
  emulation is not supported") → ubuntu-24.04 + mandatory kvm pre-flight +
  disable-linux-hw-accel:false + -no-metrics + boot timeout 900 (NO TCG fallback without
  Mo's explicit acceptance). S2-on-device OPEN.
- **Quality gates REAL (V-035 P2)**: ktlint 1.8.0 CLI sha-pinned; parse errors hard-fail;
  violations ratchet vs baseline 817 (growth fails). detekt 1.23.8 wired at root for all
  subprojects (the old `|| echo "NOTE"` fake pass is gone; maxIssues ceiling 100000
  pending Mo's ratchet decision — D-017). JaCoCo XML parsed by scripts/ci_coverage_gate.py:
  hard fail <85% lines on core:quran/core:align, measured numbers printed.
- **License-asset regression repaired (V-035 P0b)**: Mo's 3b1da0a generate_notices rewrite
  produced degraded cold-runner texts (Tanzil 1004 B, OFL 4027 B, README overwritten). Fixed:
  fallbacks = git-tracked canonical assets; byte-exact verified (1106 B/4389 B, shas
  72493509…/72de68e5…); README only generated when absent.
- **Basmala BLOCKER closed (V-023)**: 112 surahs carry the prefix (110 plain + 95/97 with
  `بِّسْمِ` shadda-first bytes); Fatihah = ayah itself; Tawbah none. The earlier "111" was a
  byte-startswith artifact of the CHECK, not the text. OPTIONAL_PREFIX modeled in the derived
  table (never MISSED, excluded from Y, absorbed when recited) + core:align honors it. Tests
  on Fatihah/Baqarah/Tawbah/95/97.
- **SPEC_DELTAS.md committed**: every v2.0 §-reference resolves; deltas normative.
- **CI engineered for one push (docs/CI.md)**: build(+hygiene+lint+manifest+classpath), release
  (R8 guard + AAB), size (bundletool SDK/ABI measurement), instrumented (API 27+35 emulator,
  native smoke + loud-skip fixture policy). E1/S2/AAB/minSdk close on Mo's FIRST push.
- **N() collision analysis (V-024)**: 18.9% of forms collide (68.9% of instances — harakat-only
  mostly); 275 ayahs letter-identical elsewhere. Documented as inherent letters-only limitation
  (EVALUATION + user-facing ar/en disclosure). NOT "fixed" (correct per §0).
- **SegmenterSimulator + REAL aligner defect fixed (V-025)**: finals aligned lo=0; synthetic
  report regenerated: 19/19 CORRECT, MISSED fires, WRONG fires, hit-rate 96.5%, simulated
  latency p50=750/p95=4700 labeled SIMULATION. 35 align tests.
- **Phase 2 host-side (P3) — implemented, NOT claimed done**: core:audio Scheduler (law
  max(partialEveryMs, RTF×W), queue depth 1, final-never-starves, stale-drop; 13 tests with
  fake RTFs), PcmRingBuffer, Segmenter (pause-finals, force-cut + overlap); core:asr decode +
  word assembly (▁ separators, seconds×1000 + 40 ms, null-timing fallback; 5 tests); core:session
  orchestration with fake recognizer + §11 checkpoints + calibration (3 probes, discard first,
  median → RTF → L0–L3 with MEASURED triggers + model choice + badge from L2; 8 tests).
  88 unit tests green across 5 modules. The Phase 2 gate (fixture decode on device, RTF/RAM/
  battery/latency) stays OPEN until the CI emulator job or Mo's devices run.
- **§7.5 N() self-validation CLOSED on clean audio (V-026)**: 6/6 studio clips CER 0.00%
  (primary model, model space) — with the optional basmala correctly excluded. Indicative (one
  reciter, studio); the §13 phone gate is separate.
- **S6 baseline STILL BLOCKED** (429/404); run_baseline.py stub refuses to emit numbers (V-027).
- **Pushed to GitHub (V-030)**: repo at https://github.com/Mo-zidan3/Tasme3_test, main branch,
  leak gates clean before/after. The workflow triggered on push, but the fine-grained token lacks
  the Actions permission — run monitoring is BLOCKED (403 on /actions/runs, check-runs,
  workflow_dispatch; public HTML 404). NO gate marked green from the workflow file alone; Mo must
  paste the run logs/URLs to close E1/AAB/S2-on-device.
- **Pre-push hardening (V-028/9, D-013–D-016)**: release=arm64-v8a ONLY / debug=arm64+x86_64
  (unzip-proven on built APKs); minSdk=27 APPLIED (floor assertions in CI over every shipped .so);
  deterministic code-generated timing fixture + on-device timing/accuracy decode tests; CI
  hardening (independent jobs, pinned SDK components, bundletool 1.17.0 pin, SHA-keyed caches
  with backoff + hard-fail, path filters, concurrency cancel, 7-day retention, secrets-scan +
  notices-placeholder checks); THIRD_PARTY_NOTICES.md Tanzil block byte-verbatim; JaCoCo
  re-measured (93.1%/91.0%).
- **§4 host-side fixes (V-031)**: simulated latency p95 4700→950 ms (metric dup-text bug +
  cross-segment partial windows); EXTRA 7→0 (matched-position suppression + (text,startMs/40)
  dedup); repeated-ayah invariants on the surah-55 refrain (×31) + segment-initial MISSED
  defect fixed (leading-skip absence); strictOverlapIndependence flag (default OFF) with
  WRONG-fire numbers (1 default vs 0 strict) for Mo before the §13 gate.
- **D-012**: minSdk 26→27 ADR applied (D-015) + RATIFIED by Mo + runtime-proven (V-037c); size: MEASURED 55.9 MB — budget MET, no options table needed
  deferred until the bundletool measurement from the CI run.
- Coverage after this session (V-029 re-measured via exec analysis): core:quran 93.1% (67/72) /
  core:align 91.0% (372/409) lines.

## Phase 1 — DONE (with two honest pendings) (Quran + align + harness, per §16)
- **core:quran DONE**: Tanzil asset committed with verbatim notice header (whole-file sha256
  8c3a25b0…; ayah-payload sha256 a0d9cd37…); integrity tests (114 / 6,236 / checksums / notice /
  basmala placement / no-mutation) GREEN; N() derived from the model repo's own normalizer
  (retrieve.py at pinned rev) — folds/drops documented in code (V-020), 12 golden tests GREEN;
  full-text verification 77,881 words → 327,514 letters, exactly 29 letters, 0 empty words.
- **core:align DONE**: full §9 — semi-global NW (free leading/trailing observed, free leading
  expected) + 1↔2 merge, independence counting, interiority (timed + null-timing token-index),
  precedence machine (HINTED terminal > CORRECT never-downgraded > MISSED ≥2-independent-absence >
  WRONG per preset > CLOSE > PENDING), EXTRA dedup (text,250ms bucket), speech-only lost timer,
  range-wide reanchor, hint, §9.6 summary semantics. 31 tests GREEN; coverage 91.7% lines.
  Two REAL bugs found & fixed during testing (V-021).
- **tools:eval + eval/run_asr.py DONE**: same hypotheses JSON as the app; synthetic pipeline run
  end-to-end (N()(surah 112, 19 words) → run_asr.py --synthetic → tools:eval → report);
  everything labeled "not measured on real audio" (V-022).
- **Coverage: core:quran 93.6% lines, core:align 91.7% lines (combined 91.9%) — ≥85% gate MET.**
- **N() self-validation (§7.5) — PENDING REAL AUDIO**: protocol requires a clean fixture
  (quiet close-mic/studio recitation); no real speech exists in the sandbox and §0 forbids
  inventing audio. N() equivalence with the author's normalizer verified over the full text
  (V-020). Runs as soon as Mo provides one clip; CER < 2% target in model space.
- **S6 baseline GGML — STILL BLOCKED**: retried with exponential backoff (60/120/180/240 s),
  HF keeps returning 429. Harness-only, not on the critical path (V-012, V-019).
- Full build gate: assembleDebug + all unit tests GREEN.
