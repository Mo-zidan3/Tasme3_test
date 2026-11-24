# TESTING

Current (Phase 0): core:align contract tests — observation identity (§9.2
4-tuple), independence rules (<50% overlap of shorter window; final +
window-bounds differ), TrackerConfig defaults per §9.1, AsrWord confidence
null. JUnit Jupiter 6.1.3.

Current after Phase 1 (all green):
- core:quran (21 tests): integrity (114/6,236/sha256/notice), basmala placement (1:1 IS basmala,
  9:1 none), N() goldens (dagger alif, 06DF/06E5/06E6 drops, alef-wasla, hamza seats, ة/ى, tatweel,
  basmala, ta'awwudh, full-77,881-word sweep).
- core:align (31 tests): sim/levenshtein; aligner table tests (semi-global free edges, skip, merge,
  substitution); tracker property tests (idempotency, independence-gated WRONG for both presets,
  nothing-downgrades-CORRECT, HINTED terminal, MISSED via realistic partial sequence, boundary
  exclusion vs pause-final relaxation, null-timing token-index interiority, monotonic pointer,
  repeats→EXTRA, EXTRA dedup, lost timer, reanchor, §9.6 summary semantics).
- JaCoCo line coverage (report-level counters, CI-measured run 45064204429):
  **core:quran 93.1% (67/72 lines), core:align 91.0% (383/421 lines)** — both ≥85% floor,
  asserted by scripts/ci_coverage_gate.py on every CI build. (Earlier 93.6/91.7 quotes were
  pre-Phase-2 copies; superseded by V-029/V-037 measurements.)
- tools:eval + eval/run_asr.py: synthetic end-to-end pipeline exercised (report with honest labels).

Current after Phase 2 host-side (all green; unit total across modules — see each module's
`test` task output; the CI build job runs them all):
- core:quran (27): integrity + N() goldens + basmala OPTIONAL_PREFIX + arithmetic reconciliation.
- core:align (39): §9 machine + simulator tests on REAL Quranic vocabulary (cadence law, all-CORRECT,
  MISSED, WRONG under both presets, trailing-EXTRA verdict V-025, repeated-ayah invariants).
- core:audio (13): scheduler law (fake RTFs 0.2/0.3/0.5/0.9/1.4), queue depth 1 + final-never-
  starves + stale-drop, ring buffer FIFO/overflow/degenerate, segmenter pause/force-cut/overlap.
- core:asr (5): word assembly (▁, seconds×1000, +40 ms frame shift), confidence always null,
  null-timing fallbacks.
- core:session (8): fake-recognizer orchestration, §11 checkpoints after FINALs only, StateFlow,
  calibration median/ladder L0–L3/model choice/badge.
- Harness: tools:eval + eval/run_asr.py --synthetic (app-shaped streams via the simulator law) —
  report labeled "not measured on real audio"; N() self-validation V-026 (6 clips CER 0.00%).

## Instrumented tests (CI emulator, ubuntu-24.04 + /dev/kvm; runs 45067719380)
Inventory of `app/src/androidTest` (package com.iqraa.app):
- `loadLibrarySmoke_bothNativeLibsLoad` — `System.loadLibrary("onnxruntime")` +
  `System.loadLibrary("sherpa-onnx-jni")` on API 27 + API 35. **PASS on both** — the
  minSdk=27 native-floor runtime proof (D-012/D-015).
- `sherpaTimingDecode_generatedFixture_secondsAnd40msGrid` — decodes the generated 8 s fixture
  through the real sherpa JNI path on-device; asserts seconds-scale timestamps + 40 ms
  frame-grid deltas. **PASS on both floors (0.597 s / 0.598 s, V-037c).** The fixture rides
  INSIDE the androidTest APK assets (app/src/androidTest/assets/eval/fixtures/, CI-generated
  via scripts/make_timing_fixture.py, git-ignored) — scoped storage on API 35 hides
  shell-pushed external files, so APK assets are the channel (V-037c evidence).
- `amiriQuranFont_rendersAllTanzilCodepoints_nonEmpty` — font asset loads and renders
  non-empty on both floors. PASS.
- `sherpaAccuracyDecode_moClip_loudSkipWhenAbsent` — Mo's own accuracy clip test; loud-skips
  while the clip is absent (§0). With the T4 fixture committed, this test becomes the
  real-audio accuracy smoke (see eval/data/PROVENANCE.md).

Planned per §14: golden/table tests (N(), alignment planted-error matrix),
property-based tracker tests (idempotency, independence, monotonicity),
instrumented fixture decode (DONE — see above), Room migrations, Compose UI tests, coverage
≥85% lines on core:quran + core:align (DONE — CI-asserted).

Run: `./gradlew test` (host) · CI runs `:app:connectedDebugAndroidTest` on API 27 + 35.

## Manual UI script (T9/B3) — docs/TESTING.md, executed on the CI emulator

The emulator-executable subset runs IN CI after every instrumented job
(`scripts/manual_ui_script.sh` — evidence lands in the job log + the `instrumented-api*`
artifact):

- M1 install + launch MainActivity
- M2 onboarding renders (rationale + riwaya + limitations in the hierarchy dump)
- M4 forced rotation — onboarding content intact after landscape
- M5 font scale 2.0× — content renders (reset afterwards)

Steps that need Mo's hands on REAL hardware (honestly OPEN, not faked):

- **H1 permission denied-permanently** — deny twice on a phone; the deep-link card opens the
  app's system settings page (the emulator system-dialog interaction is locale-flaky; the
  granted path is covered by `MainActivityUiTest.onboarding_grantFlow_reachesReader`).
- **H2 true process-death restore** — swipe-away + relaunch mid-session; Home shows
  «ملخص الجلسة السابقة» from the Room checkpoints (§11). `ActivityScenario.recreate`
  covers config changes only; the checkpoint persistence itself is covered by
  `RoomDbTest.dao_roundTrip_checkpoint_and_history` on-device.
- **H3 real interruptions** — incoming call, audio-focus loss, another app taking the mic
  (the pause/release/resume path is covered by `ReciteViewModel` + `SessionLifecycleTest`;
  the PHYSICAL interruption needs hardware).
- **H4 phone performance (Q7)** — RTF/RSS/cold-start on Mo's device (emulator numbers are
  INDICATIVE only, V-039/V-040).

**Phase 3 is NOT claimed DONE until H1–H4 are executed and recorded by Mo on hardware.**
