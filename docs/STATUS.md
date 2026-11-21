# STATUS — per phase

Last updated: 2026-11-21 (after Phase 0.6 gate-closure work)

## Phase 0 — Scaffold: **DONE WITH EXCEPTION (E1)**
Exception E1: **CI has never executed** — sandbox has no `gh` CLI and no git remote; `assembleRelease`
with R8 has never succeeded anywhere (sandbox kernel-OOM, V-014). Everything else verified green:
- 13-module Gradle scaffold per §6.1 (module count corrected this phase from "15" — V-018).
- `assembleDebug` GREEN (all modules; APK 87.75 MB debug, measured V-018); unit tests GREEN.
- Merged manifest: RECORD_AUDIO only — no INTERNET / ACCESS_NETWORK_STATE (V-011).
- CI workflow encodes: reproducible asset pipeline (fetch_native + generate_notices + fetch_models),
  debug+release builds, tests, **release-minify guard** (fails without R8 mapping.txt),
  merged-manifest offline check, license report. **Unexecuted** until Mo pushes to GitHub.
- Docs skeleton per §17 complete.

## Phase 0.5 — Spike: **PARTIAL**
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

## Needs from Mo (§18 + Phase 0.6)
1. **Trigger CI**: push the repo to GitHub (Actions workflow is ready) — closes E1 when green,
   and yields the first minified release build + AAB size measurement.
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
7. Final applicationId (Play rejects com.example.*) — Phase 4 blocker.
8. Test devices (≥1 low-end 3–4 GB + ≥1 flagship) → docs/DEVICES.md.
9. Ratification of `[MO]` items: presets §9.5, summary §9.6, Amiri default §7.7,
   tiny shipping policy §8.1, and the CC-BY-3.0 attribution interpretation (§7.1).
10. Arabic string review; store copy review; release keystore; code license (Apache-2.0 default).

## Phase 1 — IN PROGRESS (Quran + align + harness, per §16)
- core:quran: Tanzil asset + notice header + integrity tests (114 / 6,236 / checksum / no-mutation)
  + N() derived from tokens.txt (V-005 inventory; folds ة→ه, ى→ي, hamza seats) with golden tests;
  derived table separate, Tanzil notice header in every derived file.
- core:align: full §9 per v2.0 deltas (semi-global NW + 1↔2 merge/split; independence; precedence
  machine; confidence-free presets; null-timing mode; speech-only lost timer; EXTRA dedup
  (text,250ms-bucket); reanchor; idempotency).
- tools:eval + eval/run_asr.py: same hypotheses JSON as app; synthetic run now; label
  everything "not measured on real audio".
- N() self-validation (§7.5): primary model only, clean fixture, model space, target CER < 2%.
- S6 baseline retry (exponential backoff; never an unpinned mirror).
Coverage target ≥85% lines on core:quran + core:align.
