# STATUS — per phase

Last updated: 2026-11-21 (end of Phase 0 + Phase 0.5 spike)

## Phase 0 — Discover/scaffold ✅ DONE (gates green)
- Environment: JDK (Temurin 21, toolchain 17), Android SDK (platforms 37.0/37.1, build-tools
  36.0.0), Gradle 9.9.2 wrapper — installed inside workspace, git-ignored.
- 15-module scaffold per §6.1: app, core:{quran,align,asr,audio,session,data,designsystem},
  feature:{reader,recite,history,settings}, tools:eval. Version catalog single source.
- BUILD GREEN: `assembleDebug` all modules; APK builds with models (55 MB), VAD, font,
  arm64-only jniLibs. **Merged manifest: RECORD_AUDIO only — no INTERNET/ACCESS_NETWORK_STATE**
  (release-merged check encoded in CI).
- Unit tests GREEN: core:align contract tests (identity, independence, defaults).
- CI skeleton: .github/workflows/ci.yml (build, tests, manifest check, license report).
- Docs skeleton: README, ARCHITECTURE, DECISIONS, VERIFICATION, STATUS, EVALUATION,
  TESTING, PRIVACY, DEVICES, AUDIT + THIRD_PARTY_NOTICES.

## Phase 0.5 — Spike ✅ DONE (S1–S5 verified; S6 blocked; two on-device items moved forward)
- S1 models: fetched pinned revs + SHA-256 (scripts/fetch_models.py is the sanctioned path).
- S2 sherpa-onnx v2.0.2: kotlin-api vendored + jniLibs (arm64) extracted; compiles; API
  surface matches Q1-CLOSED. **Caveats: fixture decode on API 26 emulator not possible in
  sandbox (no emulator) → moved to Phase 2 on-device gate.** Archive location asked to Mo.
- S3 VAD: silero_vad.onnx pinned + hash; Vad.kt config matches VAD-CLOSED.
- S4 Tanzil: Uthmani txt-2 downloaded; **6,236 ayat / 114 surahs verified** (closes the
  §7.3 [VERIFY]); license notice preserved; asset lands committed in Phase 1.
- S5 Amiri Quran 1.003 + OFL.txt pinned with hashes; shaping tests pending device.
- S6 baseline GGML: **BLOCKED by HF rate-limiting** — retried over the spike window
  (429/HTML), not fetched. Harness-only, not on critical path; retry in Phase 1.

## Needs from Mo (§18)
1. **Archive location** for exact shipped model files + license texts (asked once — kept
   local under third_party/ until answered).
2. Recordings per §13.1 (≥50 first-look; ≥60 correct held-out + ≥25 planted-error; ≥3
   speakers) — if absent, Phase 2 ends indicative and WRONG ships as CLOSE (§13.4 default).
3. Final applicationId (Play rejects com.example.*) — Phase 4 blocker.
4. Test devices (≥1 low-end 3–4 GB + ≥1 flagship) → docs/DEVICES.md.
5. Ratification of `[MO]` items: presets §9.5, summary §9.6, Amiri default §7.7,
   tiny shipping policy §8.1.
6. Quran text + font approval (defaults applied, flagged pending).
7. Arabic string review; store copy review; release keystore; code license (Apache-2.0 default).

## Next (Phase 1 — Quran + align + harness, per §16)
- core:quran: commit processed read-only Tanzil asset + integrity tests (§7.3) + N() with
  golden tests derived from tokens.txt (V-005 inventory) + derived normalization table
  with Tanzil notice headers.
- core:align: full §9 implementation (alignment, state machine incl. null-timing mode,
  presets, tasmee' semantics) + property-based tests (idempotency, independence,
  monotonicity) — target ≥85% line coverage on core:quran + core:align.
- tools:eval + eval/run_asr.py (synthetic-capable) + N() self-validation per §7.5.
- Retry S6 baseline fetch alongside eval harness.

Planning estimate for Phase 1: 5–8 days (spec §16; estimate, not a measurement).

## Known limitations at Phase 0 close (sandbox, not product)
- R8-minified release build OOM-killed locally (kernel oom-kill; evidence docs/VERIFICATION.md V-014).
  Debug + tests green; CI runs full release. `-PsandboxNoMinify` for local smoke.
- No emulator/device in sandbox → fixture decode, timestamp unit, shaping tests move to Phase 2.
- S6 baseline GGML fetch blocked by HF rate limiting (429) — retry with Phase 1 harness.
